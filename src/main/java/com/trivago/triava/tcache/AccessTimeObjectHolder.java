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

package com.trivago.triava.tcache;

import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import javax.cache.CacheException;

import com.trivago.triava.tcache.expiry.Constants;
import com.trivago.triava.tcache.expiry.TCacheExpiryPolicy;
import com.trivago.triava.tcache.util.SecondsOrMillis;
import com.trivago.triava.tcache.util.Serializing;

/**
 * Represents a Cache entry with associated metadata.
 * This cache entry is valid as long as data != null
 * 
 * @param <V> The value type
 */
public final class AccessTimeObjectHolder<V> implements TCacheHolder<V>
{
	private static final long serialVersionUID = 1774522368637513622L;

	@SuppressWarnings("rawtypes") // AccessTimeObjectHolder<V> would be incompatible with AccessTimeObjectHolder.class
	transient static AtomicIntegerFieldUpdater<AccessTimeObjectHolder> useCountAFU = AtomicIntegerFieldUpdater.newUpdater(AccessTimeObjectHolder.class, "useCount");

	final static int SERIALIZATION_MASK = 0b0000_0011;
	final static int SERIALIZATION_NONE = 0b0000_0000;
	final static int SERIALIZATION_SERIALIZABLE = 0b0000_0001;
	final static int SERIALIZATION_EXTERNALIZABLE = 0b0000_0010;

	final static int STATE_MASK = 0b0110_0000;
	final static int STATE_INCOMPLETE = 0b0000_0000;
	final static int STATE_COMPLETE = 0b0010_0000;
	final static int STATE_RELEASED = 0b0100_0000;

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
	private int maxIdleTime = 0;  // in milliseconds or seconds relative to inputDate
	// 28 #4
	private int maxCacheTime = 0; // in milliseconds or seconds relative to inputDate
	// 32 #4
	private volatile int useCount = 0;
	// 36
	/**
	 * Bit 0,1: Serialization mode. 00=Not serialized, 01=Serializable, 10=Externizable
     *
	 */
	private volatile byte flags = STATE_INCOMPLETE;
	// 37
	
	/**
	 * Construct a holder. The holder will be incomplete and not accessible by cache users, until you call {@link #complete(long, long)}
	 * 
	 * @param value The value to store in this holder
	 * @param writeMode The CacheWriteMode that defines how to serialize the data
	 * @throws CacheException when there is a problem serializing the value
	 */
	public AccessTimeObjectHolder(V value, CacheWriteMode writeMode) throws CacheException
	{
		try
		{
//			long start = System.nanoTime();
            //Since we initializing with flags = 0, the serialization flags can be directly assigned instead of using binary operations.
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
//			
//			long duration = System.nanoTime() - start;
//			if (duration > 1_000_000)
//			{
//				System.out.println("Slow serializing. value=" + value + ", ms=" + duration / 1_000_000d);
//			}
		}
		catch (Exception exc)
		{
			throw new CacheException("Cannot serialize cache value for writeMode " + writeMode, exc);
		}

	}

	public AccessTimeObjectHolder(V value, long maxIdleTimeMillis, long maxCacheTimeSecs, CacheWriteMode writeMode) throws CacheException
	{
		this(value, writeMode);
		complete(maxIdleTimeMillis, maxCacheTimeSecs);
	}

	void complete(long maxIdleTimeMillis, long maxCacheTimeMillis)
	{
		this.maxIdleTime = SecondsOrMillis.fromMillisToInternal(maxIdleTimeMillis);
		this.maxCacheTime = SecondsOrMillis.fromMillisToInternal(maxCacheTimeMillis);
		flags |= STATE_COMPLETE;
		setInputDate();
		setLastAccessTime();
	}

	/**
	 * Returns whether the holder is valid. It must be non-null and not expired.
	 * @param holder The holder to check
	 * @return true if the holder is valid
	 */
	static public boolean isValid(AccessTimeObjectHolder<?> holder)
	{
		return holder != null && !holder.isInvalid();
	}
	
	/**
	 * Releases all references to objects that this holder holds. This makes sure that the data object can be
	 * collected by the GC, even if the holder would still be referenced by someone.
	 * <p>
	 *     This is the end-of-life for the instance, and {@link #isInvalid()} yields true from now on. If a reference to this holder is stored for a longer
	 *     time, a Thread should check {@link #isInvalid()}.   
	 * </p>
	 * @return true, if the call has released the holder. false, if the holder was already released before.
	 */
	protected boolean release()
	{
		synchronized (this)
		{
			boolean alreadyReleased = (flags & STATE_MASK) == STATE_RELEASED;
			// SAE-150 Inform the caller whether he has released the holder. Hint: Other threads may
			//         have called this concurrently, e.g. two deletes, expiration and/or eviciton.
			if (alreadyReleased)
				return false;
			
			flags = (byte)((flags & ~STATE_MASK) | STATE_RELEASED);
			return true;
		}
	}
	
	public void setMaxIdleTime(int idleTime, TimeUnit timeUnit)
	{
		maxIdleTime = SecondsOrMillis.fromMillisToInternal(timeUnit.toMillis(idleTime));
	}

	/**
	 * 
	 * @param updated
	 * @param expiryPolicy
	 * @param oldHolder
	 * @return The calculated idleTime
	 */
	long calculateMaxIdleTimeFromUpdateOrCreation(boolean updated, TCacheExpiryPolicy expiryPolicy, AccessTimeObjectHolder<V> oldHolder)
	{
		long tmpIdleTimeMillis = updated ? expiryPolicy.getExpiryForUpdate() : expiryPolicy.getExpiryForCreation();
		if (tmpIdleTimeMillis == Constants.EXPIRY_NOCHANGE)
		{
			return SecondsOrMillis.fromInternalToMillis(oldHolder.maxIdleTime);
		}
		else
		{
			return tmpIdleTimeMillis;
		}
	}


	/**
	 * Prolongs the maxIdleTime by the given idleTime. 0 means immediate expiration, -1 means to not change anything, any positive value is the prolongation in seconds.
	 * @param idleTimeMillis The time for prolong in milliseconds. See description for the special values 0 and -1.
	 */
	public void updateMaxIdleTime(long idleTimeMillis)
	{
		if (idleTimeMillis == 0)
		{
			this.maxIdleTime = 0; // invalidate immediately
		}
		if (idleTimeMillis > 0)
		{
			// Prolong time: 
			// 1) Find out how long we currently live in the Cache
			long cacheDurationMillis = currentTimeMillisEstimate() - getCreationTime();
			// 2) Prolong by idleTimeSecs.
			try
			{
				long newIdleTimeMillis = cacheDurationMillis + idleTimeMillis;
				if (newIdleTimeMillis < cacheDurationMillis)
				{
					newIdleTimeMillis = Constants.EXPIRY_MAX; // overrun
				}
				this.maxIdleTime = SecondsOrMillis.fromMillisToInternal(newIdleTimeMillis);				
			}
			catch (Exception exc)
			{
			    Cache.logger.error("updateMaxIdleTime() failed idleTimeMillis=" +idleTimeMillis + ", getInputDate()=" + getCreationTime() + ", cacheDurationMillis=" + cacheDurationMillis); 
			}
//			int newMaxIdleTarget = (int)Math.min(  (cacheDurationMillis/1000) + idleTimeSecs, (long)Integer.MAX_VALUE);
//			this.maxIdleTime = newMaxIdleTarget;
		}
		// -1 => No change
	}


	@Override
	public long getExpirationTime()
	{
		long idleDurationMillis = SecondsOrMillis.fromInternalToMillis(maxIdleTime);
		long expirationDurationMillis = SecondsOrMillis.fromInternalToMillis(maxCacheTime);
		// durationMillis: The smaller of expiration-time and idle-time wins
		long durationMillis = Math.min(expirationDurationMillis, idleDurationMillis);
		return getCreationTime() + durationMillis;
	}

	/**
	 * {@inheritDoc}
	 * 	<p>TODO The use count is not yet updated here. This makes behavior inconsistent, e.g. in the iterator 
	 */
	@Override
	public V get()
	{
		setLastAccessTime();
		return peek();
	}

	@Override
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
					Object dataRef = data; // defensive copy
					return dataRef != null ? (V)Serializing.fromBytearray((byte[])(dataRef)) : null;
				case SERIALIZATION_EXTERNALIZABLE:
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
	
	@Override
	public long getLastAccessTime()
	{
		return Cache.baseTimeMillis + lastAccess;
	}
	
	@Override
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

	@Override
	public long getCreationTime()
	{
		return Cache.baseTimeMillis + inputDate;
	}

	@Override
	public boolean isInvalid()
	{
		return isInvalid(false);
	}

	private boolean isInvalid(boolean debug)
	{	
		if (data == null)
		{
			return true; // Holder was released, e.g. via expiration
		}

		// -1- Check completeness
		boolean incomplete = (flags & STATE_MASK) != STATE_COMPLETE;
		if (incomplete)
		{
			if (debug) System.out.println("Dropped because holder is not complete: flags=" + flags + ": " + data);
			return true;
		}
		
		// -2- Check cache time
		long millisNow = currentTimeMillisEstimate();
		long expDurationMillis = SecondsOrMillis.fromInternalToMillis(maxCacheTime);
		if (expDurationMillis > 0L)
		{
			long cacheDurationMillis = millisNow - getCreationTime();
			if (cacheDurationMillis > expDurationMillis) 
			{
				if (debug) System.out.println("Dropped because expired: millisNow=" + millisNow + ", maxCacheTime" + maxCacheTime + ", expDurationMillis" + expDurationMillis + "< cacheDurationMillis" + cacheDurationMillis);
				return true;
			}
		}
		
		// -3- Check idle time
		long idleDurationMillis = SecondsOrMillis.fromInternalToMillis(maxIdleTime);
		if (idleDurationMillis == 0)
		{
			if (debug) System.out.println("Dropped because idle0: millisNow=" + millisNow + ", maxCacheTime" + maxCacheTime + ", expDurationMillis" + expDurationMillis + "< cacheDurationMillis" + idleDurationMillis);
			return true;
		}

		long lastAccess = getLastAccessTime();
		long idleSince = millisNow - lastAccess;

		if (idleSince > idleDurationMillis)
		{
			if (debug) System.out.println("Dropped because idle: millisNow=" + millisNow + ", maxCacheTime" + maxCacheTime + ", idleDurationMillis" + idleDurationMillis + "< idleSince" + idleSince + ", lastAccess=" + lastAccess);
			return true;
		}
		return false;
	}

	/**
	 * <pre>
	 *   |-------------- maxCacheTimeMillis --------------|
	 *   |                                                |
	 *   |--- newMaxCacheTimeMillis ---|                  |
	 *   |                             |                  |
	 *  -|---------|-------------------|------------------|------------------------------------- time
	 *   |         |                   |                  |
	 *   |         now                 now + delayMillis  |
	 *   |                  = expirationOnNewExpireUntil  |
	 *   |                                                |
	 *   inputDate + baseTimeMillis = creationTime        creationTime + maxCacheTime
	 * </pre>
	 * 
	 * @param maxDelay The maximum delay time until the object will be expired
	 * @param timeUnit The time unit for maxDelay
	 * @param random The random generator to use
	 */
	public void setExpireUntil(int maxDelay, TimeUnit timeUnit, Random random)
	{
		// -1- Create a random idleTime in the interval [0 ... maxDelay-1]
		long millis = timeUnit.toMillis(maxDelay);
		final long delayMillis;

		if (millis == 0)
		{
            delayMillis = millis;
        }
        // There is no Random.nextLong(), so we need a special handling to not getting integer overruns
        else if (millis <= Integer.MAX_VALUE)
		{
			delayMillis = random.nextInt((int)millis);
		}
		else
		{
			int maxDelaySecs = (int)timeUnit.toSeconds(maxDelay);
			delayMillis = 1000l * random.nextInt(maxDelaySecs);			
		}

		// -2- Calculate current expiration from cache time and the one from the newly planned expireUntil  
		long maxCacheTimeMillis = SecondsOrMillis.fromInternalToMillis(maxCacheTime);
		long creationTime = getCreationTime();
		long expirationOnCacheTime = maxCacheTimeMillis + creationTime;
		long expirationOnNewExpireUntil = currentTimeMillisEstimate() + delayMillis;
		
		if (maxCacheTimeMillis == 0 || expirationOnNewExpireUntil < expirationOnCacheTime)
		{
			// holder.maxCacheTime was not set (never expires), or new value is smaller => use it
			long newMaxCacheTimeMillis = expirationOnNewExpireUntil - creationTime;
			maxCacheTime = SecondsOrMillis.fromMillisToInternal(newMaxCacheTimeMillis);
		}
		// else: Keep delay, as holder will already expire sooner than delaySecs.
	}


	@Override
	public String toString()
	{
		return "AccessTimeObjectHolder [dataPresent=" + (data != null) + ", inputDate=" + inputDate + ", lastAccess=" + lastAccess
				+ ", maxIdleTime=" + maxIdleTime + ", maxCacheTime=" + maxCacheTime + ", useCount=" + useCount
				+ ", flags=" + flags + "]";
	}
}
