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
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.cache.CacheManager;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;

import com.trivago.triava.tcache.TCacheFactory;

public class TCacheProvider implements CachingProvider
{
	//@GuardedBy("cacheManagersLock")
	Set<CacheManager> cacheManagers = Collections.newSetFromMap(new ConcurrentHashMap<CacheManager, Boolean>());
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
		if (classLoader == null)
			classLoader = getDefaultClassLoader();
		if (uri == null)
			uri = getDefaultURI();
		
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
//		System.out.println("CacheManager.close()");
		synchronized (cacheManagersLock)
		{
			for( Iterator<CacheManager> it = cacheManagers.iterator(); it.hasNext(); )
			{
				CacheManager cacheManager = it.next();
				/**
				 *  The order must be close, remove. Reason: Otherwise we can could have 2 open caches with the same ID.
				 *  
				 *    Thread 1: it.remove();
				 *    // Now Cache is still open, but not in cacheManagers any longer
				 *    Thread 2: getCache()
				 *    // Creates a new CacheManager, as it is not found in  cacheManagers
				 *    // Now we have 2 identical open Cache managers
				 *    Thread 1: cacheManager.close();
				 *    // Now everything is fine again, but there was a time period with bad state
				 *    
				 *    Actually, due to locking with cacheManagersLock, this scenario will not happen. But for safety
				 *    after a possible refactoring or different locking, the order can get important.  
				 */
				cacheManager.close();
				// Hint: It may have happened, that cacheManager.close() already removed itself cacheManagers.
				// In that case the next line is a NOP. It is important though, that the underlying Set is a Concurrent Set.
				it.remove();
//				System.out.println("CacheManager.close() closed");
			}
		}
	}


	@Override
	public void close(ClassLoader classLoader)
	{
//		System.out.println("CacheManager.close() cl=" + classLoader);
		synchronized (cacheManagersLock)
		{
			for( Iterator<CacheManager> it = cacheManagers.iterator(); it.hasNext(); )
			{
				CacheManager cacheManager = it.next();
				if (cacheManager.getClassLoader().equals(classLoader))
				{
					cacheManager.close();
					// Hint: It may have happened, that cacheManager.close() already removed itrslef cacheManagers.
					// In that case the next line is a NOP. It is important though, that the underlying Set is a Concurrent Set.
					it.remove();
				}
			}
		}
	}

	
	@Override
	public void close(URI uri, ClassLoader classLoader)
	{
		synchronized (cacheManagersLock)
		{
			CacheManager cacheManager = findCacheManager(uri, classLoader);
			if (cacheManager != null)
			{
				cacheManager.close();
				// Hint: It may have happened, that cacheManager.close() already removed itself from cacheManagers.
				// In that case the next line is a NOP. It is important though, that the underlying Set is a Concurrent Set.
				cacheManagers.remove(cacheManager);
			}
		}
	}

	/**
	 * Removes the cache manager from this cache provider. Normal application code should not call this method, but call {@link TCacheFactory#close()} instead.
	 * <p>
	 * This method may be removed from the public API in V2.0
	 * @deprecated Use {@link TCacheFactory#close()}
	 * @param cacheManager The cache manager to remove
	 */
	public void removeCacheManager0(CacheManager cacheManager)
	{
		synchronized (cacheManagersLock)
		{
			cacheManagers.remove(cacheManager);
//			System.out.println("CacheManager.removeCacheManager() from close cm=" + cacheManager);
		}
	}
	
	@Override
	public boolean isSupported(OptionalFeature optionalFeature)
	{
		if (optionalFeature == OptionalFeature.STORE_BY_REFERENCE)
			return true;

		// Unknown / new feature => not supported 
		return false;
	}

}
