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

package com.trivago.triava.tcache.eviction;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.cache.configuration.Factory;
import javax.cache.event.EventType;
import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import javax.cache.integration.CacheWriter;

import com.trivago.triava.collections.HashInterner;
import com.trivago.triava.logging.TriavaLogger;
import com.trivago.triava.logging.TriavaNullLogger;
import com.trivago.triava.tcache.CacheWriteMode;
import com.trivago.triava.tcache.JamPolicy;
import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.action.ActionContext;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.CacheWriterWrapper;
import com.trivago.triava.tcache.core.NopCacheWriter;
import com.trivago.triava.tcache.core.StorageBackend;
import com.trivago.triava.tcache.event.ListenerCollection;
import com.trivago.triava.tcache.expiry.Constants;
import com.trivago.triava.tcache.expiry.TCacheExpiryPolicy;
import com.trivago.triava.tcache.expiry.TouchedExpiryPolicy;
import com.trivago.triava.tcache.expiry.UntouchedExpiryPolicy;
import com.trivago.triava.tcache.statistics.HitAndMissDifference;
import com.trivago.triava.tcache.statistics.NullStatisticsCalculator;
import com.trivago.triava.tcache.statistics.StandardStatisticsCalculator;
import com.trivago.triava.tcache.statistics.StatisticsCalculator;
import com.trivago.triava.tcache.statistics.TCacheStatistics;
import com.trivago.triava.tcache.statistics.TCacheStatisticsInterface;
import com.trivago.triava.tcache.statistics.TCacheStatisticsMBean;
import com.trivago.triava.tcache.storage.ByteArray;
import com.trivago.triava.tcache.storage.ConcurrentKeyDeserMap;
import com.trivago.triava.tcache.util.CacheSizeInfo;
import com.trivago.triava.tcache.util.ChangeStatus;
import com.trivago.triava.tcache.util.KeyValueUtil;
import com.trivago.triava.tcache.util.ObjectSizeCalculatorInterface;
import com.trivago.triava.tcache.util.TCacheConfigurationMBean;
import com.trivago.triava.time.EstimatorTimeSource;
import com.trivago.triava.time.SystemTimeSource;
import com.trivago.triava.time.TimeSource;


/**
 * A Cache that supports expiration based on expiration time and idle time.
 * The Cache can be of unbounded or bounded size. In the latter case, eviction is done using the selected strategy, for example
 * LFU, LRU, Clock or an own implemented custom eviction strategy (value-type aware). You can retrieve either instance by using
 * a Builder:
 * <pre>
 * // Build native TCache instance
 * Builder&lt;String, Integer&gt; builder = TCacheFactory.standardFactory().builder();
 * builder.setXYZ(value);
 * Cache&lt;String, Integer&gt; cache = builder.build();
 * javax.cache.Cache&lt;String, Integer&gt; jsr107cache = tcache.jsr107cache();
 * </pre>
 * The last line above is optional, to get a JSR107 compliant Cache instance. Alternatively that could also be created via the JSR107 API:
 * <pre>
 * // Build JSR107 Cache instance
 * CachingProvider cachingProvider = Caching.getCachingProvider();
 * CacheManager cacheManager = cachingProvider.getCacheManager();
 * MutableConfiguration&lt;String, Integer&gt; config = new MutableConfiguration&lt;&gt;()
 *   .setXYZ(value); // Configure
 * javax.cache.Cache&lt;String, Integer&gt; cache = cacheManager.createCache("simpleCache", config);  // Build
 * </pre>
 * 
 * <p>
 * Implementation note:
 * This Cache class is built for maximum throughput and scalability, by executing CPU intensive tasks like evictions in
 * separate Threads.
 * The basic limitations are those of the underlying ConcurrentMap implementation, as no further data
 * structures are maintained. This is also true for the subclasses that allow evictions.
 * 
 * @param <K> The Key type
 * @param <V> The Value type
 *
 * @author Christian Esken
 * @since 2009-06-10
 *
 */
public class Cache<K, V> implements Thread.UncaughtExceptionHandler, ActionContext<K, V>
{
	static TriavaLogger logger = new TriavaNullLogger();
	
	final private String id;
	final Builder<K,V> builder; // A reference to the Builder that created this Cache
	final private boolean strictJSR107;
	final static long baseTimeMillis = System.currentTimeMillis();
	
	final TCacheExpiryPolicy expiryPolicy;
	// max cache time in seconds
	private final long maxCacheTime;
	protected int maxCacheTimeSpread;
	
	
	final protected ConcurrentMap<K,AccessTimeObjectHolder<V>> objects;
	Random random = new Random(System.currentTimeMillis());
	
//	@ObjectSizeCalculatorIgnore
	private volatile transient CleanupThread cleaner = null;
	private volatile long cleanUpIntervalMillis;

	final HashInterner<V> interner = new HashInterner<>(100);

	/**
	 * Cache hit counter.
	 * We do not expect any overruns, as we use "long" to count hits and misses.
	 * If we would do 10 Cache GET's per second, this would mean 864.000 GET's per day and 6.048.000 per Week.
	 * Even counting with "int" would be enough (2.147.483.647), as we would get no overrun within roughly 35 weeks.
	 * If we have 10 times the load this would still be enough for 3,5 weeks with int, and "infinitely" more with long.
	 */
	StatisticsCalculator statisticsCalculator = null;

	private float[] hitrateLastMeasurements = new float[5];
	int hitrateLastMeasurementsCurrentIndex = 0;
	private volatile boolean shuttingDown = false;

	protected JamPolicy jamPolicy = JamPolicy.WAIT;
	protected final CacheLoader<K, V> loader;

	final ListenerCollection<K,V> listeners;

	final TCacheJSR107<K, V> tCacheJSR107;
	final CacheWriter<K, V> cacheWriter;
	
	final KeyValueUtil<K,V> kvUtil;

	/**
	 * constructor with default cache time and expected map size.
	 * @param maxIdleTime Maximum idle time in seconds
	 * @param maxCacheTime Maximum Cache time in seconds
	 * @param expectedMapSize
	 */
	Cache(String id, long maxIdleTime, long maxCacheTime, int expectedMapSize)
	{
		this( new Builder<K, V>(TCacheFactory.standardFactory())
		.setId(id).setMaxIdleTime(maxIdleTime).setMaxCacheTime(maxCacheTime)
		.setExpectedMapSize(expectedMapSize) );
	}


	/**
	 * Construct a Cache, using the given configuration from the Builder.
	 * @param builder The builder containing the configuration
	 */
	public Cache(Builder<K,V> builder)
	{
		this.id = builder.getId();
		this.strictJSR107 = builder.isStrictJSR107();
		this.kvUtil = new KeyValueUtil<K,V>(id);
		this.builder = builder;
		tCacheJSR107 = new TCacheJSR107<K, V>(this);
		
		
		// Expiration
		this.maxCacheTime = builder.getMaxCacheTime();
		this.maxCacheTimeSpread = builder.getMaxCacheTimeSpread();
		
		long expiryIdleMillis = 0;
		ExpiryPolicy epFromFactory = builder.getExpiryPolicyFactory().create();
		if (strictJSR107)
		{
			this.expiryPolicy = new TouchedExpiryPolicy(epFromFactory);
			// TODO Find a suitable way to calculate expiryIdleMillis for strict JSR107 mode.
			//  Issue:The JSR107 TCK disallows free use of expiryPolicy methods. When using it here, the TCK will record failures,
			//       because it requires that methods are ONLY called at very specific times. 
		}
		else
		{
			this.expiryPolicy = new UntouchedExpiryPolicy(epFromFactory);
			
			Duration expiryDuration = epFromFactory.getExpiryForAccess();
			if (expiryDuration == null)
				expiryDuration = epFromFactory.getExpiryForCreation();
			expiryIdleMillis = expiryDuration.getAdjustedTime(0);
		}
		
		// Cleaner should run often enough, but not too often. The chosen time is expiryIdleMillis / 10, but limited to 5min.
		if (expiryIdleMillis <= 0)
		{
			expiryIdleMillis = 60_000;
		}		
		this.cleanUpIntervalMillis = Math.min(5*60_000, expiryIdleMillis / 10);
		
		
		this.jamPolicy = builder.getJamPolicy();
		// CacheLoader directly or via CacheLoaderFactory 
		Factory<javax.cache.integration.CacheLoader<K, V>> lf = builder.getCacheLoaderFactory();
		if (lf != null)
		{
			this.loader  = lf.create();
		}
		else
		{
			this.loader  = builder.getLoader();
		}
		if (this.loader == null && builder.isReadThrough())
		{
			throw new IllegalArgumentException("Builder has isReadThrough, but has no loader for cache: " + id);
		}
		
		Factory<CacheWriter<? super K, ? super V>> cwFactory = builder.getCacheWriterFactory();
		if (cwFactory == null)
		{
			this.cacheWriter = new NopCacheWriter<K,V>();
		}
		else
		{
			CacheWriter<? super K, ? super V> cw = cwFactory.create();
			CacheWriterWrapper<K, V> cwWrapper = new CacheWriterWrapper<K,V>(cw, false);
			this.cacheWriter = cwWrapper;
		}

		objects = createBackingMap(builder);
		
		enableStatistics(builder.getStatistics());
		enableManagement(builder.isManagementEnabled());
		
	    activateTimeSource();
	    
	    listeners = new ListenerCollection<>(this, builder);

	    tCacheJSR107.refreshActionRunners();
	    // TODO The call here is pretty awkward. It must be moved to TCacheFactory.createCache();
		builder.getFactory().registerCache(this);
	}


	@SuppressWarnings("unchecked") // Avoid warning for the "generics cast" in the last line
	private ConcurrentMap<K, AccessTimeObjectHolder<V>> createBackingMap(Builder<K, V> builder)
	{
		StorageBackend<K, V> storageFactory = builder.storageFactory();

		ConcurrentMap<K, ? extends TCacheHolder<V>> map = storageFactory.createMap(builder, evictionExtraSpace(builder));

		CacheWriteMode cacheWriteMode = builder.getCacheWriteMode();
		if (cacheWriteMode.isStoreByValue())
		{
			ConcurrentMap<ByteArray, AccessTimeObjectHolder<V>> castedMap = (ConcurrentMap<ByteArray, AccessTimeObjectHolder<V>>)map;
			return new ConcurrentKeyDeserMap<K, AccessTimeObjectHolder<V>>(castedMap, cacheWriteMode);

		}
		else
		{
			ConcurrentMap<K, AccessTimeObjectHolder<V>> castedMap = (ConcurrentMap<K,AccessTimeObjectHolder<V>>)map;
			return castedMap;			
		}

	}

	/**
	 * Returns a size factor for the map for the specific eviction strategy of this Cache. The default implementation
	 * returns 0, as it does not use any extra space.
	 *
	 * @param builder The builder containing the configuration
	 * @return The number of extra elements required in the storage Map.
	 */
	protected int evictionExtraSpace(Builder<K, V> builder)
	{
		return 0;
	}
	
	
	

	public String id()
	{
		return id;
	}

	// TODO Make package private
	public Builder<K, V> builder()
	{
		return builder;
	}


	/**
	 * Can be overridden by implementations, if they require a custom clean up.
	 * In overridden, super.shutdown() must be called.
	 */
	public final void shutdown()
	{
		shuttingDown = true;
		shutdownCustomImpl();
		shutdownPrivate();
	}

	/**
	 * Shuts down the implementation specific parts. This method is called as part of {@link #shutdown()}.
	 * The Cache is at this point already treated as closed, and {@link #isClosed()} will return true.
	 * The default implementation does nothing.
	 */
	void shutdownCustomImpl()
	{
	}


	public boolean isClosed()
	{
		return shuttingDown;
	}
	
	final static long MAX_SHUTDOWN_WAIT_MILLIS = 100; // 100 ms

	/**
	 * Shuts down this Cache. The steps are:
	 * Disable MBeans, remove all cache entries, stop the cleaner.
	 * can be placed in the Cache. 
	 */
	private void shutdownPrivate()
	{	
		enableStatistics(false);
		enableManagement(false);
		listeners.shutdown();
		String errorMsg = stopAndClear(MAX_SHUTDOWN_WAIT_MILLIS);
		if (errorMsg != null)
		{
			logger.error("Shutting down Cache " + id + " FAILED. Reason: " + errorMsg);
		}
		else
		{
			logger.info("Shutting down Cache " + id + " OK");
		}

	}

	
	/**
	 * Waits at most millis milliseconds plus nanos nanoseconds for the given thread to die. 
	 *  
	 * If the calling thread gets interrupted during the call to this method, the method will preserve the interruption status, but
	 * will continue running until the given thread has stopped or the timeout has passed.
	 * 
	 * @param thread The target thread
	 * @param millis The number of milliseconds to wait
	 * @param nanos The number of nanoseconds to wait
	 * @return true if the thread is not running, false if it is alive.
	 */
	public boolean joinSimple(Thread thread, long millis, int nanos)
	{
		long waitUntil = System.currentTimeMillis() + millis;
		boolean interrupted = false;
		while (true)
		{
			long remainingMillis = waitUntil - System.currentTimeMillis();
			
			boolean timeout = remainingMillis < 0 || (remainingMillis==0 && nanos == 0);
//			logger.info("joinSimple() checks rtm=" + remainingMillis + ", timeout=" + timeout + " : thread=" + thread);
			if (timeout)
			{
				break;
			}
			try
			{
				thread.join(remainingMillis, nanos);
//				logger.info("thread.join() returned: thread=" + thread);
				break; // if we reach this line, the join was successful
			}
			catch (InterruptedException e)
			{
				interrupted = true;
			}
		}
		
		if (interrupted)
		{
			Thread.currentThread().interrupt();
		}

		boolean stopped = ! thread.isAlive();
//		logger.info("joinSimple() finds stopped=" + stopped + ": thread=" + thread);
		return stopped;
	}


	/**
	 * Copied from com.trivago.commons.util.Util.sleepSimple()
	 * Sleep the given number of milliseconds. This method returns after the given time or if the calling
	 * thread gets interrupted via InterruptedException. As the method potentially sleeps much shorter than
	 * wanted due to InterruptedException, you should only call this method when you are prepared to handle
	 * shorter sleep times.
	 * <br>
	 * Future directions: This method should possibly want to call Thread.interrupt() in case of InterruptedException,
	 * so callers at least have the chance to be aware of the interruption. Or it could check the server status
	 * and throw something like ServiceShuttingDownException(). Before doing ANY of this, we need to think about
	 * how (and whether) we want to provide a safe service shutdown. 
	 * 
	 * @param sleepMillis The sleep time in milliseconds
	 */
	public static void sleepSimple(long sleepMillis)
	{
		if (sleepMillis <= 0)
			return;

		try
		{
			Thread.sleep(sleepMillis);
		}
		catch (InterruptedException e)
		{ // Thread.currentThread().interrupt(); // cesken Add this after release
		} // ignore, as documented
	}

	/**
	 * 
	 * @return Maximum Cache time in seconds
	 */
	public long getMaxCacheTime() {
		return this.maxCacheTime;
	}

	/**
	 * Add an object to the cache under the given key, using the default idle time and default cache time.
	 * @param key The key
	 * @param value The value
	 */
	public void put(K key, V value)
	{
		putToMap(key, value, Constants.EXPIRY_NOCHANGE, cacheTimeSpread(), false, false);
	}

	Random randomCacheTime = new Random(System.currentTimeMillis());
	
	/**
	 * Returns a pseudorandom, uniformly distributed cache time in seconds between {@link #maxCacheTime} and {@link #maxCacheTime} + {@link #maxCacheTimeSpread} -1.
	 * 
	 * @return The cache time
	 */
	int cacheTimeSpread()
	{
		final long spread;
		if (maxCacheTimeSpread == 0)
			spread = maxCacheTime;
		else
			spread = maxCacheTime + randomCacheTime.nextInt(maxCacheTimeSpread);
		
		return AccessTimeObjectHolder.limitToPositiveInt(spread);
	}


	/**
	 * Add an object to the cache under the given key with the given idle and cache times.
	 * @param key The key
	 * @param value The value
	 * @param maxIdleTime The idle time in seconds
	 * @param maxCacheTime The cache time in seconds
	 */
	public void put(K key, V value, int maxIdleTime, int maxCacheTime)
	{
		if (maxIdleTime > Integer.MAX_VALUE || maxCacheTime > Integer.MAX_VALUE)
		{
			throw new IllegalArgumentException("maxIdleTime and maxCacheTime must be in Integer range: " + maxIdleTime + "," + maxCacheTime);
		}
		putToMap(key, value, maxIdleTime, maxCacheTime, false, false);
	}

	/**
	 * The same like {@link #put(Object, Object, int, int)}, but uses
	 * {@link java.util.concurrent.ConcurrentMap#putIfAbsent(Object, Object)} to actually write
	 * the data in the backing ConcurrentMap. For the sake of the Cache hit or miss statistics this method
	 * is a treated as a read operation and thus updates the hit or miss counters. Rationale is that the
	 * putIfAbsent() result is usually evaluated by the caller.
	 * 
	 * @param key The key
	 * @param value The value
	 * @param maxIdleTime Maximum idle time in seconds
	 * @param maxCacheTime Maximum cache time in seconds
	 * @return See {@link ConcurrentHashMap#putIfAbsent(Object, Object)}
	 */
	public V putIfAbsent(K key, V value, int maxIdleTime, int maxCacheTime)
	{
		AccessTimeObjectHolder<V> holder = putToMap(key, value, maxIdleTime, maxCacheTime, true, false);
		if ( holder == null )
			return null;
		else
			return holder.peek();
	}
	
	/**
	 * The same like {@link #putIfAbsent(Object, Object, int, int)}, but uses
	 * the default idle and cache times. The cache time will use the cache time spread if this cache
	 * is configured to use it.
	 * 
	 * @param key The key
	 * @param value The value
	 * @return See {@link ConcurrentHashMap#putIfAbsent(Object, Object)}
	 */
	public V putIfAbsent(K key, V value)
	{
		// Hint idleTime is passed as Constants.EXPIRY_MAX, but it is not evaluated.
		AccessTimeObjectHolder<V> holder = putToMap(key, value, Constants.EXPIRY_MAX, cacheTimeSpread(), true, false);
		
		if (holder == null)
		{
			return null;
		}
		
		return holder.peek();
	}
	

	/**
	 * Puts the value wrapped in a AccessTimeObjectHolder in the map and returns it. What exactly is returned depends on the value of returnEffectiveHolder. 
	 * If putIfAbsent is true, the put is done in the fashion of {@link java.util.concurrent.ConcurrentMap#putIfAbsent(Object, Object)}, otherwise
	 * like {@link java.util.Map#put(Object, Object)}.
	 * 
	 * @param key The key
	 * @param data The value
	 * @param idleTime in seconds
	 * @param cacheTime  Max Cache time in seconds
	 * @param putIfAbsent Defines the behavior when the key is already present. See method documentation. 
	 * @param returnEffectiveHolder If true, the holder object of the object in the Map is returned. If returnEffectiveHolder is false, the returned value is like described in {@link java.util.Map#put(Object, Object)}.
	 * @return The holder reference, as described in the returnEffectiveHolder parameter
	 */
	protected AccessTimeObjectHolder<V> putToMap(K key, V data, int idleTime, long cacheTime, boolean putIfAbsent, boolean returnEffectiveHolder)
	{
		Holders<V> holders = putToMapI(key, data, idleTime, cacheTime, putIfAbsent);
		if (holders == null)
			return null;
		return returnEffectiveHolder ? holders.effectiveHolder : holders.oldHolder;
		
	}
	protected Holders<V> putToMapI(K key, V data, int idleTime, long cacheTime, boolean putIfAbsent)
	{
		if (isClosed())
		{
			// We don't accept new entries if this Cache is shutting down
			if (strictJSR107)
				throw new IllegalStateException("Cache is closed:" + id);
			else
				return null;
		}

		kvUtil.verifyKeyAndValueNotNull(key, data);
		
//		if (idleTime == AccessTimeObjectHolder.EXPIRY_ZERO)
//		{
//			// We cannot do this fast-path exit, as there may be an old entry in the cache and we must "remove/invalidate" it 		 
//			return null; // already expired
//		}
		
		boolean hasCapacity = ensureFreeCapacity();
		if (!hasCapacity)
		{
			statisticsCalculator.incrementDropCount();
			return null;
		}

		if (cacheTime <= 0) // Future directions: Probably change this, to say: 0 or lower is already expired
			cacheTime = this.maxCacheTime;
		
//		if (idleTime <= 0 && cacheTime <= 0) 
//		{
//			logger.error("Adding object to Cache with infinite lifetime: " + key.toString() + " : " + data.toString());
//		}
		
		AccessTimeObjectHolder<V> holder; // holder returned by objects.put*().
		AccessTimeObjectHolder<V> newHolder; // holder that was created via new.
		AccessTimeObjectHolder<V> effectiveHolder; // holder that is effectively in the Cache
		
		boolean hasPut = false;
		
		if (putIfAbsent)
		{
			// Always use expiryForCreation. Either it is correct, or we do not care(wrong but not added to cache) 
			newHolder = new AccessTimeObjectHolder<V>(data, builder.getCacheWriteMode());
			holder = this.objects.putIfAbsent(key, newHolder);
			
			/*
			 *	A putIfAbsent() is also treated as a GET operation, so update cache statistics. 
			 *	See putIfAbsent() docs above for a more  detailed explanation.
			 */
			if (holder == null)
			{
				newHolder.complete(expiryPolicy.getExpiryForCreation(), cacheTime);
				hasPut = true;
				if (!strictJSR107)
				{
					// TCK CHALLENGE
					// JSR107 TCK does not allow incrementing the miss count. CacheMBStatisticsBeanTest.java:607 , testPutIfAbsent()
					// So we only go here in native tcache mode (== !strictJSR107)
					statisticsCalculator.incrementMissCount();
				}
				effectiveHolder = newHolder;
			}
			else
			{
				// not put
				if (!strictJSR107)
				{
					// TCK CHALLENGE
					// JSR107 TCK does not allow incrementing the hit count. CacheMBStatisticsBeanTest.java:616 , testPutIfAbsent()
					// So we only go here in native tcache mode (== !strictJSR107)
					statisticsCalculator.incrementHitCount();
				}
				holder.incrementUseCount();
				effectiveHolder = holder;
			}
		}
		else
		{
			// Add entry initially with unlimited expiration, then update the idle from the existing holder
			newHolder = new AccessTimeObjectHolder<>(data, Constants.EXPIRY_MAX, cacheTime, builder.getCacheWriteMode());
			holder = this.objects.put(key, newHolder);
			newHolder.setMaxIdleTimeAsUpdateOrCreation(holder != null, expiryPolicy, holder);
			effectiveHolder = newHolder;
			hasPut = true;
		}
		
		if (effectiveHolder.isInvalid())
		{
			// Put, but already expired => do not return the value
			effectiveHolder = null;
		}
		else
		{
			if (hasPut)
				statisticsCalculator.incrementPutCount();
		}
		
		ensureCleanerIsRunning();
		return new Holders<V>(newHolder, holder, effectiveHolder);
	}

	/**
	 * TCK-WORK JSR107 check statistics effect
	 * 
	 * {@inheritDoc}
	 */
	public V getAndReplace(K key, V value)
	{
		kvUtil.verifyKeyAndValueNotNull(key, value);
		
		AccessTimeObjectHolder<V> newHolder; // holder that was created via new.
		newHolder = new AccessTimeObjectHolder<V>(value, Constants.EXPIRY_MAX, cacheTimeSpread(), builder.getCacheWriteMode());
		AccessTimeObjectHolder<V> oldHolder = this.objects.replace(key, newHolder);

		if (oldHolder != null)
		{
			// replaced
			V oldValue = oldHolder.peek();
			newHolder.updateMaxIdleTime(expiryPolicy.getExpiryForUpdate()); // OK
			return oldValue;

		}
		else
		{
			return null;
		}
	}
	
	public ChangeStatus replace(K key, V oldValue, V newValue)
	{
		AccessTimeObjectHolder<V> newHolder; // holder that was created via new.
		AccessTimeObjectHolder<V> oldHolder; // holder for the object in the Cache
		oldHolder = objects.get(key);
		if (oldHolder == null)
			return ChangeStatus.UNCHANGED; // Not in backing store => cannot replace
		if (! oldHolder.peek().equals(oldValue))
			return ChangeStatus.CAS_FAILED_EQUALS; // oldValue does not match => do not replace
		
		newHolder = new AccessTimeObjectHolder<V>(newValue, Constants.EXPIRY_MAX, cacheTimeSpread(), builder.getCacheWriteMode());
		boolean replaced = this.objects.replace(key, oldHolder, newHolder);
		if (replaced)
			newHolder.updateMaxIdleTime(expiryPolicy.getExpiryForUpdate());
		else
			oldHolder.updateMaxIdleTime(expiryPolicy.getExpiryForAccess());

		return replaced ? ChangeStatus.CHANGED : ChangeStatus.UNCHANGED;
		
	}
	
	/**
	 * Returns whether there is capacity for at least one more element. The default implementation always returns true.
	 * Derived classes that implement a Cache limit (LFU, LRU, ...) can either create free room or
	 * return false if the Cache is full.
	 * 
	 * @return true, if there is capacity left
	 */
	protected boolean ensureFreeCapacity()
	{
		return true;
	}

	/**
	 * Sets the cleanup interval for expiring idle cache entries. If you do not call this method, the default
	 * cleanup interval is used, which is 1/10 * idleTime. This is often a good value.
	 * 
	 * TODO This should go into the Builder
	 * 
	 * @param cleanUpIntervalMillis The eviction cleanup interval in milliseconds
	 */
	public void setCleanUpIntervalMillis(long cleanUpIntervalMillis)
	{
		this.cleanUpIntervalMillis = cleanUpIntervalMillis;
	}

	/**
	 * Checks whether the cleaner is running. If not, the cleaner gets started.
	 */
	private void ensureCleanerIsRunning() 
	{
		if(cleaner == null)
		{
			startCleaner();
		}
	}
	
	/**
	 * Gets cached object for the given key, or null if this Cache contains no mapping for the key.
	 * A return value of null does not necessarily indicate that the map contains no mapping
	 * for the key; it's also possible that the map explicitly maps the key to null.
	 * There is no operation to distinguish these two cases.
	 * 
	 * Throws NullPointerException if pKey is null.
	 * 
	 * @param key The key
	 * @return The value
	 * @throws RuntimeException if key is not present and the loader threw an Exception.
	 * @throws NullPointerException if key is null.
	 */
	public V get(K key) throws RuntimeException
	{
		AccessTimeObjectHolder<V> holder = getFromMap(key);
		return holder == null ? null : holder.get();
	}
	
	AccessTimeObjectHolder<V> getFromMap(K key) throws RuntimeException
	{
		throwISEwhenClosed();
		kvUtil.verifyKeyNotNull(key);

		AccessTimeObjectHolder<V> holder = this.objects.get(key);

		boolean loaded = false;
		boolean holderWasValidBeforeApplyingExpiryPolicy = false;
		if (holder != null)
		{
			holderWasValidBeforeApplyingExpiryPolicy = !holder.isInvalid();
			holder.updateMaxIdleTime(expiryPolicy.getExpiryForAccess());
		}
		
//		boolean holderIsInvalid = holder == null || holder.isInvalid();
		
		if (!holderWasValidBeforeApplyingExpiryPolicy && builder.isReadThrough())
		{
			// Data not present, but can be loaded
			try
			{
				// loader is never null here, as isReadThrough enforced that when the Cache was created
				V loadedValue = loader.load(key);
				if (loadedValue == null)
				{
					// JSR107 TCK requires that a loader will not fail with NPE, even though the value is null.
					return null;
				}

				holder = putToMap(key, loadedValue, expiryPolicy.getExpiryForCreation(), cacheTimeSpread(), false, true);
				loaded = true;
				// ##LOADED_MISS_COUNT##
				statisticsCalculator.incrementMissCount(); // needed to load => increment miss count
			}
			catch (Exception exc)
			{
				// Wrap loader Exceptions in CacheLoaderExcpeption. The TCK requires it, but it is possibly a TCK bug.  
				
				// TODO Check back after clarifying whether this requirement is a TCK bug:
				// https://github.com/jsr107/jsr107tck/issues/99
				
				String message = "CacheLoader " + id + " failed to load key=" + key;
				throw new CacheLoaderException(message + " This is a wrapped exception. See https://github.com/jsr107/jsr107tck/issues/99", exc);
			}

		}
		
		if (holder == null)
		{
			// debugLogger.debug("1lCache GET key:"+pKey.hashCode()+"; CACHE:null");
			if (loaded)
			{
				// already counted  at ##LOADED_MISS_COUNT## => do nothing
			}
			else
			{
				statisticsCalculator.incrementMissCount();
			}
			return null;
		}

		if (!holderWasValidBeforeApplyingExpiryPolicy && holder.isInvalid())
		{
			// Holder was neither valid before we applied the ExpirationPolicy, nor after (after = Updated or New Holder)

			// debugLogger.debug("1lCache GET key:"+pKey.hashCode()+"; CACHE:invalid");
			// Hint: We do not remove the value here, as it will be done in the asynchronous thread anyways.
			statisticsCalculator.incrementMissCount();
			return null;
		}
		// debugLogger.debug("1lCache GET key:"+pKey.hashCode()+"; CACHE:hit");
		holder.incrementUseCount();
		statisticsCalculator.incrementHitCount();
		return holder;
	}



	/**
	 * Fills the given cache statistics object.
	 * 
	 * @param cacheStatistic The statistics object to fill
	 * @return The CacheStatistic object
	 */
	protected TCacheStatisticsInterface fillCacheStatistics(TCacheStatisticsInterface cacheStatistic)
	{
		cacheStatistic.setHitCount(statisticsCalculator.getHitCount());
		cacheStatistic.setMissCount(statisticsCalculator.getMissCount());
		cacheStatistic.setHitRatio(getCacheHitrate());
		cacheStatistic.setElementCount(objects.size());
		cacheStatistic.setPutCount(statisticsCalculator.getPutCount());
		cacheStatistic.setRemoveCount(statisticsCalculator.getRemoveCount());
		cacheStatistic.setDropCount(statisticsCalculator.getDropCount());
		return cacheStatistic;
	}

	public TCacheStatistics statistics()
	{
		TCacheStatistics stats = new TCacheStatistics(this.id());
		fillCacheStatistics(stats);
		return stats;
	}

	
	final Object hitRateLock = new Object();
	final static long CACHE_HITRATE_MAX_VALIDITY_MILLIS = 1*60*1000; // 1 Minute

	private long cacheHitRatePreviousTimeMillis = System.currentTimeMillis();
	private boolean managementEnabled = false;
//	@ObjectSizeCalculatorIgnore
	static TimeSource millisEstimator = null; 
	final static Object millisEstimatorLock = new Object(); 

	/**
	 * Returns the Cache hit rate. The returned value is the average of the last n measurements (2012-10-16: n=5).
	 * Implementation note: This method should be called in regular intervals, because it also updates
	 * its "hit rate statistics array".
	 * 
	 * Future direction: This method overlaps functionality, which is now provided by SlidingWindowCounter.
	 * Thus migrate the hit rate to use SlidingWindowCounter.
	 * 
	 * @return The Cache hit rate in percent (0-100)
	 */
	public float getCacheHitrate()
	{
		// -1- Calculate difference between previous values and current values
		long now = System.currentTimeMillis(); // -<- Keep currentTimeMillis() out of synchronized block
		synchronized(hitRateLock)
		{
			// Synchronizing here, to consistently copy both "Current" values to "Previous"
			// It is not synchronized with the incrementAndGet() in the rest of the code,
			// as it is not necessary (remember that those are atomical increments!). 

			if (now > cacheHitRatePreviousTimeMillis + CACHE_HITRATE_MAX_VALIDITY_MILLIS )
			{
				cacheHitRatePreviousTimeMillis = now;
				// Long enough time has passed => calculate new sample
				HitAndMissDifference stats = statisticsCalculator.tick();
				
				// -3- Add the new value to the floating array hitrateLastMeasurements
				long cacheGets = stats.getHits() + stats.getMisses();
				float hitRate = cacheGets == 0 ? 0f :  (float)stats.getHits() / (float)cacheGets * 100f;
				hitrateLastMeasurements[hitrateLastMeasurementsCurrentIndex] = hitRate;
				hitrateLastMeasurementsCurrentIndex =
						(hitrateLastMeasurementsCurrentIndex + 1) % hitrateLastMeasurements.length;
			}
		}

		return calculateAverageHitrate();
	}

	private float calculateAverageHitrate()
	{
		// Hint: There is no need to synchronize on hitrateLastMeasurements, as we always read consistent state.
		float averageHitrate = 0;
		for (int i=0; i<hitrateLastMeasurements.length; ++i)
		{
			averageHitrate += hitrateLastMeasurements[i];
		}
		averageHitrate /= hitrateLastMeasurements.length;
		return averageHitrate;
	}
	
	/**
	 * Removes all entries from the Cache without notifying Listeners
	 */
	public void clear()
	{
		stopAndClear(0);
	}
	
	protected String stopAndClear(long millis)
	{
		String errorMsg = stopCleaner(millis);
		this.objects.clear();
		return errorMsg;
	}
	
	private synchronized void  startCleaner()
	{
		if(this.cleaner != null)
		{
			return;
		}
		
		this.cleaner = new CleanupThread("CacheCleanupThread-" + id);
	    this.cleaner.setPriority(Thread.MAX_PRIORITY);
	    this.cleaner.setDaemon(true);
	    this.cleaner.setUncaughtExceptionHandler(this);
	    this.cleaner.start();
	    
	    // Don't call expiryPolicy methods in JSR107 mode. The JSR107 TCK gets confused about it, and considers it as a test failure.
	    String durationMsg = strictJSR107 ? "" :  ", timeout: " + expiryPolicy.getExpiryForAccess(); 
	    logger.info(this.id + " Cache started" + durationMsg);
	}


	/**
	 * Returns the TimeSource for this Cache. The TimeSource is used for any situation where the current time is required, for
	 * example the input date for a cache entry or on getting the current time when doing expiration.   
	 * 
	 * Instantiates the TimeSource if it is not yet there.
	 * 
	 * @return The TimeSource for this Cache.
	 */
	protected TimeSource activateTimeSource()
	{
		synchronized (millisEstimatorLock)
		{			
		    if (millisEstimator == null)
		    {
		    	millisEstimator  = new EstimatorTimeSource(new SystemTimeSource(), 10, logger);
		    }
		}
		
		return millisEstimator;
	}
	
	private void stopCleaner()
	{
		stopCleaner(0);
	}
	
	private synchronized String stopCleaner(long millis)
	{
		String errorMsg = null;
		Cache<K, V>.CleanupThread cleanerRef = cleaner;
		if ( cleanerRef != null )
		{
			cleanerRef.cancel();
			if (millis > 0)
			{
				if (! joinSimple(cleanerRef, millis, 0) )
				{
					errorMsg = "Shutting down Cleaner Thread FAILED";
				}
			}
		}
		
		cleaner = null;

		return errorMsg;
	}
	
	
	/**
	 * This is called, should the CleanupThread should go down on an unexpected (uncaught) Exception.
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable throwable) 
	{
		logger.error("CleanupThread Thread " + thread + " died because uncatched Exception",  throwable );
	   
		// We must make sure that the cleaner will be recreated on the next put(), thus "cleaner" is set to null.
		cleaner = null;
	}

	private void cleanUp()
	{
		boolean expiryNotification = listeners.hasListenerFor(EventType.EXPIRED);
		Map<K,V> evictedElements = expiryNotification ? new HashMap<K,V>() : null;
		
		// -1- Clean
		int removedEntries = 0;

	    for (Iterator<Entry<K, AccessTimeObjectHolder<V>>> iter = this.objects.entrySet().iterator(); iter.hasNext(); )
	    {
	    	Entry<K,AccessTimeObjectHolder<V>> entry = iter.next();
	    	AccessTimeObjectHolder<V> holder = entry.getValue();
	    	
	    	if (holder.isInvalid())
	    	{
	    		iter.remove();
	    		V value = holder.peek();
	    		boolean removed = holder.release();
				if (removed) // SAE-150 Verify removal
				{
					++removedEntries;
					if (evictedElements != null)
						evictedElements.put(entry.getKey(), value);
				}
	    	}
	    }
	    
	    // -2- Notify listeners
		if (evictedElements != null)
			listeners.dispatchEvents(evictedElements, EventType.EXPIRED, true);


		// -3- Stop Thread if cache is empty
	    if(objects.isEmpty())
	    {
	    	stopCleaner();
	    }
	    
	    if (removedEntries != 0)
	    {
	    	logger.info(this.id + " Cache has expired objects from Cache, count=" + removedEntries);
	    }
	}


	/**
	 * @return count of cached objects
	 */
	public int size()
	{
		return this.objects.size();
	}
	
	/**
	 * Removes the object with given key, if stored in the Cache. Returns whether it was actually removed.
	 * 
	 * @param key The key
	 * @param value The value
	 * @return The value that was stored for the given key or null 
	 */
	public boolean remove(K key, V value)
	{
		kvUtil.verifyKeyAndValueNotNull(key, value);
		
		AccessTimeObjectHolder<V> holder = objects.get(key);
		if (holder == null)
			return false;

		// WORK TCK JSR107 This implementation is not yet checked for for operating ATOMICALLY.    
		V holderValue = holder.peek();
		if (!holderValue.equals(value))
			return false;

		boolean removed = this.objects.remove(key, holder);
		if (removed)
		{
			releaseHolder(holder);
		}
		return removed;
	}

	/**
	 * Removes the object with given key, and return the value that was stored for it.
	 * Returns null if there was no value for the key stored.
	 * 
	 * @param key The key
	 * @return The value that was stored for the given key or null 
	 */
	public V remove(K key)
	{
		kvUtil.verifyKeyNotNull(key);

		AccessTimeObjectHolder<V> holder = this.objects.remove(key);
		return releaseHolder(holder);
	}

	/**
	 * Schedule the object for the given key for expiration. The time will be chosen randomly
	 * between immediately and the given maximum delay. The chosen time will never increase
	 * the natural expiration time of the object.
	 * <p>
	 * This method is especially useful if many objects are to be expired, and fetching data is an expensive operation.
	 * As each call to this method will chose a different expiration time, expiration and thus possible re-fetching
	 * will spread over a longer time, and helps to avoid resource overload (like DB, REST Service, ...).
	 *     
	 * @param key The key for the object to expire
	 * @param maxDelay The maximum delay time until the object will be expired
	 * @param timeUnit The time unit for maxDelay
	 */
	public void expireUntil(K key, int maxDelay, TimeUnit timeUnit)
	{
		AccessTimeObjectHolder<V> holder = this.objects.get(key);
		if (holder == null)
		{
			return;
		}
		
		holder.setExpireUntil(maxDelay, timeUnit, random);
	}

	
	/**
	 * Frees the data in the holder, and returns the value stored by the holder.
	 *
	 * @param holder The holder to release
	 * @return The value stored by the holder
	 */
	protected V releaseHolder(AccessTimeObjectHolder<V> holder)
	{
		if(holder == null)
		{
			return null;
		}
		
		V oldData = holder.peek();

		// Return the old data value, if we manage to release() the holder. We usually are able to
		// release, except if another thread is calling release() in parallel and has won the race.
		boolean released =  holder.release();
		if (released)
		{
			// SAE-150 Return oldData, if it the current call released it. This is the regular case.
			return oldData;
		}
		else
		{
			// SAE-150 Some other Thread has also called holder.release() concurrently and won the race.
			// => The holder was already invalid. Return null, as there can be only one Thread actually
			//    releasing the holder. Hint: This can happen with any 2 or more Threads, but the most likely
			//    Thread that do a race are the Eviciton Thread and the Expiration Thread.
			return null;
		}
	}
	
	/**
	 * Retrieve reference to AccessTimeHolder&lt;V&gt; objects as an unmodifiable Collection.
	 * You can use this when you want to serialize the complete Cache. 
	 *
	 * @return Collection of all AccessTimeHolder&lt;V&gt; Objects. The collection is unmodifiable.
	 */
	public Collection<AccessTimeObjectHolder<V>> getAccessTimeHolderObjects()
	{
		return Collections.unmodifiableCollection(objects.values());
	}
	
	/**
	 * Returns a thread-safe unmodifiable collection of the keys.
	 * 
	 * @return The keys as a Collection
	 */
	public Collection<K> keySet()
	{
		// The backing map is a ConcurrentMap, so there should not be any ConcurrentModificationException.
		return Collections.unmodifiableCollection(objects.keySet());
	}


	/**
	 * Returns true if this Cache contains a mapping for the specified key.
	 * 
	 * @see java.util.concurrent.ConcurrentMap#containsKey(Object)
	 * 
	 * @param key The key
	 * @return true if this Cache contains a mapping for the specified key
	 */
	public boolean containsKey(K key)
	{
		kvUtil.verifyKeyNotNull(key);
		// We cannot rely on objects.containsKey(), as the entry may be expired.
		 AccessTimeObjectHolder<V> value = objects.get(key);
		 return ! ( value == null || value.isInvalid());
	}


	/**
	 * Sets the logger that will be used for all Cache instances.
	 * Changing the logger during runtime has immediate effect.
	 * 
	 * @param logger The logger to use
	 */
	public static void setLogger(TriavaLogger logger)
	{
		Cache.logger = logger;
	}
	
	/**
	 * Measures the number of elements and the size of this Cache in bytes and logs it.
	 * The number of elements is logged twice. Once before and once after the size measurement. This will help to
	 * see how much the content has changed during the measurement.
	 *  
	 * @param objectSizeCalculator The implementation to use
	 * @return The size information of this Cache
	 */
	public CacheSizeInfo reportSize(ObjectSizeCalculatorInterface objectSizeCalculator)
	{
		int elemsBefore = objects.size();
		long sizeInByte = objectSizeCalculator.calculateObjectSizeDeep(objects);
		int elemsAfter = objects.size();
		CacheSizeInfo cacheSizeInfo = new CacheSizeInfo(id, elemsBefore, sizeInByte, elemsAfter);
		
		logger.info(cacheSizeInfo.toString());
		
		return cacheSizeInfo;
	}


	/**
	 * Returns a JSR107 compliant view on this Cache. The returned instance is a view of the native tCache,
	 * so any operation on it or this will be visible by the other instance. 
	 * 
	 * @return A JSR107 compliant view on this Cache 
	 */
	public TCacheJSR107<K, V> jsr107cache()
	{
		return tCacheJSR107;
	}


	/**
	 * Enables or disables statistics. If enabled, the statistics are available both via {@link #statistics()} and also
	 * by JSR107 compliant MXBeans. Disabling statistics will discard all statistics.
	 *  
	 * @param enable true for enabling statistics.
	 */
	public void enableStatistics(boolean enable)
	{
		boolean currentlyEnabled = isStatisticsEnabled();
		if (enable)
		{
			if (currentlyEnabled)
			{
				return; // No change => Do not create new statisticsCalculator as it would discard the old statistics.
			}
			else
			{
				statisticsCalculator = new StandardStatisticsCalculator();
				TCacheStatisticsMBean.instance().register(this);
				jsr107cache().refreshActionRunners(); // Action runner must use the new statistics 
			}
		}
		else
		{
			statisticsCalculator = new NullStatisticsCalculator();
			TCacheStatisticsMBean.instance().unregister(this);
			jsr107cache().refreshActionRunners(); // Action runner should stop updating statistics 
		}
		
	}


	public void enableManagement(boolean enable)
	{
		if (enable)
		{
			TCacheConfigurationMBean.instance().register(this);
		}
		else
		{
			TCacheConfigurationMBean.instance().unregister(this);
		}
		managementEnabled = enable;
	}

	public boolean isStatisticsEnabled()
	{
		boolean currentlyEnabled = statisticsCalculator != null && !(statisticsCalculator instanceof NullStatisticsCalculator);
		return currentlyEnabled;
	}


	public boolean isManagementEnabled()
	{
		return managementEnabled;
	}


	/**
	 * Returns normally with no side effects if this cache is open. Throws IllegalStateException if it is closed.
	 */
	private void throwISEwhenClosed()
	{
		if (isClosed())
			throw new IllegalStateException("Cache already closed: " + id());
	}


	// TODO Make this package-private / refactor
	public CacheWriter<K, V> cacheWriter()
	{
		return cacheWriter;
	}


	// TODO Make this package-private / refactor
	public ListenerCollection<K, V> listeners()
	{
		return listeners;
	}


	// TODO Make this package-private / refactor
	public StatisticsCalculator statisticsCalculator()
	{
		return statisticsCalculator;
	}

	/**
	 * Thread that removes expired entries.
	 */
	public class CleanupThread extends Thread
	{
		private volatile boolean running;  // Must be volatile, as it is modified via cancel() from a different thread
		private int failedCounter = 0;
		
		CleanupThread(String name)
		{
			super(name);
		}
		
		public void run()
		{
			logger.info("CleanupThread " + this.getName() + " has entered run()");
			this.running = true;
			while (running)
			{
				try
				{
					// About stopping: If interruption takes place between the lines above (while running)
					// and the line below (sleep), the interruption gets lost and a full sleep will be done.
					// TODO If that sleep is long (like 30s), and the shutdown Thread waits until this Thread is stopped,
					//   the shutdown Thread will wait very long.
					sleep(cleanUpIntervalMillis);
					this.failedCounter = 0;
					cleanUp();
					if (Thread.interrupted())
					{
						throw new InterruptedException();
					}
				}
				catch (InterruptedException ex)
				{
					if(this.running)
					{
						this.failedCounter++;
						if(this.failedCounter > 10)
						{
							logger.error("possible endless loop detected, stopping loop");
							stopCleaner();
						}
						logger.error("interrupted in run loop, restarting loop", ex);
					}
				}
			}
			logger.info("CleanupThread " + this.getName() + " is leaving run()");
		}
		
		/**
		 * Interrupts the {@link CleanupThread} and marks it for stopping
		 */
		public void cancel()
		{
			this.running = false;
			this.interrupt();
		}
	
	}

}
