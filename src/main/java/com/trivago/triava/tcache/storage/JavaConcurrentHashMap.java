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

package com.trivago.triava.tcache.storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.StorageBackend;
import com.trivago.triava.tcache.eviction.TCacheHolder;

/**
 * Implements a storage that uses Java's ConcurrentHashMap.
 *  
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public class JavaConcurrentHashMap<K,V> implements StorageBackend<K, V>
{
	@Override
	public ConcurrentMap<K, TCacheHolder<V>> createMap(Builder<K,V> builder, double evictionMapSizeFactor)
	{
		double loadFactor = 0.75F;
		int requiredMapSize = (int) (builder.getExpectedMapSize() / loadFactor) + (int)evictionMapSizeFactor;
		return new ConcurrentHashMap<>(requiredMapSize, (float) loadFactor,
				builder.getMapConcurrencyLevel());
	}

}
