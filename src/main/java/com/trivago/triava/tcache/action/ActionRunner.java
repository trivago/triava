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

import javax.cache.integration.CacheWriter;

import com.trivago.triava.tcache.core.NopCacheWriter;
import com.trivago.triava.tcache.event.ListenerCollection;
import com.trivago.triava.tcache.statistics.StatisticsCalculator;

public abstract class ActionRunner<K,V>
{
	final CacheWriter<K, V> cacheWriter;
	final ListenerCollection<K, V> listeners;
	final StatisticsCalculator stats;

	
	ActionRunner(ActionContext<K,V> actionContext)
	{
		// actionContext. It is currently taken directly from the Cache
	        CacheWriter<K, V> writer = actionContext.cacheWriter();
	        if (writer instanceof NopCacheWriter)
	        {
	            this.cacheWriter = null;
	        }
	        else
	        {               
	            this.cacheWriter = actionContext.cacheWriter();
	        }
		this.listeners = actionContext.listeners();
		this.stats = actionContext.statisticsCalculator();		
	}
	
	public abstract boolean preMutate(Action<K,V,?> action, Object arg);
	public boolean preMutate(Action<K,V,?> action)
	{
		return preMutate(action, null);
	}
	
	public void postMutate(Action<K,V,?> action)
	{
		postMutate(action, null);
	}
	public abstract void postMutate(Action<K,V,?> action, Object arg);
}
