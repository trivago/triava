package com.trivago.triava.tcache.eviction;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.trivago.triava.logging.TriavaLogger;
import com.trivago.triava.logging.TriavaNullLogger;
import com.trivago.triava.tcache.JamPolicy;
import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.CacheLoader;
import com.trivago.triava.tcache.core.StorageBackend;
import com.trivago.triava.tcache.statistics.HitAndMissDifference;
import com.trivago.triava.tcache.statistics.NullStatisticsCalculator;
import com.trivago.triava.tcache.statistics.StandardStatisticsCalculator;
import com.trivago.triava.tcache.statistics.StatisticsCalculator;
import com.trivago.triava.tcache.statistics.TCacheStatistics;
import com.trivago.triava.tcache.statistics.TCacheStatisticsInterface;
import com.trivago.triava.tcache.util.CacheSizeInfo;
import com.trivago.triava.tcache.util.ObjectSizeCalculatorInterface;


/**
 * A Cache that supports expiration based on expiration time and idle time.
 * The Cache is unbounded, and never evicts. For bounded Caches, use subclasses like CacheLimitLFUv2, which
 * implement eviction strategies.
 * This Cache class is built for maximum throughput and scalability. The basic limitations are those of the
 * underlying ConcurrentMap implementation, as no further data structures are maintained. This is also true for
 * the subclasses that allow evictions.
 * 
 * @author Christian Esken
 * @author mpolacek
 * @since 2009-06-10
 *
 */
public class Cache<T> implements Thread.UncaughtExceptionHandler
{
	protected static TriavaLogger logger = new TriavaNullLogger();

	public static final class AccessTimeObjectHolder<T> implements TCacheHolder<T>
	{
		// 12 #4 
		private T data;
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
//		private String key;
		
		/**
		 * @param data
		 * @param maxIdleTime Maximum idle time in seconds
		 * @param maxCacheTime Maximum cache time in seconds 
		 */
		public AccessTimeObjectHolder(T data , long maxIdleTime, long maxCacheTime) {
			setLastAccessTime();
			this.data = data;

			setInputDate();
			this.maxIdleTime = limitToMaxInt(maxIdleTime);
			this.maxCacheTime = limitToMaxInt(maxCacheTime);
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

		public T get()
		{
			setLastAccessTime();
			return data;
		}

		/**
		 * Return the data, without modifying metadata like access time.
		 * <p> 
		 * This is a workaround, as there are currently use cases that iterate the whole cache.
		 * They should not modify use count or access time.
		 * <p>
		 * Future directions: Offer a better method for iteration, like an iterator or a Java 8 stream.
		 * 
		 * @return The value of this holder 
		 */
		public T peek()
		{
			return data;
		}

		private void setLastAccessTime()
		{
			lastAccess = (int)(currentTimeMillisEstimate() - baseTimeMillis);;
		}
		
//		private long currentTimeSecondsEstimate()
//		{
//			return MillisEstimatorThread.secondsEstimate;
//		}
		
		private long currentTimeMillisEstimate()
		{
			return MillisEstimatorThread.millisEstimate;
		}
		
		/**
		 * @return the lastAccess
		 */
		public long getLastAccess()
		{
			return baseTimeMillis + lastAccess;
		}

		public int getUseCount()
		{
			return useCount;
		}

		private void setInputDate()
		{
			inputDate = (int)(currentTimeMillisEstimate() - baseTimeMillis);
		}

		public long getInputDate()
		{
			return baseTimeMillis + inputDate;
		}

		@Deprecated
		public double getIdleTime()
		{
			return (currentTimeMillisEstimate() - getLastAccess()) / 1000.0;
		}
		
		public boolean isInvalid()
		{
			long millisNow = currentTimeMillisEstimate();
			if (maxCacheTime > 0L)
			{
				long cacheDuationMillis = millisNow - getInputDate();
				if (cacheDuationMillis > 1000*maxCacheTime)
				{
					return true;
				}
			}
			
			if (maxIdleTime == 0)
				return false;

			long idleSince = millisNow - getLastAccess();

			return (idleSince > 1000*maxIdleTime);
		}

//		public int compareTo(AccessTimeObjectHolder<T> o)
//		{
//			return (int)(getIdleTime() -  o.getIdleTime());
//		}

	}

	/**
	 * A thread that provides the value of System.currentTimeMillis() in 10ms resolution.
	 * The reason for a background thread is the the high cost of the System.currentTimeMillis() call.
	 * <p>
	 * Why do we need it?
	 * <p>
	 * Short story: It increases the write throughput to the Cache massively (100% or even 1000%).
	 *  <p>
	 * Long stoy: This thread implements a "read-once", "provide-often" scenario: The value is fetched from the operating system
	 * once, which includes a potentially expensive crossing of the operating system boundary (calling native, levering
	 * priviledge, register swapping, ...). If you call it thousands or millions of times per second, this has a very noticeable
	 * effect. Regarding the correctness this means that more writes are done with the same timestamp. For expiration
	 * this does not have any side effect. For eviction it only effects LRU - more entries would be possible eviction targets -
	 * but within 10ms it is usually not relevant which entry is evicted first.
	 *   
	 * 
	 * @author cesken
	 *
	 */
	public static class MillisEstimatorThread extends Thread
	{
		final static int UPDATE_INTERVAL_MS = 10;
		volatile static long millisEstimate = System.currentTimeMillis();
		volatile static long secondsEstimate = millisEstimate /1000;

		private volatile boolean running;  // Must be volatile, as it is modified via cancel() from a different thread

		MillisEstimatorThread()
		{
			setName("MillisEstimatorThread-" + UPDATE_INTERVAL_MS + "ms");
		}
		
		public void run()
		{
			logger.info("MillisEstimatorThread " + this.getName() + " has entered run()");
			this.running = true;
			while (running)
			{
				try
				{
					sleep(UPDATE_INTERVAL_MS);
					millisEstimate = System.currentTimeMillis();
					secondsEstimate = millisEstimate / 1000;
//					System.out.println("seconds=" + secondsEstimate);
				}
				catch (InterruptedException ex)
				{
				}
			}
			logger.info("MillisEstimatorThread " + this.getName() + " is leaving run()");
		}

		public void cancel()
		{
			this.running = false;
			this.interrupt();
		}

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
					sleep(cleanUpIntervalMillis);
					this.failedCounter = 0;
					if (Thread.interrupted())
					{
						throw new InterruptedException();
					}
					cleanUp();
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
		
		public void cancel()
		{
			this.running = false;
			this.interrupt();
		}
	
	}

	final private String id;
	private final static long baseTimeMillis = System.currentTimeMillis();
	// idle time in seconds
	private final long defaultMaxIdleTime;
	// max cache time in seconds
	private final long maxCacheTime;
	final protected ConcurrentMap<Object,AccessTimeObjectHolder<T>> objects;
	Random random = new Random(System.currentTimeMillis());
	
//	@ObjectSizeCalculatorIgnore
	private volatile transient CleanupThread cleaner = null;
	private volatile long cleanUpIntervalMillis;

	/**
	 * Cache hit counter.
	 * We do not expect any overruns, as we use "long" to count hits and misses.
	 * If we would do 10 Cache GET's per second, this would mean 864.000 GET's per day and 6.048.000 per Week.
	 * Even counting with "int" would be enough (2.147.483.647), as we would get no overrun within roughly 35 weeks.
	 * If we have 10 times the load this would still be enough for 3,5 weeks with int, and "infinitely" more with long.
	 */
	final StatisticsCalculator statisticsCalculator;

	private float[] hitrateLastMeasurements = new float[5];
	int hitrateLastMeasurementsCurrentIndex = 0;
	private volatile boolean shuttingDown = false;

	protected JamPolicy jamPolicy = JamPolicy.WAIT;
	protected final CacheLoader<Object, T> loader;

	
	/**
	 * constructor with default cache time and expected map size.
	 * @param maxIdleTime Maximum idle time in seconds
	 * @param maxCacheTime Maximum Cache time in seconds
	 * @param expectedMapSize
	 */
	public Cache(String id, long maxIdleTime, long maxCacheTime, int expectedMapSize)
	{
		this( new Builder<Object,T>(TCacheFactory.standardFactory())
		.setId(id).setMaxIdleTime(maxIdleTime).setMaxCacheTime(maxCacheTime)
		.setExpectedMapSize(expectedMapSize) );
	}


	/**
	 * Construct a Cache, using the given configuration from the Builder.
	 * @param builder
	 */
	public Cache(Builder builder)
	{
		this.id = builder.getId();
		this.maxCacheTime = builder.getMaxCacheTime();
		this.defaultMaxIdleTime = builder.getMaxIdleTime();
		// Next line: "* 100" is actually "* 1000 / 10".
		// a) "* 1000" is for converting secs to ms.
		// b) "/ 10" is for using 10% of that time as cleanupInterval
		this.cleanUpIntervalMillis = this.defaultMaxIdleTime * 100; // makes defaultMaxIdleTime / 10
		this.jamPolicy = builder.getJamPolicy();
		this.loader  = builder.getLoader();

		StorageBackend<Object, T> storageFactory = builder.storageFactory();
		objects = storageFactory.createMap(builder, evictionExtraSpace(builder));
		
		if (builder.getStatistics())
			statisticsCalculator = new StandardStatisticsCalculator();
		else
			statisticsCalculator = new NullStatisticsCalculator();
		
		builder.getFactory().registerCache(this);
	}

	/**
	 * Returns a size factor for the map for the specific eviction strategy of this Cache. The default implementation
	 * returns 1, as it does not evict. Values bigger than 1 mean to size the underlying ConcurrentMap bigger, if
	 * it requires that.
	 * 
	 * @return Size factor for the map
	 */
	protected int evictionExtraSpace(Builder<Object,T> builder)
	{
		return 0;
	}
	
	
	

	public String id()
	{
		return id;
	}

	/**
	 * Can be overridden by implementations, if they require a custom clean up.
	 * In overridden, super.shutdown() must be called.
	 */
	public void shutdown()
	{
		shutdownPrivate();
	}

	final static long MAX_SHUTDOWN_WAIT_MILLIS = 100; // 100 ms

	/**
	 * Shuts down this Cache. It removes all cache entries, stops the cleaner and makes sure no new entries
	 * can be placed in the Cache. 
	 */
	private void shutdownPrivate()
	{	
		shuttingDown = true;
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
	 * @param millis
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
				break; // if we reach this line, the join was succesful 
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
	 * Copied from com.trivago.commons.util.Util.sleepSImple()
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
	 * @param sleepMillis
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
	 * @return MAx idle time in seconds
	 */
	public long getDefaultMaxIdleTime()
	{
		return this.defaultMaxIdleTime;
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
	 * @param pKey
	 * @param pData
	 */
	public void put(Object pKey, T pData)
	{
		put(pKey, pData, this.defaultMaxIdleTime, this.maxCacheTime);
	}
	
	
	/**
	 * Add an object to the cache under the given key with the given cache time in seconds
	 * @param pKey
	 * @param pData
	 * @param pMaxIdleTime
	 */
	public void put(Object pKey, T pData, long pMaxIdleTime, long maxCacheTime)
	{
		putToMap(pKey, pData, pMaxIdleTime, maxCacheTime, false);
	}

	/**
	 * The same like {@link #put(Object, Object, long, long)}, but uses
	 * {@link java.util.concurrent.ConcurrentMap#putIfAbsent(Object, Object)} to actually write
	 * the data in the backing ConcurrentMap. For the sake of the Cache hit or miss statistics this method
	 * is a treated as a read operation and thus updates the hit or miss counters. Rationale is that the
	 * putIfAbsent() result is usually evaluated by the caller.
	 * 
	 * @param pKey
	 * @param pData
	 * @param pMaxIdleTime Maximum idle time in seconds
	 * @param maxCacheTime Maximum cache time in seconds
	 * @return See {@link ConcurrentHashMap#putIfAbsent(Object, Object)}
	 */
	public T putIfAbsent(Object pKey, T pData, long pMaxIdleTime, long maxCacheTime)
	{
		AccessTimeObjectHolder<T> holder = putToMap(pKey, pData, pMaxIdleTime, maxCacheTime, true);
		if ( holder == null )
			return null;
		else
			return holder.data;
	}
	
	public T putIfAbsent(Object pKey, T pData)
	{
		AccessTimeObjectHolder<T> holder = putToMap(pKey, pData, this.defaultMaxIdleTime, this.maxCacheTime, true);
		
		if (holder == null)
		{
			return null;
		}
		
		return holder.data;
	}
	
	
	protected AccessTimeObjectHolder<T> putToMap(Object key, T value, long idleTime, long cacheTime, boolean putIfAbsent)
	{
		return putToMap(key, value, idleTime, cacheTime, putIfAbsent, false);
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
	 * @return If returnEffectiveHolder is true, the holder object of the object in the Map is returned. If returnEffectiveHolder is false, the returned value is like described in {@link java.util.Map#put(Object, Object)}. 
	 */
	protected AccessTimeObjectHolder<T> putToMap(Object key, T data, long idleTime, long cacheTime, boolean putIfAbsent, boolean returnEffectiveHolder)
	{
		if (shuttingDown)
		{
			// We don't accept new entries if this Cache is shutting down
			return null;
		}
		
		if(data == null || idleTime < 0)
		{
			// Reject invalid data
			return null;
		}
		
		boolean hasCapacity = ensureFreeCapacity();
		if (!hasCapacity)
		{
			statisticsCalculator.incrementDropCount();
			return null;
		}

		if (cacheTime <= 0)
			cacheTime = this.maxCacheTime;
		
		if (idleTime <= 0 && cacheTime <= 0) 
		{
			logger.error("Adding object to Cache with infinite lifetime: " + key.toString() + " : " + data.toString());
		}
		
		AccessTimeObjectHolder<T> holder; // holder returned by objects.put*().
		AccessTimeObjectHolder<T> newHolder; // holder that was created via new. 
		AccessTimeObjectHolder<T> effectiveHolder; // holder that is effectively in the Cache 
		
		if (putIfAbsent)
		{
			newHolder = new AccessTimeObjectHolder<T>(data, idleTime, cacheTime);
			holder = this.objects.putIfAbsent(key, newHolder);
			
			/*
			 *	A putIfAbsent() is also treated as a GET operation, so update cache statistics. 
			 *	See putIfAbsent() docs above for a more  detailed explanation.
			 */
			if (holder == null)
			{
				statisticsCalculator.incrementPutCount();
				statisticsCalculator.incrementMissCount();
				effectiveHolder = newHolder;
			}
			else
			{
				statisticsCalculator.incrementHitCount();
				incrementUseCount(holder);
				effectiveHolder = holder;
			}
		}
		else
		{
			newHolder = new AccessTimeObjectHolder<T>(data, idleTime, cacheTime);
			holder = this.objects.put(key, newHolder);
//			holder = newHolder;  // <<< wrong
			effectiveHolder = newHolder;
			statisticsCalculator.incrementPutCount();
		}
		ensureCleanerIsRunning();
		if (returnEffectiveHolder)
		{
			return effectiveHolder;
		}
		else
		{
			return holder;
		}
	}

//	/**
//	 * Add an existing AccessTimeObjectHolder<T> object to the cache.
//	 * You can use this when reconstruction a Cache from a serialized form.
//	 * (NB: Currently this is not in use. It was used for the HotelPool, where snapshots are taken to Memcached.)
//	 * @param atoh The AccessTimeObjectHolder object.
//	 */
//	public void put(Object key, AccessTimeObjectHolder<T> atoh)
//	{
//		objects.put(key, atoh);
//		ensureCleanerIsRunning(atoh.data.getClass().getName());
//	}


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
	 * Sets the cleanup interval for evicting idle cache entries. If you do not call this method, the default
	 * cleanup interval is used, which is 1/10 * idleTime. This is often a good value.
	 * @param cleanUpIntervalMillis
	 */
	public void setCleanUpIntervalMillis(long cleanUpIntervalMillis)
	{
		this.cleanUpIntervalMillis = cleanUpIntervalMillis;
	}

	/**
	 * Checks whether the cleaner is running. If not, the cleaner gets started.
	 * @param pData A name used as part of the name of the Cleaner Thread 
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
	 * @param key
	 * @return The value
	 * @throws NullPointerException if key is null
	 * @throws RuntimeException, if key is not present and the loader threw an Exception
	 */
	public T get(Object key) throws RuntimeException
	{
		AccessTimeObjectHolder<T> holder = this.objects.get(key);

		boolean loaded = false;
		if (holder == null && loader != null)
		{
			// Data not present, but can be loaded
			try
			{
				T loadedValue = loader.load(key);
				holder = putToMap(key, loadedValue, defaultMaxIdleTime, maxCacheTime, false, true);
				loaded = true;
				// ##LOADED_MISS_COUNT##
				statisticsCalculator.incrementMissCount(); // needed to load => increment miss count
			}
			catch (Exception exc)
			{
				throw new RuntimeException("CacheLoader " + id + " failed to load key=" + key, exc);
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

		if (holder.isInvalid())
		{
			// debugLogger.debug("1lCache GET key:"+pKey.hashCode()+"; CACHE:invalid");
			// Hint: We do not remove the value here, as it will be done in the asynchronous thread anyways.
			statisticsCalculator.incrementMissCount();
			return null;
		}
		// debugLogger.debug("1lCache GET key:"+pKey.hashCode()+"; CACHE:hit");
		incrementUseCount(holder);
		statisticsCalculator.incrementHitCount();
		return holder.get();
	}

	protected void incrementUseCount(AccessTimeObjectHolder<T> holder)
	{
		// The increment below is obviously not thread-safe, but it is good enough for our purpose (eviction statistics).
		// We spare synchronization code, and the holder can be more light-weight (not using AtomicInteger).
		holder.useCount ++;
	}


	/**
	 * Fills the givenCache statistics object.
	 * 
	 * @return The CacheStatistic object
	 */
	protected TCacheStatisticsInterface fillCacheStatistics(TCacheStatisticsInterface cacheStatistic)
	{
		cacheStatistic.setHitCount(statisticsCalculator.getHitCount());
		cacheStatistic.setMissCount(statisticsCalculator.getMissCount());
		cacheStatistic.setHitRatio(getCacheHitrate());
		cacheStatistic.setElementCount(objects.size());
		cacheStatistic.setPutCount(statisticsCalculator.getPutCount());
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
//	@ObjectSizeCalculatorIgnore
	private MillisEstimatorThread millisEsitimator = null; 

	/**
	 * Returns the Cache hit rate. The returned value is the average of the last n measurements (2012-10-16: n=5).
	 * Implementation note: This method should be called in regular intervals, because it also updates
	 * its "hit rate statistics array".
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
				HitAndMissDifference stats = statisticsCalculator.updateDifference();
				
				// -3- Add the new value to the floating array hitrateLastMeasurements
				long cacheGets = stats.getHitDifference() + stats.getMissDifference();
				float hitRate = cacheGets == 0 ? 0f :  (float)stats.getHitDifference() / (float)cacheGets * 100f;
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
	    
	    if (millisEsitimator == null)
	    {
	    	millisEsitimator  = new MillisEstimatorThread();
		    millisEsitimator.setPriority(Thread.MAX_PRIORITY);
		    millisEsitimator.setDaemon(true);
		    millisEsitimator.start();	
	    }
	    
	    logger.info(this.id + " Cache started, timeout: " + this.defaultMaxIdleTime);
	}
	
	private void stopCleaner()
	{
		stopCleaner(0);
	}
	
	private synchronized String stopCleaner(long millis)
	{
		String errorMsg = null;
		Cache<T>.CleanupThread cleanerRef = cleaner;
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
		int removedEntries = 0;
	    for (Iterator<Entry<Object, AccessTimeObjectHolder<T>>> iter = this.objects.entrySet().iterator(); iter.hasNext(); )
	    {
	    	Entry<Object,AccessTimeObjectHolder<T>> entry = iter.next();
	    	AccessTimeObjectHolder<T> holder = entry.getValue();
	    	
	    	if (holder.isInvalid())
	    	{
	    		iter.remove();
	    		holder.release();
	    		++removedEntries;
	    	}
	    }
	    
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
	 * remove object with given key
	 * @param pKey
	 */
	public Object remove(Object pKey)
	{
		AccessTimeObjectHolder<T> holder = (AccessTimeObjectHolder<T>) this.objects.remove(pKey);
		return removeHolder(holder);
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
	 * @param key
	 */
	public void expireUntil(Object key, int maxDelay, TimeUnit timeUnit)
	{
		AccessTimeObjectHolder<T> holder = (AccessTimeObjectHolder<T>) this.objects.get(key);
		if (holder == null)
		{
			return;
		}
		
		int maxDelaySecs = (int)timeUnit.toSeconds(maxDelay);
		int delaySecs = random.nextInt(maxDelaySecs);
		
		if (holder.maxCacheTime == 0 || delaySecs < holder.maxCacheTime)
		{
			// holder.maxCacheTime was not set (never expires), or new value is smaller => use it 
			holder.maxCacheTime = limitToMaxInt(delaySecs);
		}
		// else: Keep delay, as holder will already expire sooner than delaySecs.
	}

	
	protected Object removeHolder(AccessTimeObjectHolder<T> holder)
	{
		if(holder == null)
		{
			return null;
		}
		
		T oldData = holder.get();
		
		holder.release();
		
		return oldData;
	}
	
	/**
	 * Retrieve reference to AccessTimeHolder<T> objects as an unmodifiable Collection.
	 * You can use this when you want to serialize the complete Cache. 
	 *
	 * @return Collection of all AccessTimeHolder<T> Objects. The collection is unmodifiable.
	 */
	public Collection<AccessTimeObjectHolder<T>> getAccessTimeHolderObjects()
	{
		return Collections.unmodifiableCollection(objects.values());
	}
	
	/**
	 * Returns a thread-safe unmodifiable collection of the keys.
	 * 
	 * @return The keys as a Collection
	 */
	public Collection<Object> keySet()
	{
		// The backing map is a ConcurrentMap, so there should not be any ConcurrentModificationException.
		return Collections.unmodifiableCollection(objects.keySet());
	}


	/**
	 * Returns true if this map contains a mapping for the specified key.
	 * 
	 * @see java.util.concurrent.ConcurrentMap#containsKey(Object)
	 */
	public boolean containsKey(Object key)
	{
		return this.objects.containsKey(key);
	}

	
	static int limitToMaxInt(long value)
	{
		return Math.min((int)value, Integer.MAX_VALUE);
	}

	/**
	 * Sets the logger that will be used for all Cache instances.
	 * 
	 * @param logger
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
}
