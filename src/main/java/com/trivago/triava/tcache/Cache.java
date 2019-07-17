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

package com.trivago.triava.tcache;

import com.trivago.triava.logging.TriavaLogger;
import com.trivago.triava.logging.TriavaNullLogger;
import com.trivago.triava.tcache.action.ActionContext;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.CacheMetadataInterface;
import com.trivago.triava.tcache.core.CacheWriterWrapper;
import com.trivago.triava.tcache.core.Holders;
import com.trivago.triava.tcache.core.NopCacheWriter;
import com.trivago.triava.tcache.core.StorageBackend;
import com.trivago.triava.tcache.core.TCacheHolderIterator;
import com.trivago.triava.tcache.core.TriavaCacheConfiguration;
import com.trivago.triava.tcache.event.ListenerCollection;
import com.trivago.triava.tcache.expiry.Constants;
import com.trivago.triava.tcache.expiry.TCacheExpiryPolicy;
import com.trivago.triava.tcache.expiry.TouchedExpiryPolicy;
import com.trivago.triava.tcache.expiry.UntouchedExpiryPolicy;
import com.trivago.triava.tcache.statistics.HitAndMissDifference;
import com.trivago.triava.tcache.statistics.LongAdderStatisticsCalculator;
import com.trivago.triava.tcache.statistics.NullStatisticsCalculator;
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
import com.trivago.triava.tcache.weigher.DefaultWeightLimiter;
import com.trivago.triava.tcache.weigher.Weigher;
import com.trivago.triava.tcache.weigher.Weight1;
import com.trivago.triava.tcache.weigher.WeightLimiter;
import com.trivago.triava.time.EstimatorTimeSource;
import com.trivago.triava.time.SystemTimeSource;
import com.trivago.triava.time.TimeSource;

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


/**
 * A Cache that supports expiration based on expiration time and idle time.
 * The Cache can be of unbounded or bounded size. In the latter case, eviction is done using the selected strategy, for example
 * LFU, LRU, Clock or an own implemented custom eviction strategy (key and value type aware). You can retrieve either instance by using
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
public class Cache<K, V> implements Thread.UncaughtExceptionHandler, ActionContext<K, V>, CacheMetadataInterface
{
    static long CLEANUP_LOG_INTERVAL = TimeUnit.SECONDS.toMillis(60);
    static TriavaLogger logger = new TriavaNullLogger();
	
	private final TCacheFactory factory;

	final private String id;
	final Builder<K,V> builder; // A reference to the Builder that created this Cache
	final private boolean strictJSR107;
	final long baseTimeMillis;
	
	final TCacheExpiryPolicy expiryPolicy;
	
	private final long maxCacheTime; // max cache time in milliseconds
	private final int maxCacheTimeSpread; // max cache time spread in SECONDS
	
	
	final protected ConcurrentMap<K,AccessTimeObjectHolder<V>> objects;
	final Random random = new Random(System.currentTimeMillis());
	
//	@ObjectSizeCalculatorIgnore
	private volatile transient CleanupThread cleaner = null;
	// The expiration queue is for the cleaner, but it is independent from the cleaner instance
	private volatile long cleanUpIntervalMillis;

	//final HashInterner<V> interner = new HashInterner<>(100);


    protected final WeightLimiter<V> weightLimiter;
    protected final Weigher<V> weigher;

	/**
	 * Cache hit counter.
	 * We do not expect any overruns, as we use "long" to count hits and misses.
	 * If we would do 10 Cache GET's per second, this would mean 864.000 GET's per day and 6.048.000 per Week.
	 * Even counting with "int" would be enough (2.147.483.647), as we would get no overrun within roughly 35 weeks.
	 * If we have 10 times the load this would still be enough for 3,5 weeks with int, and "infinitely" more with long.
	 */
	StatisticsCalculator statisticsCalculator = null;

	private final float[] hitrateLastMeasurements = new float[5];
	int hitrateLastMeasurementsCurrentIndex = 0;
	private volatile boolean shuttingDown = false;

	protected final JamPolicy jamPolicy;
	protected final CacheLoader<K, V> loader;

	final ListenerCollection<K,V> listeners;

	final TCacheJSR107<K, V> tCacheJSR107;
	final CacheWriter<K, V> cacheWriter;
	
	final KeyValueUtil<K,V> kvUtil;

	/**
	 * Construct a Cache, using the given configuration from the Builder.
	 * @param builder The builder containing the configuration
	 * @param factory The factory, in which this Cache will be registered.
	 */
	public Cache(TCacheFactory factory, Builder<K,V> builder)
	{
		this.factory = factory;
		this.id = builder.getId();
		this.strictJSR107 = builder.isStrictJSR107();
		this.kvUtil = new KeyValueUtil<K,V>(id);
		this.builder = builder;
		tCacheJSR107 = new TCacheJSR107<K, V>(this);

		// Expiration
		this.maxCacheTime = builder.getMaxCacheTime();
		
		int spreadSeconds = (int)Math.min(Integer.MAX_VALUE, builder.getMaxCacheTimeSpread()/1000); // Spread is in seconds for technical reasons
		this.maxCacheTimeSpread = spreadSeconds;

		long expiryIdleMillis = builder.getCleanUpIntervalMillis();
		ExpiryPolicy epFromFactory = builder.getExpiryPolicyFactory().create();
		
		if (strictJSR107)
		{
			this.expiryPolicy = new TouchedExpiryPolicy(epFromFactory);
			// There is no suitable way to calculate expiryIdleMillis for strict JSR107 mode.
			//  Issue:The JSR107 TCK disallows free use of expiryPolicy methods. When using it here, the TCK will record failures,
			// because it requires that methods are ONLY called at very specific times.
			// => Workaround: For now, we just do a guess of 1 minute 
			if (expiryIdleMillis == 0)
			{
				// Auto-tuning expiryIdleMillis
				expiryIdleMillis = 600_000; // 10 minutes
			}
		}
		else
		{
			this.expiryPolicy = new UntouchedExpiryPolicy(epFromFactory);
			if (expiryIdleMillis == 0)
			{
				// Auto-tuning expiryIdleMillis
				Duration expiryDuration = epFromFactory.getExpiryForAccess();
				if (expiryDuration == null)
					expiryDuration = epFromFactory.getExpiryForCreation();
				expiryIdleMillis = expiryDuration.getAdjustedTime(0);
			}
		}
		
		// Cleaner should run often enough, but not too often. The chosen time is expiryIdleMillis / 10, but limited to 5min.
		if (expiryIdleMillis <= 0)
		{
			expiryIdleMillis = 600_000;
		}
		if (expiryIdleMillis > 10)
			this.cleanUpIntervalMillis = Math.min(5 * 60_000, expiryIdleMillis / 10);
		else
			this.cleanUpIntervalMillis = 1;

		this.jamPolicy = builder.getJamPolicy();
		// For weighing the values: Use the user provided Weigher, or apply a constant weight of 1.
        final Weigher<V> bWeigher = builder.getWeigher();
        this.weigher = bWeigher != null ? bWeigher : new Weight1();
		// CacheLoader directly or via CacheLoaderFactory
		Factory<javax.cache.integration.CacheLoader<K, V>> lf = builder.getCacheLoaderFactory();
		if (lf != null)
		{
			this.loader = lf.create();
		}
		else
		{
			this.loader = builder.getLoader();
		}
		if (this.loader == null && builder.isReadThrough())
		{
			throw new IllegalArgumentException("Builder has isReadThrough, but has no loader for cache: " + id);
		}

		Factory<CacheWriter<? super K, ? super V>> cwFactory = builder.getCacheWriterFactory();
		if (cwFactory == null)
		{
			this.cacheWriter = new NopCacheWriter<K, V>();
		}
		else
		{
			CacheWriter<? super K, ? super V> cw = cwFactory.create();
			CacheWriterWrapper<K, V> cwWrapper = new CacheWriterWrapper<K, V>(cw, false);
			this.cacheWriter = cwWrapper;
		}

        this.weightLimiter = new DefaultWeightLimiter(builder, weigher);

		objects = createBackingMap(builder, weightLimiter);

		enableStatistics(builder.getStatistics());
		enableManagement(builder.isManagementEnabled());

		millisEstimator = activateTimeSource(builder.getTimeSource());
        baseTimeMillis = millisEstimator.millis();

		listeners = new ListenerCollection<>(this, builder);

		tCacheJSR107.refreshActionRunners();
		// Hint: It sounds more natural to call registerCache() within TCacheFactory.createCache(). Doing it here has the advantage to be able
		//       to be compatible with construction via Builder.build() which is the native Triava Cache construction code.
		factory.registerCache(this);

		logger.info(this.toString());
	}

	@SuppressWarnings("unchecked") // Avoid warning for the "generics cast" in the last line
	private ConcurrentMap<K, AccessTimeObjectHolder<V>> createBackingMap(Builder<K, V> builder, WeightLimiter weightLimiter)
	{
		StorageBackend<K, V> storageFactory = builder.storageFactory();

		ConcurrentMap<K, ? extends TCacheHolder<V>> map = storageFactory.createMap(builder, weightLimiter.evictionBoundaries());

		CacheWriteMode cacheWriteMode = builder.getCacheWriteMode();
		if (cacheWriteMode.isStoreByValue())
		{
			ConcurrentMap<ByteArray, AccessTimeObjectHolder<V>> castedMap = (ConcurrentMap<ByteArray, AccessTimeObjectHolder<V>>) map;
			return new ConcurrentKeyDeserMap<K, AccessTimeObjectHolder<V>>(castedMap, cacheWriteMode);

		}
		else
		{
			ConcurrentMap<K, AccessTimeObjectHolder<V>> castedMap = (ConcurrentMap<K, AccessTimeObjectHolder<V>>) map;
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

	public TriavaCacheConfiguration<K, V, ? extends Builder<K,V>> configuration()
	{
		return builder;
	}

	/**
	 * Closes the cache and removes it from the associated CacheManager. After calling this method, the cache cannot be
	 * used any longer.
	 */
	public final void close()
	{
		close0(true);
	}
	
	/**
	 * Identical with {@link #close()}, but allows to specify whether to call CacheManager.destroyCache().
	 * <p>
	 * Using destroyCache==false is required to avoid infinite recursion, when close is called from the CacheManager.
	 * It looks unclean, but we cannot move the close0() code easily, as shutdownCustomImpl() must be called and
	 * calling that from CacheManager.destroyCache() also looks unclean.
	 * In the future this would be a good point to refactor.
	 * 
	 * @param destroyCache true means to call destroyCache()
	 */
	final void close0(boolean destroyCache)
	{
		shuttingDown = true;
		shutdownCustomImpl();
		shutdownPrivate();
		if (destroyCache)
			getFactory().destroyCache(id);
	}

	/**
	 * Shuts down the implementation specific parts. This method is called as part of {@link #close()}.
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
		if (millisEstimator != millisEstimatorStatic) {
		    // shutdown the TimeSource, if it is not the shared/global TimeSource instance
		    millisEstimator.shutdown();
        }
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
			
			boolean timeout = remainingMillis < 0 || (remainingMillis == 0 && nanos == 0);
//			logger.info("joinSimple() checks rtm=" + remainingMillis + ", timeout=" + timeout + " : thread=" + thread);
			if (timeout)
			{
				break;
			}
			try
			{
				thread.join(remainingMillis, nanos);
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
		return stopped;
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

	/**
	 * Add an object to the cache under the given key, using the default idle time and default cache time.
	 * Returns the previous value for the key, or null if no previous entry was present.
	 *
	 * @param key The key
	 * @param value The value
	 * @return The previous value
	 */
	public V getAndPut(K key, V value)
	{
		Holders<V> holders = putToMapI(key, value, cacheTimeSpread(), false);
		AccessTimeObjectHolder<V> oldHolder = holders != null ? holders.oldHolder : null;
		return oldHolder != null ? oldHolder.peek() : null;
	}

	Random randomCacheTime = new Random(System.currentTimeMillis());

	/**
	 * Returns a pseudorandom, uniformly distributed cache time in milliseconds between {@link #maxCacheTime} and
	 * {@link #maxCacheTime} + {@link #maxCacheTimeSpread} -1.
	 * 
	 * @return The cache time
	 */
	long cacheTimeSpread()
	{
		final long spread;
		if (maxCacheTimeSpread == 0)
			spread = maxCacheTime;
		else
		{
			// There is no randomCacheTime.nextLong(int bound) method => Treat Spread as seconds
			spread = maxCacheTime + 1000*randomCacheTime.nextInt(maxCacheTimeSpread);
		}

		return spread;
	}

	/**
	 * Add an object to the cache under the given key with the given idle and cache times.
	 * @param key The key
	 * @param value The value
	 * @param idleTime The idle time in seconds
	 * @param cacheTime The cache time in seconds
	 * @param timeUnit The TimeUnit for both idleTime and cacheTime
	 */
	public void put(K key, V value, int idleTime, int cacheTime, TimeUnit timeUnit)
	{
		putToMap(key, value, timeUnit.toMillis(idleTime), timeUnit.toMillis(cacheTime), false, false);
	}

	/**
	 * The same like {@link #put(Object, Object, int, int, TimeUnit)}, but uses
	 * {@link java.util.concurrent.ConcurrentMap#putIfAbsent(Object, Object)} to actually write
	 * the data in the backing ConcurrentMap. For the sake of the Cache hit or miss statistics this method
	 * is a treated as a read operation and thus updates the hit or miss counters. Rationale is that the
	 * putIfAbsent() result is usually evaluated by the caller.
	 * 
	 * @param key The key
	 * @param value The value
	 * @param idleTime Maximum idle time in seconds
	 * @param cacheTime Maximum cache time in seconds
	 * @param timeUnit The TimeUnit for both idleTime and cacheTime
	 * @return See {@link ConcurrentHashMap#putIfAbsent(Object, Object)}
	 */
	public V putIfAbsent(K key, V value, int idleTime, int cacheTime, TimeUnit timeUnit)
	{
		AccessTimeObjectHolder<V> holder = putToMap(key, value, timeUnit.toMillis(idleTime), timeUnit.toMillis(cacheTime), true, false);
		return gatedPeek(holder);
	}

	/**
	 * The same like {@link #putIfAbsent(Object, Object, int, int, TimeUnit)}, but uses
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
		return gatedPeek(holder);
	}
	
	/**
	 * The same like {@link #putIfAbsent(Object, Object)}, but returns Holders
	 * 
	 * @param key The key
	 * @param value The value
	 * @return Holders
	 */
	Holders<V> putIfAbsentH(K key, V value)
	{
		// Hint idleTime is passed as Constants.EXPIRY_MAX, but it is not evaluated.
		Holders<V> holders = putToMapI(key, value, cacheTimeSpread(), true);
		return holders;
	}

	/**
	 * Returns the value that the given holder stores.
	 * 
	 * @param holder The holder
	 * @return The value stored by the holder, or null of the holder is not valid.
	 */
	private V gatedPeek(AccessTimeObjectHolder<V> holder)
	{
		if (holder == null)
		{
			return null;
		}
		if (holder.isInvalid())
			return null;
		
		return holder.peek();
	}


	/**
	 * Puts the value wrapped in a AccessTimeObjectHolder in the map and returns it. What exactly is returned depends on the value of returnEffectiveHolder. 
	 * If putIfAbsent is true, the put is done in the fashion of {@link java.util.concurrent.ConcurrentMap#putIfAbsent(Object, Object)}, otherwise
	 * like {@link java.util.Map#put(Object, Object)}.
	 * 
	 * @param key The key
	 * @param data The value
	 * @param idleTime in milliseconds
	 * @param cacheTime  Max Cache time in milliseconds
	 * @param putIfAbsent Defines the behavior when the key is already present. See method documentation. 
	 * @param returnEffectiveHolder If true, the holder object of the object in the Map is returned. If returnEffectiveHolder is false, the returned value is like described in {@link java.util.Map#put(Object, Object)}.
	 * @return The holder reference, as described in the returnEffectiveHolder parameter
	 */
	protected AccessTimeObjectHolder<V> putToMap(K key, V data, long idleTime, long cacheTime, boolean putIfAbsent,
			boolean returnEffectiveHolder)
	{
		Holders<V> holders = putToMapI(key, data, cacheTime, putIfAbsent);
		if (holders == null)
			return null;
		if (holders.effectiveHolder != null)
			holders.effectiveHolder.updateMaxIdleTime(idleTime);
		AccessTimeObjectHolder<V> holderToReturn = returnEffectiveHolder ? holders.effectiveHolder : holders.oldHolder;
		return gatedHolder(holderToReturn);
	}

	protected AccessTimeObjectHolder<V> gatedHolder(AccessTimeObjectHolder<V> holder)
	{
		if (holder == null)
			return null;

		return holder.isInvalid() ? null : holder;
	}

	Holders<V> putToMapI(K key, V data, long cacheTime, boolean putIfAbsent)
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

		// if (idleTime <= 0 && cacheTime <= 0)
		// {
//			logger.error("Adding object to Cache with infinite lifetime: " + key.toString() + " : " + data.toString());
		// }

		AccessTimeObjectHolder<V> oldHolder; // holder returned by objects.put*().
		AccessTimeObjectHolder<V> newHolder; // holder that was created via new.
		AccessTimeObjectHolder<V> effectiveHolder; // holder that is effectively in the Cache

		boolean hasPut = false;

		if (putIfAbsent)
		{
			// Always use expiryForCreation. Either it is correct, or we do not care(wrong but not added to cache) 
			newHolder = new AccessTimeObjectHolder<V>(data, builder.getCacheWriteMode(), this);
			oldHolder = this.objects.putIfAbsent(key, newHolder);
			if (oldHolder != null && oldHolder.isInvalid())
			{
				// Entry was in backing map, but is actually invalid (e.g. expired) => just overwrite
				expireEntry(key,oldHolder); // SAE-190 Notify about expiration
//				enqueueExpirationEvent(key, holder);
				this.objects.put(key, newHolder);
				oldHolder = null;
			}

			/*
			 *	A putIfAbsent() is also treated as a GET operation, so update cache statistics. 
			 *	See putIfAbsent() docs above for a more  detailed explanation.
			 */
			if (oldHolder == null)
			{
                weightLimiter.add(data);
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
				oldHolder.incrementUseCount();
				effectiveHolder = oldHolder;
			}
		}
		else
		{
			// Add entry initially with unlimited expiration, then update the idle from the existing holder
			newHolder = new AccessTimeObjectHolder<>(data, builder.getCacheWriteMode(), this);
			oldHolder = this.objects.put(key, newHolder);
			if (oldHolder != null && oldHolder.isInvalid())
			{
				expireEntry(key,oldHolder); // SAE-190 Notify about expiration
				oldHolder = null;
			}
			long calculatedIdleTime = newHolder.calculateMaxIdleTimeFromUpdateOrCreation(oldHolder != null, expiryPolicy, oldHolder);
			effectiveHolder = newHolder;
			hasPut = true;
            weightLimiter.add(data);
            // We have to complete the entry as late as possible, to make sure it cannot be found in the Cache before it is complete.
			// Additionally, inputDate and lastAccessTime must be as accurate as possible. There was an issue before, as the TCK
			// tests used an ExpiryPolicy-Server that took 20-60 ms to process the ExpiryPolicy. At that point of time Elements with 20ms expiration
			// were already expired. The TCK is correct. Test in the TCK is org.jsr107.tck.expiry.CacheExpiryTest.testCacheStatisticsRemoveAll()
			newHolder.complete(calculatedIdleTime, cacheTime); 
		}

		AccessTimeObjectHolder<V> gatedEffectiveHolder = gatedHolder(effectiveHolder);
		if (gatedEffectiveHolder != null)
		{
			if (hasPut)
				statisticsCalculator.incrementPutCount();
		}

		ensureCleanerIsRunning();
		return new Holders<V>(gatedHolder(newHolder), gatedHolder(oldHolder), gatedEffectiveHolder);
	}

//	private void enqueueExpirationEvent(K key, AccessTimeObjectHolder<V> holder)
//	{
//		while (true)
//		{
//			Cache<K, V>.CleanupThread cleanerRef = ensureCleanerIsRunning();
//			boolean added = expirationQueue.offer(new SimpleEvictionEvent(key, holder));
//			if (added)
//			{
//				cleanerRef.processExpirationQueue();
//				break;
//			}
//			
//			// else: This is a spinlock. This could be suboptimal. Rework this before enabling it.
//		}
//	}

	/**
	 * Replace the entry stored by key with the given value.
	 * 
	 * @see javax.cache.Cache#getAndReplace(Object, Object)
	 * 
	 * @param key The key
	 * @param value The value to write
	 * @return The old value. null if there was no mapping for the key.
	 */
	public V getAndReplace(K key, V value)
	{
		kvUtil.verifyKeyAndValueNotNull(key, value);

		AccessTimeObjectHolder<V> newHolder; // holder that was created via new.
		newHolder = new AccessTimeObjectHolder<V>(value, Constants.EXPIRY_MAX, cacheTimeSpread(),
            builder.getCacheWriteMode(), this);
		AccessTimeObjectHolder<V> oldHolder = gatedHolder(this.objects.replace(key, newHolder));

		if (oldHolder != null)
		{
			// replaced
            // Hint: Weight limiter: should be applied, as the new value could be more expensive than the old one.
            weightLimiter.add(newHolder.peek());

            V oldValue = oldHolder.peek();
			if (oldValue != null)
			{
				newHolder.updateMaxIdleTime(expiryPolicy.getExpiryForUpdate()); // OK
			}
            // TODO Should we notify a Listener, similar to expireEntry(key,oldHolder);
			releaseHolder(oldHolder);
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

		if (! oldValue.equals(oldHolder.peek()))
		{
			oldHolder.updateMaxIdleTime(expiryPolicy.getExpiryForAccess());
			return ChangeStatus.CAS_FAILED_EQUALS; // oldValue does not match => do not replace
		}
		
		newHolder = new AccessTimeObjectHolder<V>(newValue, Constants.EXPIRY_MAX, cacheTimeSpread(),
            builder.getCacheWriteMode(), this);
		boolean replaced = this.objects.replace(key, oldHolder, newHolder);
		if (replaced) {
            newHolder.updateMaxIdleTime(expiryPolicy.getExpiryForUpdate());
            // Hint: Weight limiter: should be applied, as the new value could be more expensive than the old one.
            weightLimiter.add(newHolder.peek());
            // TODO Should we notify a Listener, similar to expireEntry(key,oldHolder);
            releaseHolder(oldHolder);
        }
		else {
            oldHolder.updateMaxIdleTime(expiryPolicy.getExpiryForAccess());
        }

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
	 * Checks whether the cleaner is running. If not, the cleaner gets started.
	 * @return The CleanupThread
	 */
	private Cache<K, V>.CleanupThread ensureCleanerIsRunning()
	{
		return startCleaner();
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
		AccessTimeObjectHolder<V> holder = getFromMap(key, true);
		return holder == null ? null : holder.get();
	}

	AccessTimeObjectHolder<V> getFromMap(K key) throws RuntimeException
	{
		return getFromMap(key, true);
	}

	AccessTimeObjectHolder<V> getFromMap(K key, boolean touch) throws RuntimeException
	{
		throwISEwhenClosed();
		kvUtil.verifyKeyNotNull(key);

		AccessTimeObjectHolder<V> holder = this.objects.get(key);

		boolean loaded = false;
		boolean holderWasValidBeforeApplyingExpiryPolicy = AccessTimeObjectHolder.isValid(holder);
		if (holderWasValidBeforeApplyingExpiryPolicy && touch)
		{
			holder.updateMaxIdleTime(expiryPolicy.getExpiryForAccess());
		}

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
				// For details, see https://github.com/jsr107/jsr107tck/issues/99
				String message = "CacheLoader " + id + " failed to load key=" + key;
				throw new CacheLoaderException(message + " This is a wrapped exception. See https://github.com/jsr107/jsr107tck/issues/99", exc);
			}

		}

		if (holder == null)
		{
			// debugLogger.debug("1lCache GET key:"+pKey.hashCode()+"; CACHE:null");
			if (!loaded)
			{
				statisticsCalculator.incrementMissCount();
			}
            // else: already counted at ##LOADED_MISS_COUNT## => do nothing
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
	final static long CACHE_HITRATE_MAX_VALIDITY_MILLIS = 1 * 60 * 1000; // 1 Minute

	private long cacheHitRatePreviousTimeMillis = System.currentTimeMillis();
	private boolean managementEnabled = false;
	// @ObjectSizeCalculatorIgnore
	final TimeSource millisEstimator;
    // @ObjectSizeCalculatorIgnore
    static TimeSource millisEstimatorStatic = null;
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
		synchronized (hitRateLock)
		{
			// Synchronizing here, to consistently copy both "Current" values to "Previous"
			// It is not synchronized with the incrementAndGet() in the rest of the code,
			// as it is not necessary (remember that those are atomical increments!).

			if (now > cacheHitRatePreviousTimeMillis + CACHE_HITRATE_MAX_VALIDITY_MILLIS)
			{
				cacheHitRatePreviousTimeMillis = now;
				// Long enough time has passed => calculate new sample
				HitAndMissDifference stats = statisticsCalculator.tick();

				// -3- Add the new value to the floating array hitrateLastMeasurements
				long cacheGets = stats.getHits() + stats.getMisses();
				float hitRate = cacheGets == 0 ? 0f : (float) stats.getHits() / (float) cacheGets * 100f;
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
		for (int i = 0; i < hitrateLastMeasurements.length; ++i)
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

	private Cache<K, V>.CleanupThread startCleaner()
	{
		Cache<K, V>.CleanupThread cleanerRef = this.cleaner;
		if (cleanerRef != null)
		{
		    /**
		     * Fast-path without locks. This no-lock is required because startCleaner() is called on every write.
		     * A lock would thus be a bottleneck in multi-threaded applications. The bottleneck has been confirmed in real-world
		     * scenarios using YourKit, and also via throughput benchmarks.
		     */
			return cleanerRef;
		}

		synchronized (this) {
		    if (cleaner == null) {
        		cleanerRef = new CleanupThread(id);
        		cleanerRef.setPriority(Thread.MAX_PRIORITY);
        		cleanerRef.setDaemon(true);
        		cleanerRef.setUncaughtExceptionHandler(this);
        		this.cleaner = cleanerRef;
                        cleanerRef.start();
		    }
		}

		logger.info(this.id + " expiration started" + ", cleanupInterval=" + cleanUpIntervalMillis + "ms");
		
		return cleanerRef;
	}

	/**
	 * Returns the effective TimeSource for this Cache. The TimeSource is used for any situation where the current time
     * is required, for example the input date for a cache entry or on getting the current time when doing expiration.
	 * 
	 * If #timeSource is null, then the default TimeSource is used. The latter is a 10ms precision time source.
	 * 
	 * @return The TimeSource for this Cache.
     * @param timeSource
	 */
	protected TimeSource activateTimeSource(TimeSource timeSource)
	{
	    if (timeSource != null) {
	        return timeSource;
        }
        synchronized (millisEstimatorLock) {
            if (millisEstimatorStatic == null) {
                millisEstimatorStatic = new EstimatorTimeSource(new SystemTimeSource(), 10, logger);
            }
        }
        return millisEstimatorStatic;
	}

	private void stopCleaner()
	{
		stopCleaner(0);
	}

	private synchronized String stopCleaner(long millis)
	{
		String errorMsg = null;
		Cache<K, V>.CleanupThread cleanerRef = cleaner;
		if (cleanerRef != null)
		{
			cleanerRef.cancel();
			if (millis > 0)
			{
				if (!joinSimple(cleanerRef, millis, 0))
				{
					errorMsg = "Shutting down Cleaner Thread FAILED";
				}
			}
		}

		cleaner = null;

		return errorMsg;
	}

	/**
	 * This is called, if the CleanupThread goes down on an unexpected (uncaught) Exception.
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable throwable)
	{
		logger.error("CleanupThread Thread " + thread + " died because uncatched Exception", throwable);

		// We must make sure that the cleaner will be recreated on the next put(), thus "cleaner" is set to null.
		cleaner = null;
	}

	private int cleanUp()
	{
		boolean expiryNotification = listeners.hasListenerFor(EventType.EXPIRED);
		Map<K, V> evictedElements = expiryNotification ? new HashMap<>() : null;

		// -1- Clean
		int removedEntries = 0;

		for (Iterator<Entry<K, AccessTimeObjectHolder<V>>> iter = this.objects.entrySet().iterator(); iter.hasNext();)
		{
			Entry<K, AccessTimeObjectHolder<V>> entry = iter.next();
			AccessTimeObjectHolder<V> holder = entry.getValue();

			if (holder.isInvalid())
			{
				iter.remove();
				V value = holder.peek();
				boolean removed = releaseHolderBoolean(holder, value);
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
		if (objects.isEmpty())
		{
			stopCleaner();
		}

		return removedEntries;
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

		boolean validBeforeInvalidate = gatedHolder(holder) != null;
		boolean removed = this.objects.remove(key, holder);
		releaseHolder(holder);
		return validBeforeInvalidate ? removed : false;
	}

	/**
	 * Removes the mapping for the given key, and returns the value that was stored for it.
	 * Returns null if there was no value for the key stored.
	 * <p>
	 * Implementation note: The return value reflects the "remove-from-map" outcome, not the "release".
	 * This is to fulfill the JSR Spec and TCK. In the future it should be checked again whether
	 * we can make it reflect the "release" again, as it would be cleaner (i.e.: atomic, more consistent) 
	 * 
	 * @param key The key
	 * @return The value that was stored for the given key or null
	 */
	public V remove(K key)
	{
		kvUtil.verifyKeyNotNull(key);

		AccessTimeObjectHolder<V> oldHolder = this.objects.remove(key);
		AccessTimeObjectHolder<V> gh = gatedHolder(oldHolder);
		boolean validBeforeInvalidate = gh != null;
		V releasedValue = releaseHolder(oldHolder);
//		if (validBeforeInvalidate)
//			System.out.println("oldHolder=" + oldHolder + ", validBeforeInvalidate=" + validBeforeInvalidate + ", releasedValue=" + releasedValue);
		return validBeforeInvalidate ? releasedValue : null;
	}

	
	/**
	 * Removes the mapping for the given key, and releases the associated holder.
	 * This method is thread-safe and has atomic-like behavior: If two threads call this method,
	 * only one will be returned true.   
	 * <p>
	 * Implementation note: The return value reflects the "release" outcome, not the removal from the Map.
	 * This is done, as the "release" operation is a GATE, and it can only be passed once per Holder. 
	 * 
	 * @param key The key
	 * @return true, if this call released the holder for the given key.
	 */
	protected V removeAndRelease(K key)
	{
		AccessTimeObjectHolder<V> oldHolder = this.objects.remove(key);
		return releaseHolder(oldHolder);
	}
	
	/**
	 * Schedule the entry for the given key for expiration. The time will be chosen randomly
	 * between immediately and the given maximum delay. The chosen time will never increase
	 * the natural expiration time of the object.
     * If the given maximum delay is {@code 0}, the entry is set to expire immediately.
	 * <p>
	 * This method is especially useful if many cache entries are to be invalidated, and fetching data is an expensive operation.
	 * As each call to this method will chose a different expiration time, expiration and thus possible re-fetching
	 * will spread over a longer time, and helps to avoid resource overload (like DB, REST Service, ...).
	 * 
	 * @param key The key for the entry to expire
	 * @param maxDelay The maximum delay time until the object will be expired
	 * @param timeUnit The time unit for maxDelay
     *
     * @throws IllegalArgumentException - if the maxDelay value is &lt; 0
	 */
	public void expireUntil(K key, int maxDelay, TimeUnit timeUnit)
	{
		AccessTimeObjectHolder<V> holder = this.objects.get(key);
		if (holder == null)
		{
			return;
		}

		if (maxDelay < 0) {
		    throw new IllegalArgumentException(String.format("maxDelay value must be >= 0. Passed in value was: [%d]", maxDelay));
        }

		holder.setExpireUntil(maxDelay, timeUnit, random);
	}

	/**
	 * Frees the data in the holder, and returns the value stored by the holder.
	 * This method must be called by anyone who makes a holder inaccessible, e.g. if on expiration
	 * or during remove().
	 *
	 * @param holder The holder to release
	 * @return The value stored by the holder
	 */
	V releaseHolder(AccessTimeObjectHolder<V> holder)
	{
		if (holder == null)
		{
			return null;
		}

		V oldData = holder.peek();

		// Return the old data value, if we manage to release() the holder. We usually are able to
		// release, except if another thread is calling release() in parallel and has won the race.
		boolean released = holder.release();
		if (released)
		{
		    weightLimiter.remove(oldData);
			// SAE-150 Return oldData, if it the current call released it. This is the regular case.
			return oldData;
		}
		else
		{
			// SAE-150 Some other Thread has also called holder.release() concurrently and won the race.
			// => The holder was already invalid. Return null, as there can be only one Thread actually
			// releasing the holder. Hint: This can happen with any 2 or more Threads, but the most likely
			// Threads that do a race are the Eviction Thread and the Expiration Thread.
			return null;
		}
	}

    /**
     * This is one of two methods for releasing a holder. This is the fastest implmementation, and you should prefer
     * it whenever possible over calling {@link #releaseHolder(AccessTimeObjectHolder)}}.
     * You may only call this, if holder != null and holder.peek() == value.
     *
     * @param holder The holder to release. Must be non-null
     * @return true if the holder was released by the calling thread. false if the holder was already released.
     */
    boolean releaseHolderBoolean(AccessTimeObjectHolder<V> holder, V value)
    {
        if (holder.release())
        {
            weightLimiter.remove(value);
            return true;
        }

        return false;
    }

	/**
	 * Returns an Iterator which can be used to traverse all entries of the Cache.
	 * The values of the entry are of type TCacheHolder&lt;V&gt;.
	 * <p>
	 * <b> WARNING!!! Up to v0.9.9, the value reflects the actual state and may change
	 * after retrieving. For example accessTime can change if the entry is accessed, and  value.get() may return null if it is expired.
	 * 
	 * This behavior will change with v1.0, and the returned value will we immutable.
	 * </b>
	 *     
	 * @return The Iterator
	 */
	public TCacheHolderIterator<K,V> iterator()
	{
		throwISEwhenClosed();

		return new TCacheHolderIterator<K,V>(this, this.objects, this.expiryPolicy, false);
	}

	/**
	 * Returns an Iterator which can be used to traverse all entries of the Cache.
	 * The behavior is identical with {@link #iterator()}, but the traversed elements are
	 * treated as touched. This means, access time is updated and hit count statistics are maintained. 
	 * <p>
	 * <b> WARNING!!! Up to v0.9.9, the value reflects the actual state and may change
	 * after retrieving. For example accessTime can change if the entry is accessed, and  value.get() may return null if it is expired.
	 * 
	 * This behavior will change with v1.0, and the returned value will we immutable.
	 * </b>
	 * 
	 * @return The Iterator
	 */
	public TCacheHolderIterator<K, V> iteratorWithTouch()
	{
		throwISEwhenClosed();

		return new TCacheHolderIterator<K,V>(this, this.objects, this.expiryPolicy, true);
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
		return gatedHolder(objects.get(key)) != null;
	}

	/**
	 * Returns the value if this Cache contains a mapping for the specified key.
	 * Does not have any side effects like statistics or changing metadata like access time.
	 * @param key The key
	 * @return The value, or null if there is no mapping
	 */
	V peek(K key)
	{
		kvUtil.verifyKeyNotNull(key);
		AccessTimeObjectHolder<V> holder = gatedHolder(objects.get(key));
		return holder == null ? null : holder.peek();
	}

	/**
	 * Returns the value if this Cache contains a mapping for the specified key.
	 * Does not have any side effects like statistics or changing metadata like access time.
	 * @param key The key
	 * @return The value, or null if there is no mapping
	 */
	AccessTimeObjectHolder<V> peekHolder(K key)
	{
		kvUtil.verifyKeyNotNull(key);
		return gatedHolder(objects.get(key));
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
				statisticsCalculator = new LongAdderStatisticsCalculator();
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

	// --- ACTION CONTEXT -------------------------------------------------------------------------------------
	// Future directions: The action context may be made available to the user, and he could modify it on a
	//                    per-request basis, e.g. for providing "withAsync", changing timeouts, expiration
	//                    or to forbid write-through (e.g. for negative caching).
	@Override
	public CacheWriter<K, V> cacheWriter()
	{
		return cacheWriter;
	}

	@Override
	public ListenerCollection<K, V> listeners()
	{
		return listeners;
	}

	@Override
	public StatisticsCalculator statisticsCalculator()
	{
		return statisticsCalculator;
	}
	// --- ACTION CONTEXT -------------------------------------------------------------------------------------


	/**
	 * Releases the given holder and sends an EXPIRED notification
	 * @param key The key
	 * @param holder The holder
	 * @return true if the calling thread released the holder. false if the holder was already released.
     */
	boolean expireEntry(K key, AccessTimeObjectHolder<V> holder)
	{
		V value = holder.peek();
		boolean removed = releaseHolderBoolean(holder, value);
		if (removed) // SAE-150 Verify removal
		{
			listeners.dispatchEvent(EventType.EXPIRED, key, value);
		}
		
		return removed;
	}

    @Override
    public int elementCount() {
        return size();
    }

    @Override
    public long weight() {
        return weightLimiter.weight();
    }

    /**
     * Thread that removes expired entries.
     */
    public class CleanupThread extends Thread
    {
        private volatile boolean running; // volatile: modified via cancel() from a different thread
        private int removedEntries = 0;
        private long nextLogTimeMillis = System.currentTimeMillis() + CLEANUP_LOG_INTERVAL;
        
        CleanupThread(String cacheName)
        {
            super("CacheCleanupThread-" + cacheName);
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
                    
                    // TODO If cleanUpIntervalMillis is long (like 30s), and the shutdown Thread waits until this Thread
                    // is stopped, the shutdown Thread will wait very long.
                    sleep(cleanUpIntervalMillis);
                    
                    removedEntries += cleanUp();
                    if (removedEntries != 0)
                    {
                        long now = millisEstimator.millis();
                        if (now > nextLogTimeMillis)
                        {
                            logger.info(id() + " Cache has expired objects from Cache, count=" + removedEntries);
                            removedEntries = 0;
                            nextLogTimeMillis = now + CLEANUP_LOG_INTERVAL;
                        }
                    }
                    if (Thread.interrupted())
                    {
                        throw new InterruptedException();
                    }
                }
                catch (InterruptedException ex)
                {
                    logger.info("CleanupThread interrupted, keep running =" + running);
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

	@Override
	public String toString()
	{
		return "TriavaCache [" + configToString() + "]";
	}

	protected String configToString() {
        return "id=" + id
           //", builder=" + builder
           //+ ", strictJSR107=" + strictJSR107

           + ", storeClass=" + objects.getClass().getName()
           + ", storeMode=" + builder.getCacheWriteMode()

           + ", maxCacheTime=" + maxCacheTime + "ms"
           + ", maxCacheTimeSpread=" + maxCacheTimeSpread*1000 + "ms"

           + ", expiryPolicyType=" + expiryPolicy.getClass().getName()
           + ", expirationInterval=" + cleanUpIntervalMillis + "ms"
           + ", jamPolicy=" + jamPolicy

           + ", hasLoader=" + (loader != null)
           + ", hasWriter=" + (! (cacheWriter instanceof NopCacheWriter) )
           + ", listeners=" + listeners.size()

           + ", managementEnabled=" + isManagementEnabled()
           + ", statisticsEnabled=" + isStatisticsEnabled()

           + ", weightLimiter=" + weightLimiter
            ;
    }

	TCacheFactory getFactory()
	{
		return factory;
	}

	
}
