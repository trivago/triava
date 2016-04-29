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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import javax.cache.CacheException;
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
//		properties = defaultProperties();
		properties = new Properties();

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
//		properties = defaultProperties();
		properties = new Properties();
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
					throw new CacheException("Cache with the same id is already registered: " + id);
				}
			}

			CacheInstances.add(cache);			
			// Hint: "cache" cannot escape. It is safely published, as it is put in a concurrent collection
		}
	}

	@Override
	public void destroyCache(String cacheName)
	{
		assertNotClosed();

		if (cacheName == null)
		{
			throw new NullPointerException("cacheName is null"); // JSR-107 compliance
		}

		/*
		 * JSR-107 does not specify behavior if the given cacheName is not in the CacheManager (this TCacheFactory).
		 * This implementations chooses to ignore the call and return without Exception.
		 */
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

	@Override
	public URI getURI()
	{
		return uri;
	}
	
	@Override
	public ClassLoader getClassLoader()
	{
		return classloader;
	}

	@Override
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
			closed = true;
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
		assertNotClosed();

		Cache<?, ?> tCache = getTCacheWithChecks(cacheName);
		if (tCache != null)
		{
			tCache.enableManagement(enable);
		}
		
	}

	@Override
	public void enableStatistics(String cacheName, boolean enable)
	{
		assertNotClosed();

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
		assertNotClosed();
		throwNpeOnNull(keyClass, "keyClass");
		throwNpeOnNull(valueClass, "valueClass");
		
		return getCacheInternal(cacheName, keyClass, valueClass, true);
	}

	private void throwNpeOnNull(Object obj, String message)
	{
		if (obj == null)
			throw new NullPointerException(message + " must not be null");
	}

	@Override
	public <K, V> javax.cache.Cache<K, V> getCache(String cacheName)
	{
		assertNotClosed();

		return getCacheInternal(cacheName, Object.class, Object.class, false);
	}
	
	/**
	 * Internal helper method for the two public JSR107 getCache() methods. It does the required type checks
	 * and throws the two different Exceptions that JSR107 mandates for typed and untyped caches.
	 * <p>
	 * Implementation note: TCache internally represents untyped caches with the type class Object.class. This
	 * goes along well with the JSR107, as it is allowed to return caches bound to (Object.class,Object.class) for untyped Caches.
	 * 
	 *  
	 * @param cacheName Cache name
	 * @param keyClass key class
	 * @param valueClass value class
	 * @param isTypedCache true if a typed Cache, false if an untyped Cache should be returned
	 * @return A Cache for the parameters
	 * @throws ClassCastException when the found cache is <b>untyped</b> or the types of the found cache does not match the requested classes. Only thrown for isTypedCache==true
	 * @throws IllegalArgumentException when the found cache is <b>typed</b>. Only thrown for isTypedCache==false
	 */
	private <K, V> javax.cache.Cache<K, V> getCacheInternal(String cacheName, Class<?> keyClass, Class<?> valueClass, boolean isTypedCache)
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
						if (isTypedCache)
							throw new ClassCastException("Key class mismatch. cache=" + cacheName + ", requestedClass=" + keyClass.getCanonicalName() + ", cacheClass=" + cc.getKeyType());
						else
							throw new IllegalArgumentException("Key class mismatch. cache=" + cacheName + ", requestedClass=" + "null" + ", cacheClass=" + cc.getKeyType());
					}
				}
				
				// Type check for value
				if (valueClass != null)
				{
					if ( cc.getValueType() != valueClass)
					{
						if (isTypedCache)
							throw new ClassCastException("Value class mismatch. cache=" + cacheName + ", requestedClass=" + valueClass.getCanonicalName() + ", cacheClass=" + cc.getValueType());
						else
							throw new IllegalArgumentException("Value class mismatch. cache=" + cacheName + ", requestedClass=" + "null" + ", cacheClass=" + cc.getValueType());
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
	public Iterable<String> getCacheNames()
	{
		// The following assertNotClosed() complies to the JSR107 specification, but it makes a TCK check fail.
		// This is due to a bug in the TCK, which we addressed in https://github.com/jsr107/jsr107spec/issues/342

//		assertNotClosed(); // TODO Add this again, when the JSR107 TCK has been fixed

		List<String> cacheNames = new ArrayList<>(CacheInstances.size());
		for (Cache<?, ?> cache : CacheInstances)
		{
			cacheNames.add(cache.id());
		}
		
		// JSR107 mandates that the collection is immutable. This means it must also be unmodifiable (and the TCK checks that).
		return Collections.unmodifiableList(cacheNames);
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
