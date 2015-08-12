package com.trivago.triava.tcache.storage;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentMap;

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.StorageBackend;
import com.trivago.triava.tcache.eviction.Cache.AccessTimeObjectHolder;

/**
 * <b>DO NOT USE THIS FOR PRODUCTIVE CODE. JAR IS NOT AVAILABLE LIVE.</b>
 * <p>
 * Implements a storage that uses the Highscale libs. It is only implemented for performance tests. 
 * <p>
 * <b>DO NOT USE THIS FOR PRODUCTIVE CODE. JAR IS NOT AVAILABLE LIVE.</b>
 *  
 * @author cesken
 *
 * @param <K>
 * @param <V>
 */
public class HighscalelibNonBlockingHashMap<K,V> implements StorageBackend<K, V>
{
	@Override
	public ConcurrentMap<K, AccessTimeObjectHolder<V>> createMap(Builder<K,V> builder, double evictionMapSizeFactor)
	{
		try
		{
			Class<?> cacheClass = Class.forName("org.cliffc.high_scale_lib.NonBlockingHashMap");
	
			// Create the instance by reflection, for avoiding dependencies to libraries we do have (yet) as dependency.
			// This way we can compare the TCache performance using different storages (CHM vs LocalCache vs ...)
			Constructor<?> cons = cacheClass.getDeclaredConstructor(int.class);
			
			double loadFactor = 0.75F;
			int requiredMapSize = (int) (builder.getExpectedMapSize() / loadFactor) + (int)evictionMapSizeFactor;
	
			@SuppressWarnings({ "unchecked", "rawtypes" })
			ConcurrentMap<K, AccessTimeObjectHolder<V>> inst = (ConcurrentMap) cons.newInstance(requiredMapSize);
			return inst;
		}
		catch (Exception exc)
		{
			throw new RuntimeException("Cannot create map for HashImplementation=" + builder.getHashImplementation(), exc);
		}
		
	}

}
