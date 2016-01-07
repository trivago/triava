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

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.util.CacheSizeInfo;
import com.trivago.triava.tcache.util.ObjectSizeCalculatorInterface;

/**
 * The TCacheFactory allows to create Cache instances via {@link #builder()}, and also supplies administrative methods for the
 * managed caches, like shutting down all registered Caches. The preferred way of obtaining a TCacheFactory
 * instance for application code is a call to {@link #standardFactory()}. Library code should instantiate a new
 * TCacheFactory instance, so it can manage its own Cache collection.
 * 
 * @author cesken
 * @since 2015-03-10
 *
 */
public class TCacheFactory implements Closeable
{
	final CopyOnWriteArrayList<Cache<?, ?>> CacheInstances = new CopyOnWriteArrayList<>();
	final Object factoryLock = new Object();
	boolean closed = false;
	final private URI uri;
	AtomicInteger uriSeqno = new AtomicInteger();
	final ClassLoader classloader;
	Properties properties = new Properties();

	static final TCacheFactory standardFactory = new TCacheFactory();

	public TCacheFactory()
	{
		classloader = Thread.currentThread().getContextClassLoader();
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
		return standardFactory;
	}

	/**
	 * Returns a Builder 
	 * @param <K> The Key type
	 * @param <V> The Value type
	 * @return A Builder
	 */
	public <K, V> Builder<K, V> builder()
	{
		return new Builder<>(this);
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
					// TODO cesken JSR-107 mandates the order clear(), close(), but this means, that new entries
					// could get added between clear and close. Thus my shutdown() does a close(), clear() sequence.
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
	public void close()
	{
		for (Cache<?, ?> cache : CacheInstances)
		{
			cache.shutdown();
		}
		CacheInstances.clear();
		closed = true;	
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

	private void assertNotClosed()
	{
		if (closed)
		{
			throw new IllegalStateException("CacheManager " + uri + " is already closed"); // JSR-107 compliance
		}
	}
	

}
