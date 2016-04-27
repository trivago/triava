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
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import javax.cache.CacheManager;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;

import com.trivago.triava.tcache.TCacheFactory;

public class TCacheProvider implements CachingProvider
{
	//@GuardedBy("cacheManagersLock")
	Set<CacheManager> cacheManagers = new HashSet<>();
	// Explicit lock, as cacheManagers need a higher level locking than a ConcurrentSet 
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
		return getCacheManager(getDefaultURI(), getDefaultClassLoader(), null);
	}

	@Override
	public CacheManager getCacheManager(URI uri, ClassLoader classLoader)
	{
		return getCacheManager(uri, classLoader, null);
	}

	@Override
	public CacheManager getCacheManager(URI uri, ClassLoader classLoader, Properties properties)
	{
		System.out.println("getCacheManager( " + uri + ", cl=" + classLoader + ", props=" + properties);
		if (classLoader == null)
			classLoader = getDefaultClassLoader();
		
		synchronized (cacheManagersLock)
		{
			CacheManager cacheManager = findCacheManager(uri, classLoader);
			if (cacheManager == null)
			{
				if (properties == null)
					cacheManager = new TCacheFactory(uri, classLoader, this);
				else
					cacheManager = new TCacheFactory(uri, classLoader, properties, this);

				cacheManagers.add(cacheManager);
			}
			
			System.out.println("getCacheManager( " + uri + ", cl=" + classLoader + ", props=" + properties + " found cm=" + cacheManager + " open=" + !cacheManager.isClosed());
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
		synchronized (cacheManagersLock)
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
		}
		
		return null;
	}

	@Override
	public void close()
	{
		System.out.println("CacheManager.close()");
		synchronized (cacheManagersLock)
		{
			for( Iterator<CacheManager> it = cacheManagers.iterator(); it.hasNext(); )
			{
				CacheManager cacheManager = it.next();
				// Order must be close, remove
				cacheManager.close();
				it.remove();
				System.out.println("CacheManager.close() closed");
			}
		}
	}


	@Override
	public void close(ClassLoader classLoader)
	{
		System.out.println("CacheManager.close() cl=" + classLoader);
		synchronized (cacheManagersLock)
		{
			for( Iterator<CacheManager> it = cacheManagers.iterator(); it.hasNext(); )
			{
				CacheManager cacheManager = it.next();
				if (cacheManager.getClassLoader().equals(classLoader))
				{
					cacheManager.close();
					it.remove();
					System.out.println("CacheManager.close() closed cl=" + classLoader);
				}
			}
		}
	}

	
	@Override
	public void close(URI uri, ClassLoader classLoader)
	{
		System.out.println("CacheManager.close() cl=" + classLoader + ", uri=" + uri);
		synchronized (cacheManagersLock)
		{
			CacheManager cacheManager = findCacheManager(uri, classLoader);
			if (cacheManager != null)
			{
				cacheManager.close();
				cacheManagers.remove(cacheManager);
				System.out.println("CacheManager.close() closed cl=" + classLoader + ", uri=" + uri);
			}
		}
	}

	// TODO This MUST be package-private. Refactor 
	public void removeCacheManager(CacheManager cacheManager)
	{
		cacheManagers.remove(cacheManager);
		System.out.println("CacheManager.removeCacheManager() from close cm=" + cacheManager);
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
