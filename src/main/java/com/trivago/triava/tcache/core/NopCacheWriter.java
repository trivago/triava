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

/**
 * A CacheWriter that does nothing
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public class NopCacheWriter<K,V> implements CacheWriter<K, V>
{
	@Override
	public void write(Cache.Entry<? extends K, ? extends V> entry) throws CacheWriterException
	{
	}

	@Override
	public void writeAll(Collection<Cache.Entry<? extends K, ? extends V>> entries) throws CacheWriterException
	{
	}

	@Override
	public void delete(Object key) throws CacheWriterException
	{
	}

	@Override
	public void deleteAll(Collection<?> keys) throws CacheWriterException
	{
	}

}
