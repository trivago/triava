package com.trivago.triava.tcache.eviction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.cache.CacheException;

import com.trivago.triava.tcache.CacheWriteMode;

public final class AccessTimeObjectHolder<V> implements TCacheHolder<V>
{
	
	final static int SERIALIZATION_MASK = 0b11;
	final static int SERIALIZATION_NONE = 0b00;
	final static int SERIALIZATION_SERIALIZABLE = 0b01;
	final static int SERIALIZATION_EXTERNIZABLE = 0b10;
	
	// offset #field-size
	// 0 #12
	// Object header
	// 12 #4 
	private Object data; // Holds either a V instance, or serialized data, e.g. byte[] 
	// 16 #4
	private int inputDate; // in milliseconds relative to baseTimeMillis 
	// 20 #4
	private int lastAccess = 0; // in milliseconds relative to baseTimeMillis
	// 24 #4
	private int maxIdleTime = 0;  // in seconds
	// 28 #4
	private int maxCacheTime = 0; // in seconds
	// 32 #4
	private int useCount = 0; // Not fully thread safe, on purpose. See #incrementUseCount() for the reason.
	// 36
	byte flags; // Bit 0,1: Serialization mode. 00=Not serialized, 01=Serializable, 10=Externizable 
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
						byte[] valueAsBytearray = toBytearray(value);
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


	private byte[] toBytearray(Object obj) throws IOException
	{
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutput out = null;
		try
		{
		  out = new ObjectOutputStream(bos);   
		  out.writeObject(obj);
		  return bos.toByteArray();
		}
		finally
		{
		  try
		  {
		    if (out != null)
		    {
		      out.close();
		    }
		  }
		  catch (IOException ex) {}

		  try
		  {
		    bos.close();
		  }
		  catch (IOException ex) {}
		}
	}
	
	private Object fromBytearray(byte[] serialized) throws IOException, ClassNotFoundException
	{
		ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
		ObjectInput in = null;
		try {
		  in = new ObjectInputStream(bis);
		  return in.readObject(); 
		}
		finally
		{
		  try
		  {
		    bis.close();
		  }
		  catch (IOException ex) {}
		  
		  try
		  {
		    if (in != null)
		    {
		      in.close();
		    }
		  }
		  catch (IOException ex) {} 
		}
	}

	/**
	 * Releases all references to objects that this holder holds. This makes sure that the data object can be
	 * collected by the GC, even if the holder would still be referenced by someone.
	 */
	protected void release()
	{
		data = null;
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
					return (V)fromBytearray((byte[])(data));
				case SERIALIZATION_EXTERNIZABLE:
					return (V)data;
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
		// The increment below is obviously not thread-safe, but it is good enough for our purpose (eviction statistics).
		// We spare synchronization code, and the holder can be more light-weight (not using AtomicInteger).
		useCount++;
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
