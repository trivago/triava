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

public abstract class Action<K,V,W>
{
	final K key;
	final V value;
	EventType eventType;
	
	// Result
	CacheWriterException writeThroughException = null;
	
	Action(K key, V value, EventType eventType)
	{
		this.key = key;
		this.value = value;
		this.setEventType(eventType);		
	}
	
	
	public void writeThrough(ActionRunner<K,V> actionRunner)
	{
		writeThroughImpl(actionRunner);

	}
	
	public void notifyListeners(ActionRunner<K,V> actionRunner, Object... args)
	{
		notifyListenersImpl(actionRunner, args);
	}
	
	public void statistics(ActionRunner<K,V> actionRunner, Object... args)
	{
		statisticsImpl(actionRunner, args);
	}

	abstract W writeThroughImpl(ActionRunner<K,V> actionRunner);
	abstract void notifyListenersImpl(ActionRunner<K,V> actionRunner, Object... args);
	abstract void statisticsImpl(ActionRunner<K,V> actionRunner, Object... args);
	
	public void close() throws CacheWriterException
	{
		if (writeThroughException != null)
		{
			throw writeThroughException;
		}
	}



	public void setEventType(EventType eventType)
	{
		this.eventType = eventType;
	}


	public boolean successful()
	{
		return writeThroughException == null;
	}


}
