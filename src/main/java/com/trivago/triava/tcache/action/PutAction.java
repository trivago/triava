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

import com.trivago.triava.tcache.core.TCacheJSR107Entry;

/**
 * An action suitable for any put-action like put, putIfAbsent, putAndRemove
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 * @param <W> The return type of the writeThroughImpl (unused)
 */
public class PutAction<K,V,W> extends Action<K,V,W>
{

	final boolean countStatistics;
	final boolean writeThrough;
	public PutAction(K key, V value, EventType eventType, boolean countStatistics)
	{
		super(key, value, eventType);
		this.countStatistics = countStatistics;
		this.writeThrough = true;
	}

	public PutAction(K key, V value, EventType eventType, boolean countStatistics, boolean writeThrough)
	{
		super(key, value, eventType);
		this.countStatistics = countStatistics;
		this.writeThrough = writeThrough;
	}

	@Override
	W writeThroughImpl(ActionRunner<K,V> actionRunner, Object arg)
	{
		if (!writeThrough)
			return null;
		
		try
		{
			actionRunner.cacheWriter.write(new TCacheJSR107Entry<K,V>(key, value));
		}
		catch (Exception exc)
		{
			writeThroughException = new CacheWriterException(exc);
		}
		return null;
	}

	@Override
	void notifyListenersImpl(ActionRunner<K,V> actionRunner, Object arg)
	{
		if (eventType != null)
			actionRunner.listeners.dispatchEvent(eventType, key, value);
	}

	@Override
	void statisticsImpl(ActionRunner<K,V> actionRunner, Object arg)
	{
		if (countStatistics)
			actionRunner.stats.incrementPutCount();
	}

}
