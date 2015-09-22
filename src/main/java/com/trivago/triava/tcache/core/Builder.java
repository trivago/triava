package com.trivago.triava.tcache.core;

import java.util.concurrent.atomic.AtomicInteger;

import com.trivago.triava.tcache.EvictionPolicy;
import com.trivago.triava.tcache.HashImplementation;
import com.trivago.triava.tcache.JamPolicy;
import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.eviction.CacheLimit;
import com.trivago.triava.tcache.eviction.CacheLimitLFUv2;
import com.trivago.triava.tcache.eviction.CacheLimitLRU;
//import com.trivago.triava.tcache.storage.GuavaLocalCache;
import com.trivago.triava.tcache.storage.HighscalelibNonBlockingHashMap;
import com.trivago.triava.tcache.storage.JavaConcurrentHashMap;

/**
 * A builder to create Cache instances.
 * 
 * @author cesken
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class Builder<K,V>
{
	static final AtomicInteger anonymousCacheId = new AtomicInteger();

	long MAX_IDLE_TIME = 1800; // 30 minutes
	private String id;
	private long maxIdleTime = MAX_IDLE_TIME; // 30 minutes
	private long maxCacheTime = 3600; // 60 minutes
	private int maxCacheTimeSpread = 0; // 0 seconds
	private int expectedMapSize = 10000;
	private int concurrencyLevel = 14;
	private int mapConcurrencyLevel = 16;
	private EvictionPolicy evictionPolicy = EvictionPolicy.LFU;
	private EvictionInterface<K, V> evictionClass = null;
	private HashImplementation hashImplementation = HashImplementation.ConcurrentHashMap;
	private TCacheFactory factory = TCacheFactory.standardFactory();
	private JamPolicy jamPolicy = JamPolicy.WAIT;
	private boolean statistics = true;
	private CacheLoader<K, V> loader = null;

	/**
	 * During migration work, we will also have a public constructor
	 */
	public Builder(TCacheFactory factory)
	{
		this.factory = factory;
	}

	/**
	 * Builds a Cache from the parameters that were set. evictionType defines the eviction policy. Any not
	 * explicitly set parameters will get a default values, which are:
	 * 
	 * <pre>
	 * private long maxIdleTime = 1800;
	 * private long maxCacheTime = 3600;
	 * private int expectedMapSize = 10000;
	 * private int concurrencyLevel = 16;
	 * private TCacheEvictionType evictionType = TCacheEvictionType.LFU;
	 * 
	 * </pre>
	 * 
	 * @return
	 */
	public Cache<K, V> build()
	{
		if (id == null)
		{
			id = "tcache-" + anonymousCacheId.incrementAndGet();
		}

		final Cache<K, V> cache;
		if (evictionClass != null)
		{
			cache = new CacheLimit<>(this);
		}
		else
		{
			switch (evictionPolicy)
			{
				case LFU:
					cache = new CacheLimitLFUv2<>(this);
					break;
				case LRU:
					cache = new CacheLimitLRU<>(this);
					break;
				case NONE:
					cache = new Cache<>(this);
				default:
					throw new IllegalArgumentException("Invalid evictionPolicy=" + evictionPolicy);
			}
		}
		
		return cache;
	}

	public Builder<K,V> setId(String id)
	{
		this.id = id;
		return this;
	}

	/**
	 * Sets the maximum time of an unused (idle) cache entry.
	 * 
	 * @param maxIdleTime The maximum time
	 * @return c
	 */
	public Builder<K,V> setMaxIdleTime(long maxIdleTime)
	{
		if (maxIdleTime == 0)
			this.maxIdleTime = MAX_IDLE_TIME;
		else
			this.maxIdleTime = maxIdleTime;
		return this;
	}

	/**
	 * Sets the interval within a cache entry expires. The value in the interval [maxCacheTime, maxCacheTime+interval]
	 * is selected pseudo-randomly for each individual entry put in the cache, unless an explicit expiration time is set in the put() operation. 
	 * <p>
	 * This method is useful for mass-inserts in the Cache, that
	 * should not expire at the same time (e.g. for resource reasons).
	 * 
	 * @param maxCacheTime The minimum time to keep
	 * @param interval The size of the interval
	 * @return This Builder
	 */
	public Builder<K,V> setMaxCacheTime(long maxCacheTime, int interval)
	{
		this.maxCacheTime = maxCacheTime;
		this.maxCacheTimeSpread = interval;
		return this;
	}

	/**
	 * Sets the time when a cache entry expires. All entries put in the cache use this, unless an explicit expiration time is set in the put() operation.
	 * @param maxCacheTime The time to keep
	 * @return This Builder
	 */
	public Builder<K,V> setMaxCacheTime(long maxCacheTime)
	{
		this.maxCacheTime = maxCacheTime;
		return this;
	}

	public Builder<K,V> setExpectedMapSize(int expectedMapSize)
	{
		this.expectedMapSize = expectedMapSize;
		return this;
	}

	/**
	 * Sets the expected concurrency level. In other words, the number of Threads that concurrently write to the Cache.
	 * The underlying ConcurrentMap will use the concurrencyLevel to tune its internal data structures for concurrent
	 * usage. For example, the Java ConcurrentHashMap uses this value as-is.
	 * Default is 16, and the minimum is 8.
	 * <p>
	 * If not set, the default concurrencyLevel is 16, which should usually rarely create thread contention.
	 * If running with 12 cores (24 with hyperthreading) chances are not too high for contention.
	 * A note from the  Java 6 API docs: "overestimates and underestimates within an order
	 * of magnitude do not usually have much noticeable impact."
	 * <p>
	 * For example, in a scenario where 150 Threads write concurrently via putIfAbsent(), only 12 Threads will
	 * actually run. As concurrencyLevel is 16, threads will usually rarely block. But in case of unlucky hash
	 * bucket distribution or if too many Threads get suspended during holding a lock, issues could arise. If the
	 * underlying ConcurrentMap uses unfair locks, it might even lead to thread starvation.
	 */
	public Builder<K,V> setConcurrencyLevel(int concurrencyLevel)
	{
		this.concurrencyLevel = concurrencyLevel;
		this.mapConcurrencyLevel = Math.max(concurrencyLevel + 2, 8);
		return this;
	}

	public Builder<K,V> setEvictionPolicy(EvictionPolicy evictionPolicy)
	{
		this.evictionPolicy = evictionPolicy;
		this.evictionClass = null;
		return this;
	}

	public Builder<K,V> setHashImplementation(HashImplementation hashImplementation)
	{
		this.hashImplementation = hashImplementation;
		return this;
	}

	public Builder<K,V> setJamPolicy(JamPolicy jamPolicy)
	{
		this.jamPolicy = jamPolicy;
		return this;
	}

	/**
	 * @return the factory
	 */
	public TCacheFactory getFactory()
	{
		return factory;
	}

	/**
	 * @param factory the factory to set
	 */
	public void setFactory(TCacheFactory factory)
	{
		this.factory = factory;
	}

	public boolean getStatistics()
	{
		return statistics;
	}

	/**
	 * Sets whether statistics should be gathered. The performance impact on creating statistics is very low,
	 * so it is safe to activate statistics. If this method is not called, the default is to collect statistics.
	 * @param statistics
	 * @return
	 */
	public Builder<K,V> setStatistics(boolean statistics)
	{
		this.statistics = statistics;
		return this;
	}

	/**
	 * @return the id
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * @return the maxIdleTime
	 */
	public long getMaxIdleTime()
	{
		return maxIdleTime;
	}

	/**
	 * @return The lower bound of the "maximum cache time interval" [maxCacheTime, maxCacheTime+maxCacheTimeSpread]
	 */
	public long getMaxCacheTime()
	{
		return maxCacheTime;
	}

	/**
	 * 
	 * @return The interval size of the cache time interval
	 */
	public int getMaxCacheTimeSpread()
	{
		return maxCacheTimeSpread;
	}

	/**
	 * @return the expectedMapSize
	 */
	public int getExpectedMapSize()
	{
		return expectedMapSize;
	}

	/**
	 * @return the concurrencyLevel
	 */
	public int getConcurrencyLevel()
	{
		return concurrencyLevel;
	}

	public int getMapConcurrencyLevel()
	{
		return mapConcurrencyLevel;
	}

	/**
	 * @return the evictionPolicy
	 */
	public EvictionPolicy getEvictionPolicy()
	{
		return evictionPolicy;
	}

	/**
	 * @return the hashImplementation
	 */
	public HashImplementation getHashImplementation()
	{
		return hashImplementation;
	}

	public StorageBackend<K, V> storageFactory()
	{
		switch (hashImplementation)
		{
			case ConcurrentHashMap:
				return new JavaConcurrentHashMap<K, V>();
//			case PerfTestGuavaLocalCache:
//				return new GuavaLocalCache<K, V>();
			case HighscalelibNonBlockingHashMap:
				// throw new IllegalArgumentException("NonBlockingHashMap not yet implemented");
				return new HighscalelibNonBlockingHashMap<K, V>();
			default:
				return null;
		}
	}

	public JamPolicy getJamPolicy()
	{
		return jamPolicy;
	}

	// unchecked, as the corresponding setter is generics type-safe
	public CacheLoader<K, V> getLoader()
	{
		return (CacheLoader<K, V>) loader;
	}

	public Builder<K, V> setLoader(CacheLoader<K, V> loader)
	{
		this.loader = loader;
		return this;
	}

	public Builder<K,V> setEvictionClass(EvictionInterface<K, V> clazz)
	{
		this.evictionPolicy = EvictionPolicy.CUSTOM;
		this.evictionClass = clazz;
		return this;
	}

	/**
	 * @return the evictionClass. null if there is no custom class
	 */
	public EvictionInterface<K, V> getEvictionClass()
	{
		return evictionClass;
	}

}
