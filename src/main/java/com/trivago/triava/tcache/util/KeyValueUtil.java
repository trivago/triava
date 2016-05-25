package com.trivago.triava.tcache.util;

import java.util.Set;

/**
 * Perform check operations on cache keys and values.
 * <p>
 * Implementation note: This class could be using static methods. But it was decided to use Generics to spot errors in parameter passing more easily, e.g. mixing up key and value. 
 * 
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public class KeyValueUtil<K, V>
{
	String id;
	
	/**
	 * Creates a typed KeyValueUtil for the given Cache.
	 * 
	 * @param id The id (or cacheName) of the Cache
	 */
	public KeyValueUtil(String id)
	{
		this.id = id;
	}
	
	/**
	 * Checks whether key or value are null. If at least one parameter is null, NullPointerException is thrown. Otherwise
	 * this method returns without any side-effects.
	 * 
	 * <p>
	 * JSR107 mandates to throw NullPointerException. This is not in the Javadoc of JSR107, but in the
	 * general part of the specification document:
	 * "Any attempt to use null for keys or values will result in a NullPointerException being thrown, regardless of the use." 
	 * 
	 * @param key The key
	 * @param value The value
	 * @throws  NullPointerException if at least one parameter is null 
	 */
	public void verifyKeyAndValueNotNull(K key, V value)
	{
		verifyKeyNotNull(key);
		verifyValueNotNull(value);
	}
	
	/**
	 * Checks whether key is null. If it is null, NullPointerException is thrown. Otherwise
	 * this method returns without any side-effects.
	 * 
	 * <p>
	 * JSR107 mandates to throw NullPointerException. This is not in the Javadoc of JSR107, but in the
	 * general part of the specification document:
	 * "Any attempt to use null for keys or values will result in a NullPointerException being thrown, regardless of the use." 
	 * 
	 * @param key The key
	 * @throws  NullPointerException if key is null 
	 */
	public void verifyKeyNotNull(K key)
	{
		if(key == null)
		{
			throw new NullPointerException("null key is not allowed. cache=" + id);
		}
	}

	/**
	 * Checks whether key is null. If it is null, NullPointerException is thrown. Otherwise
	 * this method returns without any side-effects.
	 * 
	 * <p>
	 * JSR107 mandates to throw NullPointerException. This is not in the Javadoc of JSR107, but in the
	 * general part of the specification document:
	 * "Any attempt to use null for keys or values will result in a NullPointerException being thrown, regardless of the use." 
	 * 
	 * @param value The value
	 * @throws  NullPointerException if value is null 
	 */
	public void verifyValueNotNull(V value)
	{
		if(value == null)
		{
			throw new NullPointerException("null value is not allowed. cache=" + id);
		}
	}

	public void verifyKeysNotNull(Set<? extends K> keys)
	{
		if(keys == null)
		{
			throw new NullPointerException("null key set is not allowed. cache=" + id);
		}
	}
}
