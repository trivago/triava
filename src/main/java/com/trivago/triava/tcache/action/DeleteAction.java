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

package com.trivago.triava.tcache.action;

import javax.cache.event.EventType;
import javax.cache.integration.CacheWriterException;

import com.trivago.triava.tcache.eviction.Cache;

public class DeleteAction<K,V,W> extends Action<K,V,W>
{

	public DeleteAction(K key, Cache<K,V> actionContext)
	{
		super(key, null, EventType.REMOVED, actionContext);
	}

	@Override
	W writeThroughImpl()
	{
		try
		{
			cacheWriter.delete(key);
		}
		catch (Exception exc)
		{
			writeThroughException = new CacheWriterException(exc);
		}
		return null;
	}

	@Override
	void notifyListenersImpl(Object... args)
	{
		@SuppressWarnings("unchecked")
		V oldValue = (V)(args[0]); // This can actually be oldValue or value
		listeners.dispatchEvent(eventType, key, oldValue);
	}

	@Override
	void statisticsImpl()
	{
		// TODO Move statistics from Cache to here for the Delete operation 
//		stats.incrementRemoveCount();
	}

}
