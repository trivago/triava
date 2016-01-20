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

import java.util.concurrent.ConcurrentMap;

import com.trivago.triava.tcache.eviction.TCacheHolder;

/**
 * The basic interface for providing a storage backend that holds the values of  
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public interface StorageBackend<K,V>
{
	/**
	 * Returns a ConcurrentMap conforming to the configuration specified in the builder.
	 * The sizeFactor is a hint that the Map should be sized bigger due to the Cache implementation. For example
	 * if eviction starts at 95% fill rate, sizeFactor could be 1 - 1/0.95 = 0,0526316 
	 * 
	 * @param builder The configuration parameter for the Map
	 * @param sizeFactor Sizing factor to take into account
	 * @return An instance of the storage.
	 */
	ConcurrentMap<K, ? extends TCacheHolder<V>> createMap(Builder<K,V> builder, double sizeFactor);
}
