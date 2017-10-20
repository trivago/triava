/*********************************************************************************
 * Copyright 2017-present trivago GmbH
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

package com.trivago.triava.tcache.util;

import com.trivago.triava.tcache.Cache;

public class NativeTriavaCacheWrapper<K, V> implements BasicCacheInterface<K, V> {
    final Cache<K, V> cache;

    public NativeTriavaCacheWrapper(Cache<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public V get(K key) {
        return cache.get(key);
    }

    @Override
    public V getAndPut(K key, V value) {
        return cache.getAndPut(key, value);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }
}
