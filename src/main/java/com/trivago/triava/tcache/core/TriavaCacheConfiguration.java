/*********************************************************************************
 * Copyright 2015-present trivago GmbH
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

package com.trivago.triava.tcache.core;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheWriter;

import com.trivago.triava.annotations.Beta;
import com.trivago.triava.tcache.Cache;
import com.trivago.triava.tcache.CacheWriteMode;
import com.trivago.triava.tcache.EvictionPolicy;
import com.trivago.triava.tcache.HashImplementation;
import com.trivago.triava.tcache.JamPolicy;
import com.trivago.triava.tcache.eviction.EvictionInterface;
import com.trivago.triava.tcache.util.Weigher;
import com.trivago.triava.time.TimeSource;

/**
 * A Builder to create Cache instances. A Builder instance must be retrieved via a TCacheFactory,
 * to guarantee each created Cache will be registered in a CacheManager (Hint: The TCacheFactory
 * implements CacheManager). 
 * 
 * @author cesken
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public interface TriavaCacheConfiguration<K,V,B extends TriavaCacheConfiguration<K, V, B>> extends CompleteConfiguration<K, V>
{

	/**
	 * Sets the id of this Cache. The id is identical with the JSR107 cache name.
	 * @param id The cache name
	 * @return This Builder
	 */
	B setId(String id);

	/**
	 * Sets the maximum time of an unused (idle) cache entry. It will overwrite any values set before via {@link #setExpiryPolicyFactory(Factory)}.
	 * 
	 * @param maxIdleTime The maximum time
	 * @param timeUnit The TimeUnit of maxIdleTime
	 * @return This Builder
	 */
	B setMaxIdleTime(int maxIdleTime, TimeUnit timeUnit);

	/**
	 * Sets the interval within a cache entry expires. The value in the interval [maxCacheTime, maxCacheTime+interval]
	 * is selected pseudo-randomly for each individual entry put in the cache, unless an explicit expiration time is set in the put() operation. 
	 * <p>
	 * This method is useful for mass-inserts in the Cache, that
	 * should not expire at the same time (e.g. for resource reasons).
	 * 
	 * @param maxCacheTime The minimum time to keep in seconds
	 * @param interval The size of the interval in seconds
	 * @param timeUnit The TimeUnit of maxCacheTime and interval
	 * @return This Builder
	 */
	B setMaxCacheTime(int maxCacheTime, int interval, TimeUnit timeUnit);

	/**
	 * Sets the default expiration time for entries in this cache. All entries use this time, unless it is
	 * added using a put method that allows overriding the expiration time, like
	 * {@link Cache#put(Object, Object, int, int, TimeUnit)}.
	 * 
	 * @param maxCacheTime
	 *            The time to keep the value in seconds
	 * @param timeUnit The TimeUnit of maxCacheTime
	 * @return This Builder
	 */
	B setMaxCacheTime(int maxCacheTime, TimeUnit timeUnit);

	/**
	 * Sets the proposed cleanup interval for expiring cache entries. The Cache will use this value
	 * when doing batch expiration. Out-of-bound values are
	 * auto-tuned by the Cache to sane limits, which are currently between 1 ms and 5 minutes.
	 * <p>
	 * If you do not call this method, the default
	 * cleanup interval is used. For JSR107 Caches this is 1 minute. For native Triava Caches this 
	 * is auto-optimized according to a fraction of {@link #getMaxIdleTime()}. 
	 * 
	 * @param cleanupInterval The eviction cleanup interval. 0 means auto-tuning.
	 * @param timeUnit The TimeUnit of cleanupInterval
	 * @return This Builder
	 */
	B setCleanupInterval(int cleanupInterval, TimeUnit timeUnit);

	/**
	 * Sets the expected number of elements to be stored. Cache instances with eviction policy will start evicting
	 * after reaching {@link #getMaxElements()}. Cache instances of unlimited size
	 * {@link EvictionPolicy}.NONE will use this value only as a hint
	 * for initially sizing the underlying storage structures.
	 * 
	 * @param maxElements The maximum number of elements to be stored
	 * @return This Builder
	 */
	B setMaxElements(int maxElements);

	/**
	 * Sets the expected concurrency level. In other words, the number of application Threads that concurrently write to the Cache.
	 * The underlying ConcurrentMap will use the concurrencyLevel to tune its internal data structures for concurrent
	 * usage. For example, the Java ConcurrentHashMap uses this value as-is.
	 * Default is 14, and the minimum is 8.
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
	 * 
	 * @param concurrencyLevel The excpected number of application Threads that concurrently write to the Cache
	 * @return This Builder
	 */
	B setConcurrencyLevel(int concurrencyLevel);

	/**
	 * Sets the eviction policy, for example LFU or LRU. 
	 * <p>
	 * If you want to use a custom eviction strategy,
	 * use {@link #setEvictionClass(EvictionInterface)} instead.
	 *  
	 * @param evictionPolicy The EvictionPolicy
	 * @return This Builder
	 */
	B setEvictionPolicy(EvictionPolicy evictionPolicy);

	/**
	 * Sets a custom eviction policy. This is useful if the value V holds sensible information that can be used for
	 * eviction. For example if V is a Session class, it could hold information about the user: Guest users may be evicted
	 * before registered users, and the last chosen should be premium users.
	 * <p>
	 * If you want to use a standard eviction strategy like LFU or LRU,
	 * use {@link #setEvictionPolicy(EvictionPolicy)} instead.
	 *  
	 * @param clazz The instance that implements the eviction policy
	 * @return This Builder
	 */
	B setEvictionClass(EvictionInterface<K, V> clazz);

	/**
	 * @return the evictionClass. null if there is no custom class
	 */
	EvictionInterface<K, V> getEvictionClass();

    /**
     * Sets the TimeSource to use for within this Cache. Whenever a timestamp for the current time is required,
     * that TimeSource is used. This includes the insert timestamp for write operations like put() or replace(),
     * and for determining the expiration time. If #timeSource is null, then the default TimeSource is used, which
     * has a 10ms precision.
     *
     * @param timeSource The TimeSource. null means to use the triava default TimeSource
     * @return This Builder
     */
    B setTimeSource(TimeSource timeSource);

    /**
     * @return the TimeSource. null if the default TimeSource
     */
    TimeSource getTimeSource();

    /**
	 * Set the StorageBackend for the underlying ConcurrentMap. If this method is not called,
	 * ConcurrentHashMap will be used.
	 *  
	 * @param hashImplementation The {@link HashImplementation}
	 * @return This Builder
	 * 
	 */
	@Beta(comment="To be replaced by a method to set a StorageBackend")
	B setHashImplementation(HashImplementation hashImplementation);

	/**
	 * Sets the policy, how a Thread that calls put() will behave the cache is full.
	 * Either the Thread will WAIT or DROP the element and not put it in the cache.
	 * The default is WAIT. The {@link JamPolicy} has no effect on caches of unlimited size
	 * {@link EvictionPolicy}}.NONE.
	 * 
	 * @param jamPolicy The {@link JamPolicy}
	 * @return This Builder
	 */
	B setJamPolicy(JamPolicy jamPolicy);

    /**
     * Sets the Weigher that determines the weight of an entry. If the Weigher is non-null, the Cache will
     * weigh each element and limit the size by the maximum weight.
     *
     * @param weigher The weigher
     * @return This Builder
     */
    B setWeigher(Weigher weigher);

    /**
     * Gets the Weigher that determines the weight of an entry.
     */
     Weigher getWeigher();

	/**
	 * Returns whether statistics are enabled
	 * @return true, if statistics are enabled
	 */
	boolean getStatistics();

	/**
	 * Sets whether statistics should be gathered. The performance impact on creating statistics is very low,
	 * so it is safe to activate statistics. If this method is not called, the default is to collect statistics.
	 * 
	 * @param statistics true, if you want to switch on statistics 
	 * @return This Builder
	 */
	B setStatistics(boolean statistics);

	/**
	 * Sets whether management should be enabled.
	 * 
	 * @param management true, if you want to switch on management 
	 * @return This Builder
	 */
	B setManagement(boolean management);

	/**
	 * @return the id
	 */
	String getId();

	/**
	 * @return the maxIdleTime in milliseconds.
	 */
	long getMaxIdleTime();

	/**
	 * @return The lower bound of the "maximum cache time interval" [maxCacheTime, maxCacheTime+maxCacheTimeSpread] in milliseconds.
	 */
	long getMaxCacheTime();

	/**
	 * 
	 * @return The interval size of the cache time interval in milliseconds.
	 */
	long getMaxCacheTimeSpread();

	/**
	 * Returns the proposed cleanup interval in ms. See {@link #setCleanupInterval(int, TimeUnit)} for details on how the Cache uses this value.   
	 * @return The proposed cleanup interval in ms. 0 means auto-tuning
	 */
	long getCleanUpIntervalMillis();
	
	/**
	 * @see #setMaxElements(int)
	 * @return the maximum number of elements.
	 */
	int getMaxElements();

	/**
	 * @return the concurrencyLevel
	 */
	int getConcurrencyLevel();

	int getMapConcurrencyLevel();

	/**
	 * @return the evictionPolicy
	 */
	EvictionPolicy getEvictionPolicy();

	/**
	 * @return the hashImplementation
	 */
	HashImplementation getHashImplementation();

	StorageBackend<K, V> storageFactory();

	JamPolicy getJamPolicy();

	@Override // JSR107
	Class<K> getKeyType();

	@Override // JSR107
	Class<V> getValueType();

	/**
	 * @see #getKeyType()
	 * @param keyType the required key type
	 */
	void setKeyType(Class<K> keyType);

	/**
	 * @see #getValueType()
	 * @param valueType the required value type
	 */
	void setValueType(Class<V> valueType);

	@Override // JSR107
	boolean isStoreByValue();
	
	CacheWriteMode getCacheWriteMode();

	B setCacheWriteMode(CacheWriteMode writeMode);

	enum PropsType { CacheManager, Cache };
	
	/**
	 * Returns a representation of the Configuration as Properties.
	 * The returned properties are a private copy for the caller and thus not shared amongst different callers.
	 * Changes to the returned Properties have no effect on the Cache.
	 * 
	 * @param propsType If PropsType.CacheManager, the Cache specific properties (cacheName, cacheLoaderClass) are excluded
	 * @return The current configuration
	 */
	Properties asProperties(PropsType propsType);

	@Override
	int hashCode();

	@Override
	boolean equals(Object obj);


	// --- Cache-Loader, Cache-Writer and Read-Through, Write-Through ------------------------------------------
	B setLoader(CacheLoader<K, V> loader);
	B setCacheLoaderFactory(Factory<javax.cache.integration.CacheLoader<K, V>> loaderFactory);
	B setCacheWriterFactory(Factory<? extends CacheWriter<? super K, ? super V>> factory);
	B setReadThrough(boolean isReadThrough);
	B setWriteThrough(boolean isWriteThrough);

	CacheLoader<K, V> getLoader();
	@Override
	Factory<javax.cache.integration.CacheLoader<K, V>> getCacheLoaderFactory();
	@Override
	Factory<CacheWriter<? super K, ? super V>> getCacheWriterFactory();
	@Override
	boolean isReadThrough();
	@Override
	boolean isWriteThrough();



	// --- Management and statistics ------------------------------------------
	@Override
	boolean isStatisticsEnabled();

	@Override
	boolean isManagementEnabled();

	/**
	 * Returns whether the Cache behaves strictly JSR107 compliant
	 * 
	 * @return true if the Cache behaves strictly JSR107 compliant
	 */
	boolean isStrictJSR107();

	void setStrictJSR107(boolean strictJSR107);

	@Override
	Iterable<CacheEntryListenerConfiguration<K, V>> getCacheEntryListenerConfigurations();


	@Override
	Factory<ExpiryPolicy> getExpiryPolicyFactory();

	/**
	 * Sets the ExpiryPolicyFactory. It will overwrite any values set before via {@link #setMaxIdleTime(int, TimeUnit)}.
	 * @param factory The factory
	 * @return This Builder
	 */
	B setExpiryPolicyFactory(Factory<? extends ExpiryPolicy> factory);

	void addCacheEntryListenerConfiguration(CacheEntryListenerConfiguration<K, V> listenerConfiguration);

	void removeCacheEntryListenerConfiguration(CacheEntryListenerConfiguration<K, V> listenerConfiguration);
}
