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

import java.util.HashMap;
import java.util.Map;

import javax.cache.integration.CacheLoaderException;

/**
 * An abstract implementation of {@link javax.cache.integration.CacheLoader}, that implements {@link #loadAll(Iterable)}
 * in a trivial fashion by iterating all keys and sequentially calling {@link #load(Object)}.
 *   
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public abstract class CacheLoader<K, V> implements javax.cache.integration.CacheLoader<K, V>
{
    @Override
    public Map<K, V> loadAll(Iterable<? extends K> keys) throws CacheLoaderException
    {
    	Map<K, V> entries = new HashMap<>();
    	for (K key : keys)
    	{
    		entries.put(key, load(key));
    	}
    	
    	return entries;
    }
}
