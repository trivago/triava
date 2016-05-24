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

package com.trivago.triava.tcache.core;

import javax.cache.Cache;
import javax.cache.event.EventType;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;

import com.trivago.triava.tcache.action.ActionRunner;
import com.trivago.triava.tcache.event.ListenerCollection;

/**
 * An ActionDispatcher delivers actions to all interested parties.
 * This implementation delivers actions to CacheWriter and ListenerCollection.
 * <p> 
 * The ActionDispatcher is useful in all situations that require to deliver actions to multiple interested parties at the same time.
 * Most API calls in the {@link Cache} interface that mutate the Cache can use the ActionDispatcher, but
 * notable exceptions are the bulk operations Cache.setAll() and Cache.deleteAll(). They require to first run write-through, and only possibly call the Listeners
 * (usually yes, but there are exceptions like errors in the CacheWriter). Implementation hint:  Cache.setAll() and Cache.deleteAll() do currently not use the
 * ActionDispatcher, but call listeners and cacheWriter manually.
 * 
 * @deprecated Replaced by {@link ActionRunner}
 * 
 * @author cesken
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public class ActionDispatcher<K,V>
{
	private final ListenerCollection<K, V> listeners;
	private final CacheWriter<K, V> cacheWriter;

	
	public ActionDispatcher(ListenerCollection<K, V> listeners, CacheWriter<K, V> cacheWriter)
	{
		this.listeners = listeners;
		this.cacheWriter = cacheWriter;
	}

	public void write(K key, V value, EventType eventType)
	{
		if (eventType != null)
			listeners.dispatchEvent(eventType, key, value);
		
		if (cacheWriter != null)
		{
			try
			{
				cacheWriter.write(new TCacheJSR107MutableEntry<K,V>(key, value));
			}
//			catch (CacheWriterException cwe)
//			{
//				//  Quoting the JavaDocs of CacheWriterException:
//				//    "A Caching Implementation must wrap any {@link Exception} thrown by a {@link CacheWriter} in this exception".
//				// Not sure whether this also applies to  CacheWriterException itself
//				throw cwe;
//			}
			catch (Exception exc)
			{
				//  Quoting the JavaDocs of CacheWriterException:
				//    "A Caching Implementation must wrap any {@link Exception} thrown by a {@link CacheWriter} in this exception".
				throw new CacheWriterException(exc);
			}
		}
	}

	public void delete(K key, V oldValue, boolean notifyListeners)
	{
//		System.out.println("ActionDispatcher.delete(" + key + ", " + oldValue + ", " + notifyListeners + "cacheWriter=" + cacheWriter);
		if (notifyListeners)
			listeners.dispatchEvent(EventType.REMOVED, key, oldValue);
		
		if (cacheWriter != null)
		{
			try
			{
				cacheWriter.delete(key);
			}
			catch (Exception exc)
			{
				//  Quoting the JavaDocs of CacheWriterException:
				//    "A Caching Implementation must wrap any {@link Exception} thrown by a {@link CacheWriter} in this exception".
				throw new CacheWriterException(exc);
			}
		}
	}
}
