/*********************************************************************************
 * Copyright 2009-present trivago GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **********************************************************************************/

package com.trivago.triava.tcache.eviction;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.cache.CacheException;

import com.trivago.triava.tcache.CacheWriteMode;
import com.trivago.triava.tcache.util.Serializing;

/**
 * Represents a Cache entry with associated metadata.
 * This cache entry is valid as long as data != null
 * @param <V>
 */
public final class AccessTimeObjectHolder<V> implements TCacheHolder<V>
{
	AtomicIntegerFieldUpdater useCountAFU = AtomicIntegerFieldUpdater.newUpdater(AccessTimeObjectHolder.class, "useCount");

	final static int SERIALIZATION_MASK = 0b11;
	final static int SERIALIZATION_NONE = 0b00;
	final static int SERIALIZATION_SERIALIZABLE = 0b01;
	final static int SERIALIZATION_EXTERNIZABLE = 0b10;

	// offset #field-size
	// 0 #12
	// Object header
	// 12 #4 
	private volatile Object data; // Holds either a V instance, or serialized data, e.g. byte[]
	// 16 #4
	private int inputDate; // in milliseconds relative to baseTimeMillis 
	// 20 #4
	private int lastAccess = 0; // in milliseconds relative to baseTimeMillis
	// 24 #4
	private int maxIdleTime = 0;  // in seconds
	// 28 #4
	private int maxCacheTime = 0; // in seconds
	// 32 #4
	private volatile int useCount = 0;
	// 36
	/**
	 * Bit 0,1: Serialization mode. 00=Not serialized, 01=Serializable, 10=Externizable
	 */
	byte flags;
	// 37
	
	/**
	 * Construct a holder
	 * 
	 * @param value The value to store in this holder
	 * @param maxIdleTime Maximum idle time in seconds
	 * @param maxCacheTime Maximum cache time in seconds 
	 * @param writeMode The CacheWriteMode that defines how to serialize the data
	 * @throws CacheException when there is a problem serializing the value
	 */
	public AccessTimeObjectHolder(V value, long maxIdleTime, long maxCacheTime, CacheWriteMode writeMode) throws CacheException
	{
		setLastAccessTime();
		try
		{
			switch (writeMode)
			{
				case Identity:
					flags = SERIALIZATION_NONE;
					this.data = value;
					break;
				case Serialize:
					if (value instanceof Serializable)
					{
						flags = SERIALIZATION_SERIALIZABLE;
						byte[] valueAsBytearray = Serializing.toBytearray(value);
						this.data = valueAsBytearray;
						break;
					}
				case Intern:
					flags = SERIALIZATION_NONE;
					//this.data = interner.get(value);
				default:
					throw new UnsupportedOperationException("CacheWriteMode not supported: " + writeMode);
			}
		}
		catch (Exception exc)
		{
			throw new CacheException("Cannot serialize cache value for writeMode " + writeMode, exc);
		}

		setInputDate();
		this.maxIdleTime = Cache.limitToPositiveInt(maxIdleTime);
		this.maxCacheTime = Cache.limitToPositiveInt(maxCacheTime);
	}


	/**
	 * Releases all references to objects that this holder holds. This makes sure that the data object can be
	 * collected by the GC, even if the holder would still be referenced by someone.
	 * <p>
	 *     This is the end-of-life for the instance. The data field is now null, which means the entry is
	 *     invalid. Even if another thread has a reference to this holder, he cannot access the data field.
	 *     any longer.
	 * </p>
	 * @return true, if the call has released the holder. false, if the holder was already released before.
	 */
	protected boolean release()
	{
		synchronized (this)
		{
			boolean releasedData = data != null;
			data = null;
			// SAE-150 Inform the caller whether he has released the holder. Hint: Other threads may
			//         have called this concurrently, e.g. two deletes, expiration and/or eviciton.
			return releasedData;
		}
	}
	
	public void setMaxIdleTime(int aMaxIdleTime)
	{
		maxIdleTime = aMaxIdleTime;
	}

	public int getMaxIdleTime()
	{
		return maxIdleTime;
	}

	public V get()
	{
		setLastAccessTime();
		return peek();
	}

	/**
	 * Return the data, without modifying statistics or any other metadata like access time.
	 * 
	 * @return The value of this holder 
	 */
	@SuppressWarnings("unchecked") 
	public V peek()
	{
		int serializationMode = flags & SERIALIZATION_MASK;
		try
		{
			switch (serializationMode)
			{
				case SERIALIZATION_NONE:
					return (V)data;
				case SERIALIZATION_SERIALIZABLE:
					return (V)Serializing.fromBytearray((byte[])(data));
				case SERIALIZATION_EXTERNIZABLE:
				default:
					throw new UnsupportedOperationException("Serialization type is not supported: " + serializationMode);

			}
		}
		catch (Exception exc)
		{
			throw new CacheException("Cannot serialize cache value for serialization type " + serializationMode, exc);
		}
	}

	private void setLastAccessTime()
	{
		lastAccess = (int)(currentTimeMillisEstimate() - Cache.baseTimeMillis);
	}
	
	private long currentTimeMillisEstimate()
	{
		return Cache.millisEstimator.millis();
	}
	
	/**
	 * @return the lastAccess
	 */
	public long getLastAccess()
	{
		return Cache.baseTimeMillis + lastAccess;
	}

	public int getUseCount()
	{
		return useCount;
	}

	public void incrementUseCount()
	{
		useCountAFU.incrementAndGet(this);
	}

	private void setInputDate()
	{
		inputDate = (int)(currentTimeMillisEstimate() - Cache.baseTimeMillis);
	}

	public long getInputDate()
	{
		return Cache.baseTimeMillis + inputDate;
	}
	
	public boolean isInvalid()
	{
		long millisNow = currentTimeMillisEstimate();
		if (maxCacheTime > 0L)
		{
			long cacheDurationMillis = millisNow - getInputDate();
			// SRT-23661 maxCacheTime explicitly converted to long, to avoid overrun due to "1000*"
			if (cacheDurationMillis > 1000L*(long)maxCacheTime) 
			{
				return true;
			}
		}
		
		if (maxIdleTime == 0)
			return false;

		long idleSince = millisNow - getLastAccess();

		// SRT-23661 maxIdleTime explicitly converted to long, to avoid overrun due to "1000*"
		return (idleSince > 1000L*(long)maxIdleTime);
	}


	public void setExpireUntil(int maxDelay, TimeUnit timeUnit, Random random)
	{
		int maxDelaySecs = (int)timeUnit.toSeconds(maxDelay);
		int delaySecs = random.nextInt(maxDelaySecs);
		
		if (maxCacheTime == 0 || delaySecs < maxCacheTime)
		{
			// holder.maxCacheTime was not set (never expires), or new value is smaller => use it 
			maxCacheTime = Cache.limitToPositiveInt(delaySecs);
		}
		// else: Keep delay, as holder will already expire sooner than delaySecs.
	}
}
