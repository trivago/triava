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

import java.net.URI;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.cache.CacheManager;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;

import com.trivago.triava.tcache.TCacheFactory;

public class TCacheProvider implements CachingProvider
{
	Set<CacheManager> cacheManagers = new HashSet<>();
	Object cacheManagersLock = new Object();
	
	public TCacheProvider()
	{
	}

	@Override
	public ClassLoader getDefaultClassLoader()
	{
		return TCacheFactory.standardFactory().getClassLoader();
	}

	@Override
	public URI getDefaultURI()
	{
		return TCacheFactory.standardFactory().getURI();
	}

	@Override
	public Properties getDefaultProperties()
	{
		return TCacheFactory.standardFactory().getProperties();
	}

	@Override
	public CacheManager getCacheManager()
	{
		return TCacheFactory.standardFactory();
	}

	@Override
	public CacheManager getCacheManager(URI uri, ClassLoader classLoader)
	{
		return getCacheManager(uri, classLoader, null);
	}

	@Override
	public CacheManager getCacheManager(URI uri, ClassLoader classLoader, Properties properties)
	{
		synchronized (cacheManagersLock)
		{
			CacheManager cacheManager = findCacheManager(uri, classLoader);
			if (cacheManager == null)
			{
				if (properties == null)
					cacheManager = new TCacheFactory(uri, classLoader);
				else
					cacheManager = new TCacheFactory(uri, classLoader, properties);
			}
			cacheManagers.add(cacheManager);
			return cacheManager;
		}
	}

	/**
	 * Returns the CacheManager for the given parameters if it is managed by this TCacheProvider 
	 * @param uri The Cache URI
	 * @param classLoader The Classloader
	 * 
	 * @return The CacheManager, or null if no matching one is managed by this TCacheProvider
	 */
	private CacheManager findCacheManager(URI uri, ClassLoader classLoader)
	{
		for (CacheManager cacheManager : cacheManagers)
		{
			if (! cacheManager.getURI().equals(uri))
				continue;
			if (! cacheManager.getClassLoader().equals(classLoader))
				continue;
			
			// Properties are to be ignored for equality check according to the JSR107 Spec
			return cacheManager;
		}
		
		return null;
	}

	@Override
	public void close()
	{
		for (CacheManager cacheManager : cacheManagers)
		{
			closeAndRemove(cacheManager);
		}
	}

	private void closeAndRemove(CacheManager cacheManager)
	{
		cacheManager.close();
		cacheManagers.remove(cacheManager);
	}

	@Override
	public void close(ClassLoader classLoader)
	{
		for (CacheManager cacheManager : cacheManagers)
		{
			if (cacheManager.getClassLoader().equals(classLoader))
				closeAndRemove(cacheManager);
		}
	}

	@Override
	public void close(URI uri, ClassLoader classLoader)
	{
		CacheManager cacheManager = findCacheManager(uri, classLoader);
		if (cacheManager != null)
			closeAndRemove(cacheManager);
	}

	@Override
	public boolean isSupported(OptionalFeature optionalFeature)
	{
		if (optionalFeature == OptionalFeature.STORE_BY_REFERENCE)
			return true;
		// TCK JSR107 Actually STORE_BY_VALUE is currently not supported  

		// Unknown / new feature => not supported 
		return false;
	}

}
