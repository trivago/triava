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

package com.trivago.triava.tcache.core;

import java.util.Comparator;

import com.trivago.triava.tcache.eviction.HolderFreezer;
import com.trivago.triava.tcache.eviction.TCacheHolder;

/**
 * Eviction interface, that can operate on 4 values: Meta data, key, value and a "frozen value". Values that could
 * change during an eviction run (like use count) must be "frozen" instead of used directly. 
 * Typical implementations will extend {@link FreezingEvictor} instead of directly implementing EvictionInterface,
 * as the former already implements the {@link #evictionComparator()}. 
 * 
 * @author cesken
 *
 * @param <K> Key class
 * @param <V> Value class
 */
public interface EvictionInterface<K, V>
{
	/**
	 * Returns the Comparator implementing the eviction policy. Elements to be evicted earlier have to be sorted to the
	 * front by the returned Comparator.
	 * 
	 * @return The comparator implementing the eviction policy
	 */
	Comparator<? super HolderFreezer<K, V>> evictionComparator();

	/**
	 * Returns a value that is required by the actual implementation for eviction. Implement this if you rely on a
	 * value that may change over the course of the eviction sorting process. Examples of changing values are the usage
	 * count or the last use timestamp. 
	 * <p>
	 * TODO For v0.9.5: First, rename this to getSnapshot() as it is clearer. Secondly, we could allow snapshots to be objects,
	 * and then we would have "public interface EvictionInterface&lt;K, V, SNAPSHOT&gt;"
	 * 
	 * @param key The key of the cache entry
	 * @param holder The holder, which includes the value and metadata
	 *  
	 * @return The snapshot of the value 
	 */
	long getFreezeValue(K key, TCacheHolder<V> holder);
	
	/**
	 * Called each time before an eviction cycle is being started.
	 */
	void beforeEviction();
	
	/**
	 * Called each time after an eviction cycle has ended.
	 */
	void afterEviction();
}
