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

import javax.cache.management.CacheMXBean;

import com.trivago.triava.tcache.eviction.Cache;

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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getValueType()
	{
		// TODO Auto-generated method stub
		return null;
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

}
