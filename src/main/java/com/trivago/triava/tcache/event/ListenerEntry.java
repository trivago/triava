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

package com.trivago.triava.tcache.event;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;
import javax.cache.event.EventType;

import com.trivago.triava.tcache.eviction.Cache;

/**
 * Holds a CacheEntryListenerConfiguration, and the objects created from it: CacheEntryEventFilter and
 * CacheEntryListener. The {@link #hashCode()} and {@link #equals(Object)} are only looking whether the
 * CacheEntryListenerConfiguration are the identical object (reference comparison), which makes it easy to
 * follow the JSR107 requirement that the same CacheEntryListenerConfiguration must be registered only once
 * via {@link javax.cache.Cache#registerCacheEntryListener(CacheEntryListenerConfiguration)}.
 * <p>
 * This class is strictly internal: Fields should be private and this class should provide only
 * package-private methods.
 * 
 * @author cesken
 *
 * @param <K> The Key type
 * @param <V> The Value type
 */
final class ListenerEntry<K,V>
{
	private final CacheEntryListenerConfiguration<K, V> config;

	private CacheEntryEventFilter<? super K, ? super V> filter = null;
	private CacheEntryListener<? super K, ? super V> listener = null;
	
	private final Cache<K, V> tcache;
	
	private final CacheEventManager<K,V> eventManager;
	private final DispatchMode dispatchMode;
	private final BlockingQueue<TCacheEntryEventCollection<K,V>> dispatchQueue;
	private DispatchRunnable dispatchThread = null;

	/**
	 * Creates a ListenerEntry from the factories in CacheEntryListenerConfiguration.
	 * Both CacheEntryEventFilter and CacheEntryListener are created.
	 * The {@link #dispatchMode} regulates how events get dispatched, for example synchronous, asynchronous batched or timed
	 * 
	 * @param config The CacheEntryListenerConfiguration
	 * @param tcache The cache which events should be listened to 
	 * @param dispatchMode How events are dispatched to listeners
	 */
	ListenerEntry(CacheEntryListenerConfiguration<K, V> config, Cache<K,V> tcache, DispatchMode dispatchMode)
	{
		this.config = config;
		this.tcache = tcache;
		this.dispatchMode = dispatchMode;

		CacheEventManager<K,V> em = null;
		Factory<CacheEntryListener<? super K, ? super V>> listenerFactory = config.getCacheEntryListenerFactory();
		if (listenerFactory != null)
		{
			listener = listenerFactory.create();
			if (listener != null)
			{
				Factory<CacheEntryEventFilter<? super K, ? super V>> filterFactory = config
						.getCacheEntryEventFilterFactory();
				if (filterFactory != null)
					filter = filterFactory.create();
				
				em = new ListenerCacheEventManager<K,V>();
			}
		}
		
		eventManager = em;

		if (dispatchMode.isAsync())
		{
			this.dispatchQueue = new ArrayBlockingQueue<TCacheEntryEventCollection<K,V>>(1024);
			/**
			 * Future directions: Starting the listener in the constructor is problematic.
			 * If this class would be subclassed, the Thread would start too early. Right now it cannot happen, as this class is final.
			 * Second, we possibly want a Thread restart mechanism anyhow, like we have with the expiration and eviction threads. For the
			 * latter, there should be a dedicated "BackgroundThreadController<T>" class that controls/restarts background threads.
			 */
			dispatchThread = ensureListenerThreadIsRunning();
		}
		else
		{
			dispatchQueue = null;
		}

	}
	
	CacheEntryListenerConfiguration<K, V> getConfig()
	{
		return config;
	}
	
	
	
	/**
	 * Sends the events to the listener, if it passes the filter. Sending is done in batches of up to 256 events,
	 * either synchronously or asynchronously
	 * All events in the list must have the same event type.
	 * 
	 * @param events The events to dispatch
	 * @param eventType The event type
	 * @param forceAsync Force async mode
	 */
	void dispatch(Iterable<TCacheEntryEvent<K, V>> events, EventType eventType, boolean forceAsync)
	{
		if (eventManager == null)
			return;

		@SuppressWarnings("unchecked")
		CacheEntryListener<K, V> listenerRef = (CacheEntryListener<K, V>) this.listener;


		int batchSize = 256;
		int i = 0;
//		int sentCount = 0;
//		int sentBatches = 0;
		boolean needsSend = false;
		
		List<CacheEntryEvent<? extends K, ? extends V>> interestingEvents = new ArrayList<>(batchSize);
		for (TCacheEntryEvent<? extends K, ? extends V> event : events)
		{
			
			if (!interested(event))
				continue; // filtered
			
			interestingEvents.add(event);
			needsSend = true;
			
//			sentCount ++;
			
			if (i++ == batchSize)
			{
//				sentBatches++;
				scheduleEvents(interestingEvents, listenerRef, eventType, forceAsync);
				needsSend = false;
				i = 0;
			}
		}
		
		// Push out the last batch
		if (needsSend)
		{
//			sentBatches++;
			scheduleEvents(interestingEvents, listenerRef, eventType, forceAsync);
		}
		
//		if (sentBatches > 0)
//			System.out.println("sendEvent: " + sentCount + " in " + sentBatches + " batches (multi). type=" + eventType);

	}


	/**
	 * Sends one event to the listener, if it passes the filter. Sending is either done synchronously or asynchronously
	 * 
	 * @param event The event to dispatch
	 */
	void dispatch(TCacheEntryEvent<K, V> event)
	{
		if (eventManager == null)
			return;
		
		if (!interested(event))
			return; // filtered

		
		@SuppressWarnings("unchecked")
		CacheEntryListener<K, V> listenerRef = (CacheEntryListener<K, V>) this.listener;

		if (!dispatchMode.isAsync())
		{
			sendEvent(event, listenerRef);
		}
		else
		{
			try
			{
				dispatchQueue.put(createSingleEvent(event));
			}
			catch (InterruptedException e)
			{
				/** Interruption policy:
				 * The #dispatch method can be part of client interaction like a put or get call. Or it can
				 * be from internal operations like eviction. In both cases we do not want to blindly
				 * bubble up the stack until some random code catches it. Reason is, that it could leave the
				 * Cache in an inconsistent state, e.g. a value was put() into the cache but the statistics
				 * do not reflect that. Thus, we simply mark the current thread interrupted, so any caller
				 * on any stack level may inspect the status.
				 */
				Thread.currentThread().interrupt();
				// If we come here, the event may not be in the dispatchQueue. But we will not
				// retry, as there are no guarantees when interrupting and it is safer to just go on.
				// For example if during shutdown the dispatchQueue is full, we would iterate here
				// forever as the DispatchRunnable instance could be shutdown and not read from the
				// queue any longer.
			}
		}
	}


	/**
	 * Schedules to send the events to the given listener. Scheduling means to send immediately if this
	 * {@link ListenerEntry} is synchronous, or to put it in a queue if asynchronous (including the forceAsync
	 * parameter. For synchronous delivery, it is guaranteed that the listener was executed when returning
	 * from this method.
	 * <p>
	 * The given eventType must match all events
	 * 
	 * @param events The events to send
	 * @param listener The listener to notify
	 * @param eventType The event type. It must match all events to send
	 * @param forceAsync
	 */
	private void scheduleEvents(List<CacheEntryEvent<? extends K, ? extends V>> events, CacheEntryListener<K, V> listener,
			EventType eventType, boolean forceAsync)
	{
		if (eventManager == null)
			return;
		
		TCacheEntryEventCollection<K, V> eventColl = new TCacheEntryEventCollection<>(events, eventType);
		if (!(forceAsync || dispatchMode.isAsync()))
		{
			sendEvents(eventColl, listener);
		}
		else
		{
			try
			{
				dispatchQueue.put(eventColl);
			}
			catch (InterruptedException e)
			{
				/** Interruption policy:
				 * The #dispatch method can be part of client interaction like a put or get call. Or it can
				 * be from internal operations like eviction. In both cases we do not want to blindly
				 * bubble up the stack until some random code catches it. Reason is, that it could leave the
				 * Cache in an inconsistent state, e.g. a value was put() into the cache but the statistics
				 * do not reflect that. Thus, we simply mark the current thread interrupted, so any caller
				 * on any stack level may inspect the status.
				 */
				Thread.currentThread().interrupt();
				// If we come here, the event may not be in the dispatchQueue. But we will not
				// retry, as there are no guarantees when interrupting and it is safer to just go on.
				// For example if during shutdown the dispatchQueue is full, we would iterate here
				// forever as the DispatchRunnable instance could be shutdown and not read from the
				// queue any longer.
			}
		}
	}
	
	private void sendEvent(CacheEntryEvent<? extends K, ? extends V> event, CacheEntryListener<K, V> listener)
	{
//		System.out.println("sendEvent: 1 (single)");

		TCacheEntryEventCollection<K, V> singleEvent = createSingleEvent(event);
		switch (event.getEventType())
        {
            case CREATED:
                if (listener instanceof CacheEntryCreatedListener)
                    eventManager.created((CacheEntryCreatedListener<K, V>)listener, singleEvent);
                break;

            case EXPIRED:
                if (listener instanceof CacheEntryExpiredListener)
                    eventManager.expired((CacheEntryExpiredListener<K, V>)listener,  createSingleEvent(event));
                break;

            case UPDATED:
                if (listener instanceof CacheEntryUpdatedListener)
                    eventManager.updated((CacheEntryUpdatedListener<K,V>)listener,  createSingleEvent(event));
                break;

            case REMOVED:
                if (listener instanceof CacheEntryRemovedListener)
                    eventManager.removed((CacheEntryRemovedListener<K,V>)listener,  createSingleEvent(event));
                break;

            default:
                // By default do nothing. If new event types are added to the Spec they are ignored.
        }
	}

	private void sendEvents(TCacheEntryEventCollection<K, V> eventColl, CacheEntryListener<K, V> listener)
	{
		EventType eventType = eventColl.eventType();
//		System.out.println("sendEvents to listener " + listener + ", eventType=" + eventColl.eventType());
		switch (eventType)
        {
            case CREATED:
                if (listener instanceof CacheEntryCreatedListener)
                    eventManager.created((CacheEntryCreatedListener<K, V>)listener, eventColl);
                break;

            case EXPIRED:
                if (listener instanceof CacheEntryExpiredListener)
                    eventManager.expired((CacheEntryExpiredListener<K, V>)listener,  eventColl);
                break;

            case UPDATED:
                if (listener instanceof CacheEntryUpdatedListener)
                    eventManager.updated((CacheEntryUpdatedListener<K,V>)listener,  eventColl);
                break;

            case REMOVED:
                if (listener instanceof CacheEntryRemovedListener)
                    eventManager.removed((CacheEntryRemovedListener<K,V>)listener,  eventColl);
                break;

            default:
                // By default do nothing. If new event types are added to the Spec they are ignored.
        }
	}
	
	/**
	 * Creates a TCacheEntryEventCollection from the given single event.
	 * 
	 * @param event The event
	 * @return The event collection with one element  
	 */
	private TCacheEntryEventCollection<K,V> createSingleEvent(TCacheEntryEvent<K, V> event)
	{
		List<CacheEntryEvent<? extends K, ? extends V>> events = new ArrayList<>();
		events.add(event);
		TCacheEntryEventCollection<K,V> coll = new TCacheEntryEventCollection<K,V>(events, event.getEventType());
		return coll;
	}

	/**
	 * Checks whether this ListenerEntry is interested in the given event. More formally,
	 * it is interested, when it passses the filter or when there is no filter at all.
	 * 
	 * @param event The CacheEntryEvent to check
	 * @return true if interested
	 */
	private boolean interested(CacheEntryEvent<? extends K, ? extends V> event)
	{
		return filter == null ? true : filter.evaluate(event);
	}
	

//	public CacheEntryEventFilter<? super K, ? super V> getListener()
//	{
//		return listener;
//	}

	void shutdown()
	{
		DispatchRunnable runnable = dispatchThread;
		if (runnable != null)
		{
			runnable.shutdown();
		}
	}

	@Override
	public int hashCode()
	{
		return getConfig().hashCode();
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof ListenerEntry))
			return false;
		
		return this.getConfig() == ((ListenerEntry<?,?>)obj).getConfig();
	}

	/**
	 *
	 */
	private class DispatchRunnable  extends Thread implements Runnable
	{
		private volatile boolean running = true;

		DispatchRunnable(String cacheId)
		{
			super("tCache-Notifier:" + cacheId);
			setDaemon(true);
		}

		@Override
		public void run()
		{
			@SuppressWarnings("unchecked")
			CacheEntryListener<K, V> listenerRef = (CacheEntryListener<K, V>) listener;

			while (running)
			{
				try
				{
					TCacheEntryEventCollection<K, V> eventColl = dispatchQueue.take();
					sendEvents(eventColl, listenerRef);
				}
				catch (InterruptedException ie)
				{
					// Interruption policy: Only used for quitting
				}
				catch (Exception exc)
				{
					// If the thread enters this line, there was an issue wit sendEvent(). Likely it
					// is in the user provided Listener code, so we must make sure not to die if this
					// happens. For now we will silently ignore any errors.
				}
			}
		}

		 public void shutdown()
		 {
			 running = false;
			 Thread thread = dispatchThread;
			 if (thread != null)
			 {
				 thread.interrupt();
				 dispatchThread = null;
			 }
		 }

	}
	
	/**
	 * Starts the DispatchRunnable thread
	 * 
	 * @return
	 */
	private DispatchRunnable ensureListenerThreadIsRunning()
	{
		dispatchThread = new DispatchRunnable(tcache.id());
		dispatchThread.start();
		
		return dispatchThread;
	}

	


	/**
	 * Creates an Iterable with a single element in it 
	 * @param event The event to make available
	 * @return The Iterable
	 */
	private TCacheEntryEventCollection<K, V> createSingleEvent(
			CacheEntryEvent<? extends K, ? extends V> event)
	{
		List<CacheEntryEvent<? extends K, ? extends V>> list = new ArrayList<>();
		list.add(event);
		TCacheEntryEventCollection<K,V> coll = new TCacheEntryEventCollection<>(list, event.getEventType());
		return coll;
	}
	
	/**
	 * Returns whether this {@link ListenerEntry} is listening to the given eventType
	 * @param eventType The event Type
	 * @return true, if the listener is listening to the given eventType
	 */
	boolean isListeningFor(EventType eventType)
	{
		switch (eventType)
		{
			case CREATED:
				return listener instanceof CacheEntryCreatedListener;
			case EXPIRED:
				return listener instanceof CacheEntryExpiredListener;
			case REMOVED:
				return listener instanceof CacheEntryRemovedListener;
			case UPDATED:
				return listener instanceof CacheEntryUpdatedListener;
		}

		return false;
	}


}