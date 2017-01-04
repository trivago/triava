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
 * Freezes all statistics that may change
 * @author cesken
 *
 */
public class HolderFreezer<K, V>
{
	private static int tiebreakerVal = (byte)System.currentTimeMillis();

	private final K key;
	private final TCacheHolder<V> holder;
	private final long frozenValue;
	private final int tiebreaker = tiebreakerVal++;

	
	public HolderFreezer(K key, TCacheHolder<V> holder, long frozenValue)
	{
		this.key = key;
		this.holder = holder;
		this.frozenValue = frozenValue;
	}

	public K getKey()
	{
		return key;
	}

	public int getTiebreaker()
	{
		return tiebreaker;
	}

	public long getFrozenValue()
	{
		return frozenValue;
	}

	public TCacheHolder<V> getHolder()
	{
		return holder;
	}
}


