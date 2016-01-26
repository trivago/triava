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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.trivago.triava.annotations.ObjectSizeCalculatorIgnore;
import com.trivago.triava.tcache.JamPolicy;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.EvictionInterface;
import com.trivago.triava.tcache.statistics.SlidingWindowCounter;
import com.trivago.triava.tcache.statistics.TCacheStatisticsInterface;

/**
 * A size limited Cache, that evicts elements asynchronously in the background.
 * The element to be evicted are chosen by the evictionClass, 
 * 
 * @author cesken
 *
 * @param K Key type
 * @param V Value type
 */
public class CacheLimit<K, V> extends Cache<K, V>
{
	/**
	 * FREE_PERCENTAGE defines how much elements to free.
	 * Percentage relates to evictionStartAt. 
	 */
	private static final int FREE_PERCENTAGE = 10; // Future directions: Move to instance. Add to Builder.
	private static final float EVICTION_SPACE_PERCENT = 15; // Future directions: Move to instance. Add to Builder.
	
	// --- FEATURE_ExtraParEvictionSpace START ----------
	private static final boolean FEATURE_ExtraParEvictionSpace = false;
	private static final int EVICTION_SPACE_PER_WRITER = 2500;
	private static final int MAXIMUM_EVICTION_SPACE_FOR_WRITERS = 200000;
	// --- FEATURE_ExtraParEvictionSpace END ----------

	private static final boolean LOG_INTERNAL_DATA = true;
	private static final boolean LOG_INTERNAL_EXTENDED_DATA = false;

	protected EvictionInterface<K, V> evictionClass = null;

	@ObjectSizeCalculatorIgnore(reason="Thread contains a classloader, which would lead to measuring the whole Heap")
	private volatile transient EvictionThread evictor = null;
	
	protected final AtomicLong evictionCount  = new AtomicLong();	
	private int counterEvictionsRounds = 0;
	private AtomicInteger  counterEvictionsHalts = new AtomicInteger();
	private SlidingWindowCounter evictionRateCounter = new SlidingWindowCounter(60, 1);
	
	private final Object evictionNotifierDone = new Object();
	
	// 1 element in the blocking queue is likely enough, but using 2 should definitely decouple reads and writes
	private final BlockingQueue<Boolean> evictionNotifierQ = new LinkedBlockingQueue<>(2);


	public CacheLimit(Builder<K, V> builder)
	{
		super(builder);
		if (builder.getEvictionClass() == null)
		{
			throw new IllegalArgumentException("evictionClass must not be null in an evicting Cache");
		}
		this.evictionClass = builder.getEvictionClass();
	}

	// *** VALUES BELOW ARE FIXED AT CONSTRUCTION. See evictionExtraSpace(builder) ************************  
	private int userDataElements; // SET DURING  CONSTRUCTION
	private int blockStartAt; // SET DURING  CONSTRUCTION
	private int evictUntilAtLeast; // SET DURING  CONSTRUCTION
	private int evictNormallyElements; // SET DURING  CONSTRUCTION
	// *** VALUES ABOVE ARE FIXED AT CONSTRUCTION. See evictionExtraSpace(builder) ************************  


	/**
	 * Returns whether the Cache is full. We declare the Cache full, when it has reached the number of
	 * expected elements, even though there may be some extra eviction space available.
	 * 
	 * @return true, if the cache is full
	 */
	protected boolean isFull()
	{
		int size = objects.size();
		boolean full = size >= userDataElements;
//		if (LOG_INTERNAL_DATA && LOG_INTERNAL_EXTENDED_DATA && full)
//		{
//			logger.info("isFull: size=" + size + ", userDataElements=" +  userDataElements);
//		}
		
		return full;
	}
	
	/**
	 * Returns whether the cache is overfull. We declare the Cache full, when it has reached the 
	 * blocking position.
	 * @return true, if the cache is overfull
	 */
	protected boolean isOverfull()
	{
		// maxElements = expectedElements from the configuration. NOT how we sized the ConcurrentMap. 
		int size = objects.size();
		boolean full = size >= blockStartAt;
		if (full)
		{
			if (LOG_INTERNAL_DATA && logInternalExtendedData())
				logger.info("Overfull [" + id() +"]. currentSize=" +  size + ", blockStartAt="+ blockStartAt);
		}
		
		return full;
	}


	private static final boolean logInternalExtendedData()
	{
		return LOG_INTERNAL_EXTENDED_DATA;
	}

	/**
	 * Calculates the amount of extra space that we need as eviction extra space in the storage Map.
	 * The amount returned is the number of extra elements to allocate in the Map. A StorageBackend
	 * can use that.
	 * 
	 * <p>
	 * Internally this method calculates some more numbers:
	 * 
	 *   0                               // Empty
	 *   private int evictUntilAtLeast;  // Position below userDataElements
	 *   private int userDataElements;   // Expected elements, as given by user
	 *   private int blockStartAt;       // Block mark
	 *   
	 *   private int evictNormallyElements; // SET DURING  CONSTRUCTION
	 *
	 * 
	 * @return The number of extra elements required in the storage Map.
	 */
	@Override
	protected int evictionExtraSpace(Builder<K, V> builder)
	{
		double factor = EVICTION_SPACE_PERCENT / 100D; //  20/100d = 0.2
		userDataElements = builder.getExpectedMapSize();
		
		final int parallelityEvictionSpace;
		if (FEATURE_ExtraParEvictionSpace)
		{
			/**
			 * This can take a significant amount of extra memory, especially for small sized caches which contain
			 * big elements. Example would be a Session cache, with (e.g.) 2000 Elements, each sized 500KB => 1GB size.
			 * If we would allow the below number, it would allow 2500*14 = 35000 more elements => 17,5GB.
			 * This is clearly undesirable. Thus the feature is currently off.
			 * 
			 * It may likely be more desirable to let "factor" grow, e.g. by 1% per 5 Threads (max 20%) 
			 */
			int evictionSpacePerWriter = builder.getConcurrencyLevel() * EVICTION_SPACE_PER_WRITER;
			parallelityEvictionSpace = Math.min(evictionSpacePerWriter, MAXIMUM_EVICTION_SPACE_FOR_WRITERS);
		}
		else
		{
			parallelityEvictionSpace = 0;
		}

		long normalEvictionSpace = (long)(userDataElements * factor);
		long extraEvictionSpace = Math.max(normalEvictionSpace, parallelityEvictionSpace);
		long plannedSizeLong = userDataElements + extraEvictionSpace;
		blockStartAt = (int)Math.min(plannedSizeLong, Integer.MAX_VALUE); 
		
		evictNormallyElements = (int)((double)userDataElements * FREE_PERCENTAGE / 100D);
		evictUntilAtLeast = userDataElements - evictNormallyElements;
		if (LOG_INTERNAL_DATA)
		{
			logger.info("Cache eviction tuning [" + id() +"]. Size=" + userDataElements + ", BLOCK=" + blockStartAt + ", evictToPos=" + evictUntilAtLeast + ", normal-evicting=" + evictNormallyElements);
		}
		
		return blockStartAt - userDataElements;
	}

	/**
	 * Determine how many elements to remove. The goal is to reach the interval
	 * [ {@link #evictUntilAtLeast}, {@link #userDataElements}]. Typically we would try to
	 * evict {@link #evictNormallyElements} elements.
	 *
	 * @return The number of elements to remove
	 */
	protected int elementsToRemove()
	{
		int currentElements = objects.size();
		if (currentElements < userDataElements)
		{
			// [0, userDataElements-1] means: Not full. Nothing to evict.
			return 0;
		}
		
		// ----------------------------------------------------------
		
		int removeTargetPos = currentElements - evictNormallyElements;
		if (removeTargetPos > userDataElements)
		{
			// Evict will reach [userDataElements, MAX_INT] : Evicting not enough
			removeTargetPos = userDataElements - evictNormallyElements;
		}
		
		// removeTargetPos in now in the interval [-MAX_INT,  userDataElements-1]
		if (removeTargetPos >= evictUntilAtLeast)
		{
			// Evict will reach [evictUntilAtLeast, userDataElements-1] : Good
		}
		else
		{
			// Evict will reach [0, evictUntilAtLeast-1] : Too much
			removeTargetPos = evictUntilAtLeast;
		}

		// else: Make sure we make room for at least until evictUntilAtLeast
		int removeCount1 = currentElements - removeTargetPos;
		if (removeCount1 < 0)
		{
			logger.error("Trying to evict a negative number of elements. id=" + id() + ", currentElements=" + currentElements + ", removeCount=" + removeCount1);
		}
		
		return removeCount1 < 0 ? 0 : removeCount1;
	}

	/**
	 * Returns a reference to the EvictionThread, starting the thread if it is not running.
	 * 
	 * @return A non null reference to the EvictionThread
	 */
	private EvictionThread ensureEvictionThreadIsRunning()
	{
		EvictionThread eThread = evictor;
		if (eThread != null)
		{
			// This is a fast path for the normal case. Eviction thread is running.
			// No locks are in this part.
			// already running
			return eThread;
		}

		synchronized (this)
		{
			if (evictor == null)
			{
				eThread = new EvictionThread("CacheEvictionThread-" + id());
				eThread.setPriority(Thread.MIN_PRIORITY);
				eThread.setDaemon(true);
				eThread.setUncaughtExceptionHandler(eThread);
				eThread.start();
				evictor = eThread;
				logger.info(id() + " Eviction Thread started");
			}
			else
			{
				eThread = evictor;
			}
		}

		return eThread;
	}

	class EvictionThread extends Thread implements Thread.UncaughtExceptionHandler
	{
		volatile boolean running = true;
		volatile boolean evictionIsRunning = false;
		
		public EvictionThread(String name)
		{
			super(name);
		}

		@Override
		public void run()
		{
			while (running)
			{

				try
				{
					evictionIsRunning = false;
					evictionNotifierQ.take();  // wait
					evictionNotifierQ.clear();  // get rid of further notifications (if any)
					// --- clear() must be before evictionIsRunning = true;  // TODO Explain why!!! There is a race condition when we do not do this. But it needs an explanation
					evictionIsRunning = true;
//					if (LOG_INTERNAL_DATA && logInternalExtendedData())
//						System.out.println("Evicting");
					evict();
					
					synchronized (evictionNotifierDone)
					{
						evictionIsRunning = false;
						evictionNotifierDone.notifyAll();
					}
				}
				catch (InterruptedException e)
				{
					logger.info(id() + " Eviction Thread interrupted");
					// Ignore: If someone wants to cancel this thread, "running" will also be false
				}
				catch (Exception e)
				{
					logger.error(id() + " Eviction Thread error", e);
				}
				finally
				{
					evictionIsRunning = false;
					/**
					 * In case of an Exception, there is no "evictionNotifierDone.notifyAll();".
					 * Threads waiting on evictionNotifierDone may be stuck forever, or at least until the next
					 * put() operation starts another eviction cycle via evictionNotifierQ. 
					 */
				}

			} // while running

			logger.info(id() + " Eviction Thread ended");
		}

		/**
		 * Evict overflow elements from this Cache. The number of elements is determined by elementsToRemove()
		 */
		protected void evict()
		{
			counterEvictionsRounds++;
			evictionClass.beforeEviction();
			evictWithFreezer();
			evictionClass.afterEviction();
		}
		
		/**
		 * Evict optimally according to eviction policy by inspecting ALL Cache entries.
		 * The values to be compared are frozen, so that comparisons
		 * are consistent during the eviction.  
		 */
		protected void evictWithFreezer()
		{
			int elemsToRemovePreCheck = elementsToRemove();
			if (elemsToRemovePreCheck <= 0)
			{
				/**
				 * Check, if eviction makes sense. Rationale: In a concurrent situation, threads may enqueue
				 * an additional "eviction request".
				 * 
				 * This thread: evictionNotifierQ.clear(); // get rid of further notifications (if any)
				 * 
				 * Other thread: evictionNotifierQ.offer(Boolean.TRUE);
				 * 
				 * This thread: evictionIsRunning = true;
				 * 
				 * For the case described above: If we wouldn't check at the beginning of this method, we would first go
				 * through the entrySet() (which can be very expensive due to CHM locking) only to find out
				 * shortly after that there is no work to do.
				 */
				return;
			}
			
			int i=0;
			Set<Entry<K, Cache.AccessTimeObjectHolder<V>>> entrySet = objects.entrySet();
			// ###A###
			int size = entrySet.size();

			ArrayList<HolderFreezer<K, V>> toCheckL = new ArrayList<>(size);
			for (Entry<K, AccessTimeObjectHolder<V>> entry : entrySet)
			{
				if (i == size)
				{
					// Skip new elements that came in between ###A### and ###B###
					// This is required, as entrySet reflects changes made to the map.
					break;
				}

				K key = entry.getKey();
				AccessTimeObjectHolder<V> holder = entry.getValue();
				long frozenValue = evictionClass.getFreezeValue(key, holder);
				HolderFreezer<K,V> frozen = new HolderFreezer<>(key, holder, frozenValue);

				toCheckL.add(i, frozen);
				i++;
			}
			// ###B###

			@SuppressWarnings("unchecked")
			HolderFreezer<K, V>[] toCheck = toCheckL.toArray(new HolderFreezer[0]);
			Arrays.sort(toCheck, evictionClass.evictionComparator());

			int removedCount = 0;
			
			// Important note: We do not re-use the value elemsToRemovePreCheck. Other threads may have added
			// elements or removed some (eviction + expiration thread). Even though the size is
			// a moving goal, we want to be as close as possible to the true value. So lets call
			// elementsToRemove() again.
			int elemsToRemove = elementsToRemove();
			for (HolderFreezer<K, V> entryToRemove : toCheck)
			{
				V oldValue = remove(entryToRemove.getKey()); // ###C###
				if (oldValue != null)
				{
					/**
					 * By evaluating the remove() return value we know that the cache entry was removed by us
					 * (the EvictionThread). This means we count only what has not "magically" disappeared
					 * between ###A### and ###C###. Actually the reasons for disappearing are not magical at
					 * all: Most notably objects can disappear because they expire, see the CleanupThread in
					 * the base class. Also if someone calls #remove(), the entry can disappear.
					 */
					++removedCount;
					if (removedCount >= elemsToRemove)
						break;
				}
				// else: Removed in the meantime by some other means: delete API call, eviction, expiration
			}
			
			evictionCount.addAndGet(removedCount);			
			evictionRateCounter.registerEvents(millisEstimator.seconds(), removedCount);
		}


		public void shutdown()
		{
			running = false;
			evictionIsRunning = false;
			this.interrupt();
		}

		/**
		 * This is called, should the EvictionThread should go down on an unexpected (uncaught) Exception.
		 */
		@Override
		public void uncaughtException(Thread thread, Throwable throwable)
		{
			logger.error("EvictionThread Thread " + thread + " died because uncatched Exception", throwable);

			// We must make sure that evictor will be recreated on demand
			evictionIsRunning = false;
			evictor = null;
		}

//		@edu.umd.cs.findbugs.annotations.SuppressWarnings(value = { "RV_RETURN_VALUE_IGNORED_BAD_PRACTICE" }, justification = "evictionNotifierQ.offer() is just to notify. If queue is full, then notification has already been sent.")
		public void trigger()
		{
			if (evictionIsRunning)
			{
				// Fast-path if the Eviction Thread already is running. No locks in this code path.
				return;
			}
			
			evictionNotifierQ.offer(Boolean.TRUE);
		}
	}

	private synchronized String stopEvictor(long millis)
	{
		String errorMsg = null;
		CacheLimit<K, V>.EvictionThread evictorRef = evictor;
		if ( evictorRef != null )
		{
			evictorRef.shutdown();
			if (millis > 0)
			{
				if (! joinSimple(evictorRef, millis, 0) )
				{
					errorMsg = "Shutting down Eviction Thread FAILED";
				}
			}
		}
		
		return errorMsg;
	}
	
	/**
	 * Shuts down the Eviction thread.
	 */
	@Override
	public void shutdownCustomImpl()
	{
		super.shutdownCustomImpl();
		
		String errorMsg = stopEvictor(MAX_SHUTDOWN_WAIT_MILLIS);
		if (errorMsg != null)
		{
			logger.error("Shutting down Evictor for Cache " + id() + " FAILED. Reason: " + errorMsg);
		}
		else
		{
			logger.info("Shutting down Evictor for Cache " + id() + " OK");
		}
	}


	/**
	 * Frees entries, if the Cache is near full.
	 * 
	 * @return true, if free capacity can be granted.
	 */
	@Override
	protected boolean ensureFreeCapacity()
	{
		if (!isFull())
			return true;

		EvictionThread evictionThread = ensureEvictionThreadIsRunning();
		evictionThread.trigger();
		

		if (isOverfull())
		{
			counterEvictionsHalts.incrementAndGet();
			if ( jamPolicy == JamPolicy.DROP)
			{
				// Even when dropping, make sure the evictor will make some space for the next put(). 
				evictionThread = ensureEvictionThreadIsRunning();
				evictionThread.trigger();
				return false;
			}			
		}
		
		// JamPolicy.WAIT
		while (isOverfull())
		{
			try
			{
				synchronized (evictionNotifierDone)
				{
					if (evictionThread.evictionIsRunning)
					{
						evictionNotifierDone.wait();
					}
				}
				evictionThread = ensureEvictionThreadIsRunning();
				evictionThread.trigger();
			}
			catch (InterruptedException e)
			{
				// ignore, and continue waiting
			}
		}
		
		return true;
	}

	@Override
	protected TCacheStatisticsInterface fillCacheStatistics(TCacheStatisticsInterface cacheStatistic)
	{
		cacheStatistic.setEvictionCount(evictionCount.get());
		cacheStatistic.setEvictionRounds(counterEvictionsRounds);
		cacheStatistic.setEvictionHalts(counterEvictionsHalts.get());
		cacheStatistic.setEvictionRate(evictionRateCounter.getRateTotal(millisEstimator.seconds()));
		
		return super.fillCacheStatistics(cacheStatistic);
	}

}
