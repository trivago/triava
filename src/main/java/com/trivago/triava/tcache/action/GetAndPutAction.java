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

package com.trivago.triava.tcache.action;

import javax.cache.event.EventType;

import com.trivago.triava.tcache.statistics.StatisticsCalculator;
import com.trivago.triava.tcache.util.ChangeStatus;

/**
 * An Action that has both Get and Put characteristics, like getAndPut(), and the 3-arg replace().
 * Should be renamed to GetPutAction.
 *  
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 * @param <W> The return type of the writeThroughImpl (unused)
 */
public class GetAndPutAction<K, V, W> extends PutAction<K, V, W>
{

	public GetAndPutAction(K key, V value, EventType eventType)
	{
		super(key, value, eventType, true);
	}

	@Override
	void statisticsImpl(ActionRunner<K, V> actionRunner, Object arg)
	{
		StatisticsCalculator stats = actionRunner.stats;
		ChangeStatus changeStatus = (ChangeStatus) arg;
		if (changeStatus == null)
			return;
		
//		System.out.println("actionRunnter " + actionRunner + ", cs=" +changeStatus);
		switch (changeStatus)
		{
			case CHANGED:
				stats.incrementHitCount();
//				stats.incrementPutCount(); // cache.putToMap() still does it. Thus do not increment here
				break;
			case CREATED:
				stats.incrementMissCount();
//				stats.incrementPutCount(); // cache.putToMap() still does it. Thus do not increment here
				break;
			case UNCHANGED:
			case CAS_FAILED_EQUALS:
				break;
		

		}
	}
}
