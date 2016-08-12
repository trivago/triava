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

package com.trivago.triava.tcache.storage;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import javax.cache.CacheException;

import com.trivago.triava.tcache.CacheWriteMode;
import com.trivago.triava.tcache.util.Serializing;

/**
 * A concurrent Map that serializes and de-serializes keys. You should only instanciate this if you need serialized keys.
 * <p>
 * Using this class can drastically reduce performance as keys are serialized or deserialized on each operation. Calling
 * bulk methods is also not very efficient, as all keys need to be converted. Worst method to call is entrySet()
 *   
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public class ConcurrentKeyDeserMap<K,V> implements ConcurrentMap<K, V>
{
	final ConcurrentMap<ByteArray,V> backingMap;
	final CacheWriteMode writeMode;
	
	public ConcurrentKeyDeserMap(ConcurrentMap<ByteArray,V> backingMap, CacheWriteMode writeMode)
	{
		this.backingMap = backingMap;
		this.writeMode = writeMode;
	}
	
	
	@Override
	public int size()
	{
		return backingMap.size();
	}

	@Override
	public boolean isEmpty()
	{
		return backingMap.isEmpty();
	}

	@Override
	public boolean containsKey(Object key)
	{
		return backingMap.containsKey(serialize(key));
	}

	@Override
	public boolean containsValue(Object value)
	{
		return backingMap.containsValue(value);
	}

	@Override
	public V get(Object key)
	{
		return backingMap.get(serialize(key));
	}

	@Override
	public V put(K key, V value)
	{
		return backingMap.put(serialize(key), value);
	}

	@Override
	public V remove(Object key)
	{
		return backingMap.remove(serialize(key));
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m)
	{
		Map<ByteArray, V> map = new HashMap<>(m.size()); 
		for (java.util.Map.Entry<? extends K, ? extends V> entry : m.entrySet())
		{
			map.put(serialize(entry.getKey()), entry.getValue());
		}
		backingMap.putAll(map);
	}

	@Override
	public void clear()
	{
		backingMap.clear();
	}

	@Override
	public Set<K> keySet()
	{
		Set<K> keySet = new HashSet<>(backingMap.size());
		for (ByteArray key : backingMap.keySet())
		{
			keySet.add(deserialize(key));
		}
		return keySet;
	}

	@Override
	public Collection<V> values()
	{
		return backingMap.values();
	}

	/**
	 * Note that this method is quite inefficient. It creates a whole new Map and then returns the entrySet from it.
	 */
	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet()
	{
		Map<K, V> map = new HashMap<>(backingMap.size()); 
		for (java.util.Map.Entry<ByteArray, V> entry : backingMap.entrySet())
		{
			map.put(deserialize(entry.getKey()), entry.getValue());
		}
		
		return map.entrySet();
	}

	@Override
	public V putIfAbsent(K key, V value)
	{
		return backingMap.putIfAbsent(serialize(key), value);
	}

	@Override
	public boolean remove(Object key, Object value)
	{
		return backingMap.remove(serialize(key), value);
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue)
	{
		return backingMap.replace(serialize(key), oldValue, newValue);
	}

	@Override
	public V replace(K key, V value)
	{
		return backingMap.replace(serialize(key), value);
	}


	private K deserialize(ByteArray key)
	{
		try
		{
			@SuppressWarnings("unchecked")
			K fromBytearray = (K)Serializing.fromBytearray((byte[])(key.bytes));
			return fromBytearray;
		}
		catch (ClassNotFoundException | IOException e)
		{
			// Nearly impossible, as we serialized the data ourselves
			throw new CacheException("Cannot deserialize key with length=" + key.bytes.length, e);
		}
	}
	
	private ByteArray serialize(Object key)
	{
		if (key == null)
		{
			throw new NullPointerException("key must not be null");
		}

		try
		{
			return new ByteArray(Serializing.toBytearray(key));
		}
		catch (IOException e)
		{
			throw new CacheException("Cannot serialize key class of type: " + key.getClass().getName() , e);
		}
	}





}
