package com.trivago.triava.tcache.event;

import javax.cache.event.CacheEntryEvent;
import javax.cache.event.EventType;

public class TCacheEntryEventCollection<K,V>
{

	private final Iterable<CacheEntryEvent<? extends K, ? extends V>> events;
	private final EventType eventType;
	
	/**
	 * Creates a TCacheEntryEventCollection where all events have the same type "event"
	 * 
	 * @param events The events
	 * @param event The EventType. All events must match the given event.
	 */
	TCacheEntryEventCollection(Iterable<CacheEntryEvent<? extends K, ? extends V>> events, EventType eventType)
	{
		this.events = events;
		this.eventType = eventType;
	}

	public Iterable<CacheEntryEvent<? extends K, ? extends V>> events()
	{
		return events;
	}

	public EventType eventType()
	{
		return eventType;
	}
}
