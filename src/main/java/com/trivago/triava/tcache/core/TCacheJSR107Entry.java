/*********************************************************************************
 * Copyright 2016-present trivago GmbH
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
 * tCache implementation of {@link javax.cache.Cache.Entry}. It is based on the native tCache entry
 * and can be unwrapped to it via unwrap(TCacheHolder.class)
 * 
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public class TCacheJSR107Entry<K, V> implements javax.cache.Cache.Entry<K, V>
{
	final K key;
	final V value;
	
	/**
	 * Creates an instance based on the native tCache entry plus the key.
	 * 
	 * @param key The key
	 * @param value The value
	 */
	public TCacheJSR107Entry(K key, V value)
	{
		this.key = key;
		this.value = value;
	}

	@Override
	public K getKey()
	{
		return key;
	}

	@Override
	public V getValue()
	{
		return value;
	}

	@Override
	public <T> T unwrap(Class<T> clazz)
	{
		if (clazz.isAssignableFrom(TCacheJSR107Entry.class))
		{
			@SuppressWarnings("unchecked")
			T holderCast = (T) this;
			return holderCast;
		}

		throw new IllegalArgumentException("Cannot unwrap to unsupported Class " + clazz);
	}
}
