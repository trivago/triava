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
	 * @param eventType The EventType. All events must match the given event.
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
