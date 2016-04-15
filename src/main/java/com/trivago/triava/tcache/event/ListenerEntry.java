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

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryEventFilter;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

import com.trivago.triava.tcache.eviction.Cache;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Holds a CacheEntryListenerConfiguration and the two "listeners" created from it,
 * the CacheEntryEventFilter and the CacheEntryListener. The {@link #hashCode()} and
 * {@link #equals(Object)} are only looking whether the CacheEntryListenerConfiguration are the identical object (reference comparison),
 * which makes it easy to follow the JSR107 requirement that the same CacheEntryListenerConfiguration
 * must be registered only once via {@link javax.cache.Cache#registerCacheEntryListener(CacheEntryListenerConfiguration)}.  
 * 
 * @author cesken
 *
 * @param <K> The Key type
 * @param <V> The Value type
 */
public class ListenerEntry<K,V> // should be private to TCache
{
	final CacheEntryListenerConfiguration<K, V> config;

	CacheEntryEventFilter<? super K, ? super V> filter = null;
	CacheEntryListener<? super K, ? super V> listener = null;
	
	final Cache<K, V> tcache;
	
	final CacheEventManager<K,V> eventManager;
	final DispatchMode dispatchMode;
	final BlockingQueue<CacheEntryEvent<K, V>> dispatchQueue;
	DispatchRunnable dispatchThread = null;

	/**
	 * Creates a ListenerEntry from the factories in CacheEntryListenerConfiguration.
	 * Both CacheEntryEventFilter and CacheEntryListener are created.
	 * 
	 * @param config The CacheEntryListenerConfiguration
	 * @param tcache The cache which events should be listened to 
	 */
	public ListenerEntry(CacheEntryListenerConfiguration<K, V> config, Cache<K,V> tcache, DispatchMode dispatchMode)
	{
		this.config = config;
		this.tcache = tcache;
		this.dispatchMode = dispatchMode;
		if (dispatchMode.isAsync())
		{
			this.dispatchQueue = new ArrayBlockingQueue<CacheEntryEvent<K, V>>(1024);
			dispatchThread = new DispatchRunnable("tCacheEventDispatcher-" + tcache.id());
			dispatchThread.start();
		}
		else
		{
			dispatchQueue = null;
		}

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
				
				em = ListenerCacheEventManager.instance();
			}
		}
		
		eventManager = em;
	}
	
	public CacheEntryListenerConfiguration<K, V> getConfig()
	{
		return config;
	}
	

	/**
	 * Sends the event to the listener, if it passes the filter. Sending is either done synchronously or asynchronously
	 * 
	 * @param event The event to dispatch
	 */
	public void dispatch(CacheEntryEvent<K, V> event)
	{
		if (eventManager != null)
		{
			if (!interested(event))
				return; // filtered
			
//			@SuppressWarnings("unchecked")
			CacheEntryListener<K, V> listener = (CacheEntryListener<K, V>) this.listener;

			if (!dispatchMode.isAsync())
			{
				sendEvent(event, listener);
			}
			else
			{
				try
				{
					dispatchQueue.put(event);
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
	}

	private void sendEvent(CacheEntryEvent<K, V> event, CacheEntryListener<K, V> listener)
	{
		switch (event.getEventType())
        {
            case CREATED:
                if (listener instanceof CacheEntryCreatedListener)
                    eventManager.created(tcache, (CacheEntryCreatedListener<K,V>)listener, event);
                break;

            case EXPIRED:
                if (listener instanceof CacheEntryExpiredListener)
                    eventManager.expired(tcache, (CacheEntryExpiredListener<K,V>)listener,  event);
                break;

            case UPDATED:
                if (listener instanceof CacheEntryUpdatedListener)
                    eventManager.updated(tcache, (CacheEntryUpdatedListener<K,V>)listener,  event);
                break;

            case REMOVED:
                if (listener instanceof CacheEntryRemovedListener)
                    eventManager.removed(tcache, (CacheEntryRemovedListener<K,V>)listener,  event);
                break;

            default:
                // By default do nothing. If new event types are added to the Spec they are ignored.
        }
	}


	/**
	 * Checks whether this ListenerEntry is interested in the given event. More formally,
	 * it is interested, when it passses the filter or when there is no filter at all.
	 * 
	 * @param event The CacheEntryEvent to check
	 * @return true if interested
	 */
	private boolean interested(CacheEntryEvent<K, V> event)
	{
		return filter == null ? true : filter.evaluate(event);
	}
	

//	public CacheEntryEventFilter<? super K, ? super V> getListener()
//	{
//		return listener;
//	}

	public void shutdown()
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

		DispatchRunnable(String id)
		{
			super(id);
			setDaemon(true);
		}

		@Override
		public void run()
		{
			CacheEntryListener<K, V> listenerRef = (CacheEntryListener<K, V>) listener;

			while (running)
			{
				try
				{
					final CacheEntryEvent<K, V> event = dispatchQueue.take();
					sendEvent(event, listenerRef);
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
}