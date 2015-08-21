package com.trivago.triava.tcache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.util.CacheSizeInfo;
import com.trivago.triava.tcache.util.ObjectSizeCalculatorInterface;

/**
 * The TCacheFactory allows to create Cache instances, and also supplies administrative methods for the
 * managed caches, like shutting down all registered Caches. The preferred way of obtaining an instance for application
 * code is a call to {@link #standardFactory()}. Library code should create a new TCacheFactory instance, so it can manage its own
 * Cache collection.  
 * 
 * @author cesken
 * @since 2015-03-10
 *
 */
public class TCacheFactory
{
	private static final CopyOnWriteArrayList<Cache<?>> CacheInstances = new CopyOnWriteArrayList<Cache<?>>();

	static TCacheFactory standardFactory = new TCacheFactory();
	
	/**
	 * Returns the standard factory. 
	 * @return
	 */
	public static TCacheFactory standardFactory()
	{
		return standardFactory;
	}
	
	public <K,V>Builder<K,V> builder()
	{
		return new Builder<K,V>(this);
	}
	
	
	/**
	 * Registers a Cache to this factory. Registered caches will be used for bulk operations like {@link #shutdownAll()}. 
	 * @param cache
	 */
	public void registerCache(Cache<?> cache)
	{
		// Hint: "cache" cannot escape. It is safely published, as it is put in a concurrent collection
		CacheInstances.add(cache);
	}

	/**
	 * Shuts down all Cache instances, which were registered via {@link #registerCache(Cache)}.
	 * It waits until all cleaners have stopped.
	 */
	public void shutdownAll()
	{
		for (Cache<?> cache : CacheInstances)
		{
			cache.shutdown();
		}
	}
	
	/**
	 * Reports size of all Cache instances, which were registered via {@link #registerCache(Cache)}.
	 * Using this method can create high load, and may require particular permissions, depending on the used object size calculator. 
	 * <p> 
	 * <b>USE WITH CARE!!!</b>
	 */
	public Map<String,CacheSizeInfo> reportAllCacheSizes(ObjectSizeCalculatorInterface objectSizeCalculator)
	{
		Map<String,CacheSizeInfo> infoMap = new HashMap<>();
		for (Cache<?> cache : CacheInstances)
		{
			infoMap.put(cache.id(), cache.reportSize(objectSizeCalculator));
		}
		return infoMap;
	}
	
	/**
	 * Returns the list of Caches that have been registered in this factory.
	 * @return
	 */
	public List<Cache<?>> instances()
	{
		return new ArrayList<Cache<?>>(CacheInstances);
	}

}
