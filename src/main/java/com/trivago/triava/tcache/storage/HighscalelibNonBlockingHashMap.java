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

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentMap;

import com.trivago.triava.tcache.TCacheHolder;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.StorageBackend;

/**
 * Implements a storage that uses the Highscale libs. It is only implemented for performance tests.
 * The instantiation is done fully via reflection, for avoiding compile-time dependencies to 3rd-party libraries.
 *  
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public class HighscalelibNonBlockingHashMap<K,V> implements StorageBackend<K, V>
{
	@Override
	public ConcurrentMap<K, TCacheHolder<V>> createMap(Builder<K,V> builder, double evictionMapSizeFactor)
	{
		try
		{
			Class<?> cacheClass = Class.forName("org.cliffc.high_scale_lib.NonBlockingHashMap");
	
			// Create the instance by reflection, for avoiding dependencies to libraries we do have (yet) as dependency.
			// This way we can compare the TCache performance using different storages (CHM vs LocalCache vs ...)
			Constructor<?> cons = cacheClass.getDeclaredConstructor(int.class);
			
			double loadFactor = 0.75F;
			int requiredMapSize = (int) (builder.getExpectedMapSize() / loadFactor) + (int)evictionMapSizeFactor;
	
			@SuppressWarnings({ "unchecked", "rawtypes" })
			ConcurrentMap<K, TCacheHolder<V>> inst = (ConcurrentMap) cons.newInstance(requiredMapSize);
			return inst;
		}
		catch (Exception exc)
		{
			throw new RuntimeException("Cannot create map for HashImplementation=" + builder.getHashImplementation(), exc);
		}
		
	}

}
