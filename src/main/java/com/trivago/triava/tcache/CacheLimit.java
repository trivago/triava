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

import com.trivago.triava.annotations.ObjectSizeCalculatorIgnore;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.eviction.EvictionInterface;
import com.trivago.triava.tcache.eviction.HolderFreezer;
import com.trivago.triava.tcache.statistics.SlidingWindowCounter;
import com.trivago.triava.tcache.statistics.TCacheStatisticsInterface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.cache.event.EventType;

/**
 * A size limited Cache, that evicts elements asynchronously in the background.
 * The element to be evicted are chosen by the evictionClass, 
 * 
 * @author cesken
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class CacheLimit<K, V> extends Cache<K, V>
{
    private static final boolean INTERMEDIATE_NOTFULL_NOTIFICATION = false;

	private static final boolean LOG_INTERNAL_DATA = true;

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


	public CacheLimit(TCacheFactory factory, Builder<K, V> builder)
	{
		super(factory, builder);
		if (builder.getEvictionClass() == null)
		{
			throw new IllegalArgumentException("evictionClass must not be null in an evicting Cache");
		}
		this.evictionClass = builder.getEvictionClass();
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

        ArrayList<Object> evictedElements = new ArrayList<>();

		// Future directions: Pass a "Listener" down here, instead of the full tcache 
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

                    final boolean expiryNotification = listeners.hasListenerFor(EventType.EXPIRED);
					if (expiryNotification && evictedElements == null)
					{
					    // TODO We tune the initial size here only once when creating the ArrayList.
                        double removePercent =
                            (double)weightLimiter.weightToRemove() / weightLimiter.evictionBoundaries().maxWeight();
                        double removeElemements =
                            (double)weightLimiter.evictionBoundaries().maxElements() * removePercent;
                        int estimatedEvictedElements = (int)Math.min(removeElemements, Integer.MAX_VALUE);

                        // The ArrayList holds key1, value1, key2, value2, ... , keyN, valueN
                        // For the listeners we need to create a Map, but at that time evictionNotifierDone.notifyAll
                        // () was already called resolving a possible write stall.
						evictedElements = new ArrayList<>(2 * (estimatedEvictedElements + 10));
					}

					evict(evictedElements);
					
					synchronized (evictionNotifierDone)
					{
						evictionIsRunning = false;
						evictionNotifierDone.notifyAll();
					}
					
					if (expiryNotification)
					{
					    HashMap<K,V> evictedElementsMap = new HashMap<K,V>(evictedElements.size(), 1.0f);
					    for (int i=0; i<evictedElements.size(); i+=2) {
					        evictedElementsMap.put((K)evictedElements.get(i), (V)evictedElements.get(i+1));
					    }

					    //logger.info("dispatchEvent EXPIRED for " + evictedElementsMap.size() + " entries");
						// Send "EXPIRED" notifications (this is EVICTION, but it is not documented in the JSR107 specs
						// whether one should send "REMOVED" or "EXPIRED" for evictions.
						listeners.dispatchEvents(evictedElementsMap, EventType.EXPIRED, true);
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
					/*
					 * In case of an Exception, there is no "evictionNotifierDone.notifyAll();".
					 * Threads waiting on evictionNotifierDone may be stuck forever, or at least until the next
					 * put() operation starts another eviction cycle via evictionNotifierQ. 
					 * 
					 * This behavior is wanted, as in presence of an Exception we cannot be sure whether elements were evicted at all.
					 */
                    //int lastSize = evictedElements.size();
					evictedElements.clear();
					// clear() does not shrink the underlying array, so it will just grow over time. For now,
                    // call trimToSize() to work around that. ensureCapacity() could also be used.
                    //evictedElements.ensureCapacity(lastSize);
                    evictedElements.trimToSize();
                    // We either want to clear the map or recreate it. The JMH GetPutBenchmark from Caffeine showed repeatedly 3-4% better performance for both
                    // read_only and readwrite, so we keep evictedElements.clear() for now. Future directions: HashMap might degrade over time, so recreate it from time to time.
                    //evictedElements = null;
				}

			} // while running

			logger.info(id() + " Eviction Thread ended");
		}

		/**
		 * Evict overflow elements from this Cache. The number of elements is determined by elementsToRemove()
         * @param evictedElements
         */
		protected void evict(ArrayList<Object> evictedElements)
		{
			counterEvictionsRounds++;
			evictionClass.beforeEviction();
			evictWithFreezer(evictedElements);
			evictionClass.afterEviction();
		}
		
		/**
		 * Evict optimally according to eviction policy by inspecting ALL Cache entries.
		 * The values to be compared are frozen, so that comparisons
		 * are consistent during the eviction.
         * @param evictedElements
         */
		protected void evictWithFreezer(ArrayList<Object> evictedElements)
		{
            long weightToRemovePreCheck = weightLimiter.weightToRemove();
			if (weightToRemovePreCheck <= 0)
			{
				/*
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
			Set<Entry<K, AccessTimeObjectHolder<V>>> entrySet = objects.entrySet();
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
			HolderFreezer<K, V>[] toCheck = toCheckL.toArray(new HolderFreezer[toCheckL.size()]);
			Arrays.sort(toCheck, evictionClass.evictionComparator());

			int removedCount = 0;
            long removedWeight = 0;

			// Important note: We do not re-use the value elemsToRemovePreCheck. Other threads may have added
			// elements or removed some (eviction + expiration thread). Even though the size is
			// a moving goal, we want to be as close as possible to the true value. So lets call
			// elementsToRemove() again.
            long weightToRemove = weightLimiter.weightToRemove();
			int notifyCountdown = 1000;

            final boolean expiryNotification = evictedElements != null;

			for (HolderFreezer<K, V> entryToRemove : toCheck)
			{
				K key = entryToRemove.getKey();
				V oldValue = removeAndRelease(key); // ###C###
				if (oldValue != null)
				{
					/*
					 * By evaluating the removeAndRelaese() return value we know that the cache entry was removed by us
					 * (the EvictionThread). This means we count only what has not "magically" disappeared
					 * between ###A### and ###C###. Actually the reasons for disappearing are not magical at
					 * all: Most notably objects can disappear because they expire, see the CleanupThread in
					 * the base class. Also if someone calls #remove(), the entry can disappear.
					 */
                    removedWeight += weigher.weight(oldValue);
					++removedCount;
					if (INTERMEDIATE_NOTFULL_NOTIFICATION)
					{
					    /*
					     * This is an optimization to notify blocked writers asap instead of at the end of eviction.
					     * This is (very likely) not fully thread safe, so the feature is disabled.
					     * 
					     * Blocked writer could miss notifications and be stuck forever. The order of events could be:
					     * 1) A blocked writer Thread B is notified. B does not wait on the lock any longer
					     * 2) Before B can write we run out of space
					     * 3) Eviction thread is running and notifies. B does not see it, as it currently does not wait
					     * 4) B calls evictionNotifierDone.wait() but missed the notify.
					     * 
					     * To test and reproduce: Use a test with a single writer thread. As no other thread writes there
					     * will be no more eviction triggering via ensureFreeCapacity(). 
					     * 
					     * Future directions: replace lock object with a Condition object, just like it is done in many Queue implementations:
					     * final Condition notFull  = lock1.newCondition();
					     * final Condition full     = lock2.newCondition();
					     * final Condition overfull = lock3.newCondition();
					     */
        					if (notifyCountdown-- == 0 && !weightLimiter.isFull()) {
        	                                       synchronized (evictionNotifierDone)
        	                                        {
        
        	                                           evictionNotifierDone.notifyAll();
        	                                        }
        					    notifyCountdown = 1000;
        					}
					}
					
					if (expiryNotification) {
					    this.evictedElements.add(key);
                        this.evictedElements.add(oldValue);
					}
					if (removedWeight >= weightToRemove) {
                        break; // reached or eviction target
                    }
				}
				// else: Removed in the meantime by some other means: delete API call, eviction, expiration
			}
			
			evictionCount.addAndGet(removedCount);			
			statisticsCalculator.incrementRemoveCount(removedCount);
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
		if (!weightLimiter.isFull()) {
            return true;
        }

		EvictionThread evictionThread = ensureEvictionThreadIsRunning();
		evictionThread.trigger();
		

		if (weightLimiter.isOverfull())
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
		while (weightLimiter.isOverfull())
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

    @Override
    protected String configToString() {
        EvictionInterface<K, V> evictionClass = builder.getEvictionClass();
        String evictionClassName = evictionClass == null ? "null" : evictionClass.getClass().getSimpleName();
        return super.configToString() + ", eviction-class=" + evictionClassName+ ", " + weightLimiter.toString();
    }

}
