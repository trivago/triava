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

import javax.cache.configuration.Configuration;
import javax.cache.management.CacheMXBean;

import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.eviction.TCacheJSR107;

public class TCacheConfigurationBean<K,V> implements CacheMXBean
{
	final Cache<K, V> tcache;
	
	public TCacheConfigurationBean(Cache<K, V> tcache)
	{
		this.tcache = tcache;
	}

	@Override
	public String getKeyType()
	{
		Configuration<K,V> config = getCacheConfiguration();
		return config.getKeyType().getCanonicalName();
	}

	@Override
	public String getValueType()
	{
		Configuration<K,V> config = getCacheConfiguration();
		return config.getValueType().getCanonicalName();
	}

	@Override
	public boolean isReadThrough()
	{
		return false; // tCache is single-level Cache => always false
	}

	@Override
	public boolean isWriteThrough()
	{
		return false; // tCache is single-level Cache => always false
	}

	@Override
	public boolean isStoreByValue()
	{
		return tcache.isStoreByValue();
	}

	@Override
	public boolean isStatisticsEnabled()
	{
		return tcache.isStatisticsEnabled();
	}

	@Override
	public boolean isManagementEnabled()
	{
		return tcache.isManagementEnabled();
	}


	/**
	 * Returns the Configuration object of the corresponding Cache.
	 * @return
	 */
	private Configuration<K,V> getCacheConfiguration()
	{
		TCacheJSR107<K, V> jsr107cache = tcache.jsr107cache();
		@SuppressWarnings("unchecked")
		Configuration<K,V> config = (Configuration<K,V>)jsr107cache.getConfiguration(Configuration.class);
		return config;
	}
}
