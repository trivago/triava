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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.event.EventType;

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.eviction.TCacheJSR107;

public class ListenerCollection<K,V>
{
	private final Set<ListenerEntry<K,V> > listeners = new HashSet<>(); // Move this to be copy-on-write data structure
	private final Builder<K, V> builder;
	private final Cache<K, V> tcache;
	private final TCacheJSR107<K, V> jsr107cache;
	/** listenerPresent is a data structure to quickly lookup which kinds of listeners have been registered. Lookup time is O(1). */ 
	private boolean listenerPresent[] = new boolean[EventType.values().length]; 

	/**
	 * Creates a ListenerCollection that consists of all listeners from builder.getCacheEntryListenerConfigurations().
	 * 
	 * @param tcache The Cache
	 * @param builder The Builder of the given tcache
	 */
	public ListenerCollection(Cache<K,V> tcache, Builder<K,V> builder)
	{
		this.builder = builder;
		this.tcache = tcache;
		this.jsr107cache = tcache.jsr107cache();
		
	    for (Iterator<CacheEntryListenerConfiguration<K, V>> it = builder.getCacheEntryListenerConfigurations().iterator(); it.hasNext(); )
	    {
	    	enableCacheEntryListener(it.next());
	    }

	}


	// TODO Make this thread-safe (listeners is a simple HashSet and thus unsafe)
	public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> listenerConfiguration)
	{
		throwISEwhenClosed();

		Iterator<ListenerEntry<K, V>> it = listeners.iterator();
		while (it.hasNext())
		{
			ListenerEntry<K, V> listenerEntry = it.next();
			if (listenerConfiguration.equals(listenerEntry.getConfig()))
			{
				listenerEntry.shutdown();
				it.remove();
				// Reflect listener change in the configuration, as required by JSR107
				builder.removeCacheEntryListenerConfiguration(listenerConfiguration);
				// TODO The listenerPresent array should be updated to reflect the new state.
				// The effect of not updating it has no functional impact. It is only leading to a small
				// performance loss - see the usage of hasListenerFor(eventType).
				break; // Can be only one, as it is in the Spec that Listeners must not added twice.
			}
		}
	}



	public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> listenerConfiguration)
	{
		throwISEwhenClosed();
		
		boolean added = enableCacheEntryListener(listenerConfiguration);
		if (!added)
		{
			throw new IllegalArgumentException("Cache entry listener may not be added twice to " + tcache.id() + ": "+ listenerConfiguration);
		}
		else
		{
			// Reflect listener change in the configuration, as required by JSR107
			builder.addCacheEntryListenerConfiguration(listenerConfiguration);
		}
	}
	
	/**
	 * Enables a listener, without adding it to the Configuration. An  enabled listener can send events after this method returns.  
	 * The caller must make sure that the
	 * corresponding Configuration object reflects the change.
	 * 
	 * @param listenerConfiguration
	 * @return
	 */
	// TODO Make this thread-safe (listeners is a simple HashSet and thus unsafe)
	boolean enableCacheEntryListener(CacheEntryListenerConfiguration<K, V> listenerConfiguration)
	{
		DispatchMode dispatchMode = listenerConfiguration.isSynchronous() ? DispatchMode.SYNC : DispatchMode.ASYNC_TIMED;
		ListenerEntry<K, V> newListener = new ListenerEntry<K, V>(listenerConfiguration, tcache, dispatchMode);
		boolean added = listeners.add(newListener);
		for (EventType eventType : EventType.values())
		{
			if (newListener.isListeningFor(eventType))
			{
				listenerPresent[eventType.ordinal()] = true;
			}
		}

		return added;
	}
	
	public void dispatchEvent(EventType eventType, K key, V value)
	{
		// Only start dispatching if we have a listener for it. Try to avoid garbage (TCacheEntryEvent) if nobody is interested in it.
		if (hasListenerFor(eventType))
			dispatchEventToListeners(new TCacheEntryEvent<K, V>(jsr107cache, eventType, key, value));
	}


	public void dispatchEvent(EventType eventType, K key, V value, V oldValue)
	{
		// Only start dispatching if we have a listener for it. Try to avoid garbage (TCacheEntryEvent) if nobody is interested in it.
		if (hasListenerFor(eventType))
			dispatchEventToListeners(new TCacheEntryEvent<K, V>(jsr107cache, eventType, key, value, oldValue));
	}

	/**
	 * Notifies all listeners that a given EventType has happened for all the given entries.
	 * You can force all listeners to Async mode, but should only do this if it is compliant with JSR107.
	 * <p>
	 * <b>IMPORTANT PERFORMANCE NOTE</b>: To avoid unnecessary object creation (TCacheEntryEvent and the event
	 * Iterable), you SHOULD check with {@link #hasListenerFor(EventType)} whether there is any Listener
	 * interested in the given eventType. If not, do you can spare to create the TCacheEntryEvent events.
	 * 
	 * @param entries The key-value pairs for which to send events
	 * @param eventType The event Type
	 * @param forceAsync Force async mode
	 */
	public void dispatchEvents(Map<K, V> entries, EventType eventType, boolean forceAsync)
	{
		if (!hasListenerFor(eventType))
		{
			return;
		}
		
		List<TCacheEntryEvent<K,V>> events = new ArrayList<>(entries.size());
		for (Entry<K, V> entry : entries.entrySet())
		{
			K key = entry.getKey();
			V value = entry.getValue();
			TCacheEntryEvent<K,V> event = new TCacheEntryEvent<>(jsr107cache, eventType, key, value);
			events.add(event);
		}
		dispatchEventsToListeners(events, eventType, forceAsync);
	}
	
	
	private void dispatchEventToListeners(TCacheEntryEvent<K, V> event)
	{
		for (ListenerEntry<K, V> listener : listeners)
		{
			listener.dispatch(event);
		}
	}

	/**
	 * Dispatch the events to all Listeners. You can force all listeners to Async mode, but should only
	 * do this if it is compliant with JSR107
	 * <p>
	 * <b>IMPORTANT PERFORMANCE NOTE</b>: To avoid unnecessary object creation (TCacheEntryEvent and the event
	 * Iterable), you SHOULD check with {@link #hasListenerFor(EventType)} whether there is any Listener
	 * interested in the given eventType. If not, do you can spare to create the TCacheEntryEvent events.
	 * 
	 * @param events The events to send
	 * @param eventType The event Type
	 * @param forceAsync Force async mode
	 */
	private void dispatchEventsToListeners(Iterable<TCacheEntryEvent<K, V>> events, EventType eventType, boolean forceAsync)
	{
		// Only start dispatching if we have a listener for it.
		if (!hasListenerFor(eventType))
			return;

		for (ListenerEntry<K, V> listener : listeners)
		{
			listener.dispatch(events, eventType, forceAsync);
		}
	}
	

	/**
	 * Checks whether this ListenerCollection includes at least one Listener for the given EventType.
	 *  
	 * @param eventType The EventType to check
	 * @return true if at least one Listener is interested in the EventType
	 */
	public boolean hasListenerFor(EventType eventType)
	{
		return (listenerPresent[eventType.ordinal()]);
	}
	
	/**
	 * Returns the number of listeners in this ListenerCollection.
	 * @return The number of listeners
	 */
	public int size()
	{
		return listeners.size();
	}
	
	/**
	 * Returns normally with no side effects if this cache is open. Throws IllegalStateException if it is closed.
	 */
	private void throwISEwhenClosed()
	{
		if (tcache.isClosed())
			throw new IllegalStateException("Cache already closed: " + tcache.id());
	}

	public void shutdown()
	{
		for (ListenerEntry<K, V> listener : listeners)
		{
			listener.shutdown();
		}
	}

}
