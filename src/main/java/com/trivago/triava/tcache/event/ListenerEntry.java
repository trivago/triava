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

import com.trivago.triava.tcache.core.CacheEventManager;
import com.trivago.triava.tcache.core.ListenerCacheEventManager;
import com.trivago.triava.tcache.eviction.Cache;

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
	
	/**
	 * Creates a ListenerEntry from the factories in CacheEntryListenerConfiguration.
	 * Both CacheEntryEventFilter and CacheEntryListener are created.
	 * 
	 * @param config The CacheEntryListenerConfiguration
	 * @param tcache The cache which events should be listened to 
	 */
	public ListenerEntry(CacheEntryListenerConfiguration<K, V> config, Cache<K,V> tcache, DispatchMode dispatchMode)
	{
		if (dispatchMode != DispatchMode.SYNC)
		{
			throw new UnsupportedOperationException("DispatchMode is not yet supported: " + dispatchMode);
		}
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
			
			@SuppressWarnings("unchecked")
			CacheEntryListener<K, V> listener = (CacheEntryListener<K, V>) this.listener;
			
			if (dispatchMode == DispatchMode.SYNC)
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
			else
			{
				// TODO Implement async/batched dispatching
			}
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

}