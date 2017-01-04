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

/**
 * LRU eviction based on last access time
 * 
 * @author cesken
 *
 * @param <K> Key class
 * @param <V> Value class 
 */
public class LRUEviction<K,V> extends FreezingEvictor<K,V> 
{
	/**
	 * @return The last access time
	 */
	@Override
	public long getFreezeValue(K key, TCacheHolder<V> holder)
	{
		return holder.getLastAccess();
	}
}
