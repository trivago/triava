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

/**
 * A basic cache interface, with common methods of the triava native interface and the JCache interface.
 * This eases unit testing both interaces in the same way.
 * @param <K> The Key class
 * @param <V> The Value class
 */
public interface BasicCacheInterface<K, V> {
    V get(K key);

    V getAndPut(K key, V value);

    void put(K key, V value);
}
