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

import java.util.Collection;

import javax.cache.Cache;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;

public class CacheWriterWrapper<K,V> implements CacheWriter<K,V>
{
	private CacheWriter<K, V> cacheWriter;
//	private boolean isAsync;

	public CacheWriterWrapper(CacheWriter<? super K, ? super V> cw, boolean isAsync)
	{
		@SuppressWarnings("unchecked")
		CacheWriter<K, V> cw2 = (CacheWriter<K, V>) cw;
		this.cacheWriter = cw2;
//		this.isAsync = isAsync;
	}
	
	@Override
	public void write(Cache.Entry<? extends K, ? extends V> entry) throws CacheWriterException
	{
		cacheWriter.write(entry);
	}

	@Override
	public void writeAll(Collection<Cache.Entry<? extends K, ? extends V>> entries) throws CacheWriterException
	{
		cacheWriter.writeAll(entries);
	}

	@Override
	public void delete(Object key) throws CacheWriterException
	{
		cacheWriter.delete(key);
	}

	@Override
	public void deleteAll(Collection<?> keys) throws CacheWriterException
	{
		cacheWriter.deleteAll(keys);
	}


}
