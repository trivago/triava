/*********************************************************************************
 * Copyright 2016-present trivago GmbH
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

import javax.cache.event.CacheEntryCreatedListener;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

/**
 * An interface for a manager that forwards the given events to the given listener.
 * {@link ListenerCacheEventManager} actually only forwards the events. This interface
 * is here to be able to create decorators that e.g. also log the event, proxy it to
 * a remote service, or globally filter events.
 * <p>
 * Implementation note: At the moment you cannot inject decorators from outside, neither
 * by code nor by configuration. But this is an option that should be considered.
 * 
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public interface CacheEventManager<K,V>
{
	void created(CacheEntryCreatedListener<K, V> listener, TCacheEntryEventCollection<K, V> eventColl);
	void updated(CacheEntryUpdatedListener<K, V> listener, TCacheEntryEventCollection<K, V> eventColl);
	void removed(CacheEntryRemovedListener<K, V> listener, TCacheEntryEventCollection<K, V> eventColl);
	void expired(CacheEntryExpiredListener<K, V> listener, TCacheEntryEventCollection<K, V> eventColl);
}
