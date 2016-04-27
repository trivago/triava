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

package com.trivago.triava.tcache;

import java.io.Closeable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import javax.cache.spi.CachingProvider;

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.Builder.PropsType;
import com.trivago.triava.tcache.core.TCacheProvider;
import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.eviction.TCacheJSR107;
import com.trivago.triava.tcache.util.CacheSizeInfo;
import com.trivago.triava.tcache.util.ObjectSizeCalculatorInterface;

/**
 * The TCacheFactory allows to create Cache instances via calls to {@link #builder()}, and also supplies administrative methods for the
 * managed caches, like shutting down all registered Caches. The preferred way of obtaining a TCacheFactory
 * instance for application code is a call to {@link #standardFactory()}. Library code should instantiate a new
 * TCacheFactory instance, so it can manage its own Cache collection.
 * An alternative way is to use the JCache API, and obtain an instance via {@link javax.cache.spi.CachingProvider}.getCacheManager() calls.
 * 
 * @author cesken
 * @since 2015-03-10
 *
 */
public class TCacheFactory implements Closeable, CacheManager
{
	final CopyOnWriteArrayList<Cache<?, ?>> CacheInstances = new CopyOnWriteArrayList<>();
	final Object factoryLock = new Object();
	boolean closed = false;
	final private URI uri;
	AtomicInteger uriSeqno = new AtomicInteger();
	final ClassLoader classloader;
	final Properties properties;
	final TCacheProvider cachingProvider;

	static TCacheFactory standardFactory = null;

	public TCacheFactory()
	{
		classloader = Thread.currentThread().getContextClassLoader();
		properties = defaultProperties();

		int seqno = uriSeqno.incrementAndGet();
		String uriString = "tcache:/manager-" + seqno;
		try
		{
			uri = new URI(uriString);
		}
		catch (URISyntaxException e)
		{
			throw new AssertionError("URI cannot be created: " + uriString, e);
		}
		
		cachingProvider = null; // A standalone Cache without CachingProvider
	}
	
	public TCacheFactory(URI uri, ClassLoader classLoader, TCacheProvider cachingProvider)
	{
		this.classloader = classLoader;
		this.uri = uri;
		properties = defaultProperties();
		this.cachingProvider = cachingProvider;
	}

	public TCacheFactory(URI uri, ClassLoader classLoader, Properties properties, TCacheProvider cachingProvider)
	{
		this.classloader = classLoader;
		this.uri = uri;
		this.properties = new Properties(properties);
		this.cachingProvider = cachingProvider;
	}

	/**
	 * Returns the standard factory. The factory can produce Builder instances via {@link #builder()}.
	 * <br>
	 * Library code should not use this method, but create a new TCacheFactory
	 * instance for managing its own Cache collection.
	 * 
	 * @return The standard cache factory.
	 */
	public static TCacheFactory standardFactory()
	{
		TCacheFactory sf = standardFactory;
		if (sf == null || sf.isClosed())
		{
			sf = new TCacheFactory();
			standardFactory = sf;
		}
		return sf;
	}

	private Properties defaultProperties()
	{
		// A new builder holds the default properties
		Builder<?, ?> builder = new Builder<>(null);
		return builder.asProperties(PropsType.CacheManager);
	}


	/**
	 * Returns a Builder 
	 * @param <K> The Key type
	 * @param <V> The Value type
	 * @return A Builder
	 */
	public <K, V> Builder<K, V> builder()
	{
		return new Builder<K,V>(this);
	}

	/**
	 * Registers a Cache to this factory. Registered caches will be used for bulk operations like
	 * {@link #shutdownAll()}.
	 * 
	 * @param cache The Cache to register
	 *  @throws IllegalStateException If a cache with the same id is already registered in this factory.
	 */
	public void registerCache(Cache<?, ?> cache)
	{
		assertNotClosed();
		
		String id = cache.id();
		synchronized (factoryLock)
		{
			for (Cache<?, ?> registeredCache : CacheInstances)
			{
				if (registeredCache.id().equals(id))
				{
					throw new IllegalStateException("Cache with the same id is already registered: " + id);
				}
			}

			CacheInstances.add(cache);			
			// Hint: "cache" cannot escape. It is safely published, as it is put in a concurrent collection
		}
	}

	/**
	 * Destroys a specifically named and managed {@link Cache}. Once destroyed a new {@link Cache} of the same
	 * name but with a different Configuration may be configured.
	 * <p>
	 * This is equivalent to the following sequence of method calls:
	 * <ol>
	 * <li>{@link Cache#clear()}</li>
	 * <li> Cache#close()</li>
	 * </ol>
	 * followed by allowing the name of the {@link Cache} to be used for other {@link Cache} configurations.
	 * <p>
	 * From the time this method is called, the specified {@link Cache} is not available for operational use.
	 * An attempt to call an operational method on the {@link Cache} will throw an
	 * {@link IllegalStateException}.
	 *
	 * JSR-107 does not specify behavior if the given cacheName is not in the CacheManager (this TCacheFactory).
	 * This implementations chooses to ignore the call and return without Exception.
	 * 
	 * @param cacheName
	 *            the cache to destroy
	 * @throws IllegalStateException
	 *             if the TCacheFactory
	 *
	 *             {@link #isClosed()}
	 * @throws NullPointerException
	 *             if cacheName is null
	 * @throws SecurityException
	 *             when the operation could not be performed
	 *
	 *             due to the current security settings
	 */
	public void destroyCache(String cacheName)
	{
		if (cacheName == null)
		{
			throw new NullPointerException("cacheName is null"); // JSR-107 compliance
		}
		assertNotClosed();
		
		synchronized (factoryLock)
		{
			// Cannot loop using an Iterator and remove(), as the CopyOnWriteArrayList Iterator does not support remove()
			int index = 0;
			for (Cache<?, ?> registeredCache : CacheInstances)
			{
				if (registeredCache.id().equals(cacheName))
				{
					// JSR-107 mentions the order clear(), close(), but this means, that new entries
					// could get added between clear and close. Thus my shutdown() does a close(), clear() sequence.
					// For practical purposes it should be the same, and also conform to the Cache-TCK.
					registeredCache.shutdown(); 
					CacheInstances.remove(index);
					break;
				}
				index++;
			}
		}
	}

	public URI getURI()
	{
		return uri;
	}
	
	public ClassLoader getClassLoader()
	{
		return classloader;
	}

	public Properties getProperties()
	{
		return properties;
	}

	
	/**
	* Closes the TCacheFactory.
	* <p>
	* For each {@link Cache} managed by the TCacheFactory, the
	* Cache#close() method will be invoked, in no guaranteed order.
	* <p>
	* If a Cache#close() call throws an exception, the exception will be
	* ignored.
	* <p>
	* After executing this method, the {@link #isClosed()} method will return
	* <code>true</code>.
	* <p>
	* All attempts to close a previously closed TCacheFactory will be
	* ignored.
	*
	* @throws SecurityException when the operation could not be performed due to the
	*
	 current security settings
	*/
	@Override
	public void close()
	{
		for (Cache<?, ?> cache : CacheInstances)
		{
			cache.shutdown();
		}
		CacheInstances.clear();
		if (cachingProvider == null)
		{
			// Standalone, deprecated
			closed = true;				
		}
		else
		{
			// From Caching provider
			cachingProvider.removeCacheManager(this);
		}
	}
	

	/**
	 * Shuts down all Cache instances, which were registered via {@link #registerCache(Cache)}. It waits until
	 * all cleaners have stopped.
	 * @deprecated Please migrate to the JSR-107 compatible method {@link #close()}
	 */
	public void shutdownAll()
	{
		close();
	}

	@Override
	public boolean isClosed()
	{
		return closed;
	}
	
	/**
	 * Reports size of all Cache instances, which were registered via {@link #registerCache(Cache)}. Using
	 * this method can create high load, and may require particular permissions, depending on the used object
	 * size calculator.
	 * <p>
	 * <b>USE WITH CARE!!!</b>
	 * 
	 * @param objectSizeCalculator An implementation that can calculate the deep size of an Object tree 
	 * @return A map with the cacheName as key and the CacheSizeInfo as value
	 */
	public Map<String, CacheSizeInfo> reportAllCacheSizes(ObjectSizeCalculatorInterface objectSizeCalculator)
	{
		Map<String, CacheSizeInfo> infoMap = new HashMap<>();
		for (Cache<?, ?> cache : CacheInstances)
		{
			infoMap.put(cache.id(), cache.reportSize(objectSizeCalculator));
		}
		return infoMap;
	}

	/**
	 * Returns the list of Caches that have been registered via {@link #registerCache(Cache)}.
	 * 
	 * @return The cache list
	 */
	public List<Cache<?, ?>> instances()
	{
		return new ArrayList<>(CacheInstances);
	}

	/**
	 * Throws IllegalStateException if this TCacheFactory is closed. Otherwise returns without error.
	 * 
	 * @throws IllegalStateException if this TCacheFactory is closed
	 */
	private void assertNotClosed()
	{
		if (closed)
		{
			throw new IllegalStateException("CacheManager " + uri + " is already closed"); // JSR-107 compliance
		}
	}

	@Override
	public <K, V, C extends Configuration<K, V>> javax.cache.Cache<K, V> createCache(String cacheName, C configuration)
			throws IllegalArgumentException
	{
		assertNotClosed();
		if (cacheName == null)
		{
			throw new NullPointerException("cacheName is null"); // JSR-107 compliance
		}

		// Create a new Builder, this will copy the configuration.
		Builder<K,V> builder = new Builder<>(this, configuration);
		builder.setId(cacheName);
		Cache<K, V> tcache = builder.build();
		return tcache.jsr107cache();
	}

	@Override
	public void enableManagement(String cacheName, boolean enable)
	{
		Cache<?, ?> tCache = getTCacheWithChecks(cacheName);
		if (tCache != null)
		{
			tCache.enableManagement(enable);
		}
		
	}

	@Override
	public void enableStatistics(String cacheName, boolean enable)
	{
		Cache<?, ?> tCache = getTCacheWithChecks(cacheName);
		if (tCache != null)
		{
			tCache.enableStatistics(enable);
		}
	}
	
	/**
	 * Returns the Cache with the given name
	 * 
	 * @param cacheName
	 * @return A reference to the Cache, or null if it is not present in this CacheManager
	 * 
	 * @throws IllegalStateException - if the CacheManager or Cache isClosed()
	 * @throws NullPointerException - if cacheName is null
	 * @throws SecurityException - when the operation could not be performed due to the current security settings
	 */
	private Cache<?, ?> getTCacheWithChecks(String cacheName)
	{
		if (isClosed())
		{
			throw new IllegalStateException();
		}
		if (cacheName == null)
		{
			throw new NullPointerException();
		}
		
		return getTCache(cacheName);
	}
	
	public Cache<?, ?> getTCache(String cacheName)
	{
		for (Cache<?, ?> registeredCache : CacheInstances)
		{
			if (registeredCache.id().equals(cacheName))
			{
				return registeredCache;
			}
		}

		return null;
	}

	@Override
	public <K, V> javax.cache.Cache<K, V> getCache(String cacheName, Class<K> keyClass, Class<V> valueClass)
	{
		for (Cache<?, ?> registeredCache : CacheInstances)
		{
			if (registeredCache.id().equals(cacheName))
			{
				javax.cache.Cache<?, ?> jsr107cacheTypeless = registeredCache.jsr107cache();
				TCacheJSR107<?, ?> jsr107cache2 = registeredCache.jsr107cache();
				@SuppressWarnings("unchecked")
				Configuration<?,?> cc = jsr107cache2.getConfiguration(Configuration.class);
				
				// Type check for key
				if (keyClass != null)
				{
					if ( cc.getKeyType() != keyClass)
					{
						throw new IllegalArgumentException("Key class mismatch. cache=" + cacheName + ", requestedClass=" + keyClass.getCanonicalName() + ", cacheClass=" + cc.getKeyType());
					}
				}
				
				// Type check for value
				if (valueClass != null)
				{
					if ( cc.getValueType() != valueClass)
					{
						throw new IllegalArgumentException("Value class mismatch. cache=" + cacheName + ", requestedClass=" + valueClass.getCanonicalName() + ", cacheClass=" + cc.getValueType());
					}
				}
				
				@SuppressWarnings("unchecked")
				javax.cache.Cache<K, V> jsr107cache = (javax.cache.Cache<K, V>) jsr107cacheTypeless;
				return jsr107cache;
			}
		}

		return null;
	}

	@Override
	public <K, V> javax.cache.Cache<K, V> getCache(String cacheName)
	{
		return getCache(cacheName, null, null);
	}

	@Override
	public Iterable<String> getCacheNames()
	{
		List<String> cacheNames = new ArrayList<>(CacheInstances.size());
		for (Cache<?, ?> cache : CacheInstances)
		{
			cacheNames.add(cache.id());
		}
		return cacheNames;
	}

	@Override
	public CachingProvider getCachingProvider()
	{
		return cachingProvider;
	}

	@Override
	public <T> T unwrap(Class<T> clazz)
	{
		if (!(clazz.isAssignableFrom(TCacheFactory.class)))
			throw new IllegalArgumentException("Cannot unwrap CacheManager to unsupported Class " + clazz);
		
		@SuppressWarnings("unchecked")
		T thisCasted = (T)this;
		return thisCasted;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((classloader == null) ? 0 : classloader.hashCode());
		result = prime * result + uri.hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TCacheFactory other = (TCacheFactory) obj;
		if (classloader != other.classloader)
			return false;
		if (!uri.equals(other.uri))
			return false;
		
		return true;
	}
}
