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
import javax.cache.event.CacheEntryEvent;
import javax.cache.event.CacheEntryExpiredListener;
import javax.cache.event.CacheEntryRemovedListener;
import javax.cache.event.CacheEntryUpdatedListener;

import com.trivago.triava.tcache.eviction.Cache;

public interface CacheEventManager<K,V>
{
	void created(Cache<K,V> cache, CacheEntryCreatedListener<K, V> listener, CacheEntryEvent<K, V> event);
	void updated(Cache<K,V> cache, CacheEntryUpdatedListener<K, V> listener, CacheEntryEvent<K, V> event);
	void removed(Cache<K,V> cache, CacheEntryRemovedListener<K, V> listener, CacheEntryEvent<K, V> event);
	void expired(Cache<K,V> cache, CacheEntryExpiredListener<K, V> listener, CacheEntryEvent<K, V> event);	
}
