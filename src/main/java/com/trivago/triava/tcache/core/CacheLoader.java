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

/**
 * A CacheLoader implements on-the-fly retrieval of values which are not yet in the Cache.  
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public interface CacheLoader<K, V>
{
	/**
	 * Computes or retrieves the value corresponding to {@code key}. Failures to load the
	 * value must throw an Exception.
	 *
	 * @param key the non-null key whose value should be loaded
	 * @return the value associated with {@code key}; <b>must not be null</b>
	 * @throws Exception The cause if loading the value has failed 
	 */
    V load(K key) throws Exception;
}
