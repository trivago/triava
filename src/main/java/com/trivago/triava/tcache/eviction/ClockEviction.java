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

package com.trivago.triava.tcache.eviction;

import com.trivago.triava.tcache.TCacheHolder;
import com.trivago.triava.time.TimeSource;

/**
 * Clock eviction based on last access time.
 * 
 * @author cesken
 *
 * @param <K> Key class
 * @param <V> Value class 
 */
public class ClockEviction<K,V> extends FreezingEvictor<K,V> 
{
	private static final long serialVersionUID = 6871216638625966432L;

	long lastTimestamp;
	TimeSource timeSource;
	
	
	/**
	 * @param timeSource A non-null TimeSource
	 * @throws NullPointerException If timeSource is null 
	 */
	ClockEviction(TimeSource timeSource)
	{
		this.timeSource = timeSource;
		lastTimestamp = timeSource.millis();
	}
	
	@Override
	public void afterEviction()
	{
		lastTimestamp = timeSource.millis();
	}
	
	/**
	 * @return A negative value if the entry was not used, a positive if it was used since the last eviction run.
	 */
	@Override
	public long getFreezeValue(K key, TCacheHolder<V> holder)
	{
		long lastAccess = holder.getLastAccessTime();
		if (lastAccess >= lastTimestamp)
		{
			// Was used => Sort back
			return 1;
		}
		return -1; // Not used => Sort front
	}
}
