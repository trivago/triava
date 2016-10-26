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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.cache.Cache.Entry;
import javax.cache.CacheException;
import javax.cache.configuration.MutableConfiguration;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Test;

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.eviction.TCacheJSR107;
import com.trivago.triava.tcache.statistics.TCacheStatistics;

/**
 * Tests for tCache operations, and behavior like eviction
 * @author cesken
 *
 */
public class CacheTest
{
	private static final int DEFAULT_CAPACITY = 10;
	private static final int maxIdleTime = 100;
	private static final int maxCacheTime = 1000;
	private Cache<String, Integer> cache;

	boolean runAcceptanceTests = false;

	
	@AfterClass
	public static void tearDown()
	{
		// Not closing, as there is another unit test that also uses the TCacheFactory.standardFactory() 
		//TCacheFactory.standardFactory().close();
	}
	
	@Before
	public void setUpEach()
	{
		cache = TCacheFactory.standardFactory().<String,Integer>builder().setExpectedMapSize(DEFAULT_CAPACITY).setMaxCacheTime(maxCacheTime, TimeUnit.SECONDS).setMaxIdleTime(maxIdleTime, TimeUnit.SECONDS) .build();
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
	}
	
	@Test
	public void testLimitToMaxInt()
	{
		assertEquals(0, limitToPositiveInt(0));
		assertEquals(1, limitToPositiveInt(1));
		assertEquals(0, limitToPositiveInt(-1));
		assertEquals(0, limitToPositiveInt(Integer.MIN_VALUE));
		assertEquals(0, limitToPositiveInt(Long.MIN_VALUE));
		assertEquals(Integer.MAX_VALUE, limitToPositiveInt(Integer.MAX_VALUE));
		assertEquals(Integer.MAX_VALUE, limitToPositiveInt(((long)Integer.MAX_VALUE)+1));
		assertEquals(Integer.MAX_VALUE, limitToPositiveInt(((long)Integer.MAX_VALUE)+2));
		assertEquals(Integer.MAX_VALUE, limitToPositiveInt(Long.MAX_VALUE));
	}
	
	@Test
	public void testNullKey()
	{
		Builder<String, Integer> cacheB = TCacheFactory.standardFactory().builder();
		cacheB.setId("testNullKey");
		cacheB.setCacheWriteMode(CacheWriteMode.Serialize);
		Cache<String, Integer> cache1 = cacheB.build();
		
		try
		{
			cache1.containsKey(null);
			fail("Must throw exception");
		}
		catch (NullPointerException npe)
		{
			// good
		}
	}
	
	@Test
	public void testSerializing()
	{
		cache = TCacheFactory.standardFactory().<String,Integer>builder()
				.setCacheWriteMode(CacheWriteMode.Serialize).build();
		Integer putValue = 1;
		cache.put("ONE", putValue);
		Integer value = cache.get("ONE");
		assertEquals(putValue, value);
	}

	/**
	 * This is a copy from the Cache class. It is not public there, but we would like to do some unit tests on it.
	 */
	static int limitToPositiveInt(long value)
	{
		if (value > (long)Integer.MAX_VALUE)
		{
			return Integer.MAX_VALUE;
		}
		else if  (value < 0)
		{
			return 0;
		}
		return (int)value;
	}

	private static Cache<String, Integer> createCache(String id, int idleTime, int cacheTime, int size)
	{
		Builder<String, Integer> cacheB = TCacheFactory.standardFactory().builder();
		cacheB.setId(id);
		cacheB	.setMaxIdleTime(idleTime, TimeUnit.SECONDS)
                .setMaxCacheTime(cacheTime, TimeUnit.SECONDS)
                .setExpectedMapSize(size);

		return cacheB.build();
	}

//	@Test
//	public void testCacheConstructorDefaultIdle()
//	{
//		try
//		{	
//			//Test constructor with all parameters and idle time
//			Cache<String, Integer> tmpCache = createCache("CacheTest-2", 1800, maxCacheTime, 10);
//
//			assertEquals("Cache object should have idle time of 1800", 1800, tmpCache.expiryForAccessSecs());
//		}
//		catch (Exception e)
//		{
//			fail(e.getMessage());
//		}
//	}
	
	@Test
	public void expireCacheEntry()
	{
		Cache<String, Integer> cache1 = createCache("CacheTest-autoCleanCacheEntry", 1, 1, 10);

		assertTrue("Cache is not empty at start of test",  cache1.size() == 0);
		
		
		String key = "key-a";
		Integer value = 1;
		
		cache1.put(key, value);
		assertEquals("Retrieved value do not match.", value, cache1.get(key));
		try 
		{
			Thread.sleep(1400);
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
		
		assertTrue("Cache is not empty after sleep",  cache1.size() == 0);
	}
	
	@Test
	public void putIfAbsent()
	{
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		
		String key = "key-b";
		Integer value = 1;
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime, TimeUnit.SECONDS);
		assertEquals("Retrived value do not match.", value, cache.get(key));
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime, TimeUnit.SECONDS);
		assertEquals("Retrived value do not match.", value, cache.get(key));
	}
	
	
	@Test
	public void getAndPut()
	{
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		
		String key = "key-b";
		Integer value1 = 1;
		Integer value2 = 2;
		
		TCacheJSR107<String, Integer> jsr107cache = cache.jsr107cache();

		jsr107cache.put(key, value1);
		Integer oldValue = jsr107cache.getAndPut(key, value2);
		assertEquals("Retrived value do not match.", value1, oldValue);
		
		Integer newValue = jsr107cache.get(key);
		assertEquals("Retrived value do not match.", value2, newValue);
	}

	@Test
	public void getAndPut_Existing() throws Exception
	{

		MutableConfiguration<String, Integer> mc = new MutableConfiguration<>();

		TCacheFactory factory = TCacheFactory.standardFactory();
		javax.cache.Cache<String, Integer> jsr107cache = factory.createCache("getAndPut_Existing", mc);

		String existingKey = Long.toString(System.currentTimeMillis());
		Integer value1 = 1;
		jsr107cache.getAndPut(existingKey, value1);
		Integer value2 = 2;
		assertEquals(value1, jsr107cache.getAndPut(existingKey, value2));
		assertEquals(value2, jsr107cache.get(existingKey));
	}

	@Test
	public void removeEntry()
	{
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		
		String key = "key-c";
		Integer value = 1;
		
		// Add key-value
		cache.put(key, value, 5, 5, TimeUnit.SECONDS);
		
		assertTrue("Cache is not empty at start of test", cache.size() == 1);
		assertEquals("Retrived value do not match.", value, cache.get(key));
		
		// Remove existing key-value
		Integer removedValue = (Integer) cache.remove(key);
		
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		assertEquals("Removed value do not match.", value, removedValue);
		
		// Remove non existing key-value
		Integer nullValue = (Integer) cache.remove("_non_existent_key_");
		
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		assertNull("Found value when removing a non existent key.", nullValue);
	}
	
	/*
	@Test
	public void testDefaultAccessTimeOjectHolder()
	{
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		
		String key = "key-a";
		Integer value = 1;
		long inputDate = System.currentTimeMillis();
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
		
		TCacheJSR107<String, Integer> jsr107cache = cache.jsr107cache();
		Iterator<Entry<String, Integer>> it = jsr107cache.iterator();
		
		// cache.getAccessTimeHolderObjects() is not available any longer. We might want a replacement like an iterator on the actual Cache entries 
//		Collection<AccessTimeObjectHolder<Integer>> list = cache.getAccessTimeHolderObjects();
		
//		assertTrue("List should contain exactly one holder", !list.isEmpty() && list.size() == 1);
		
//		AccessTimeObjectHolder<Integer> holder = list.iterator().next(); // Get  first (and only) element
		Entry<String, Integer> holder = it.next();
		assertTrue("iterator has more than one element", !it.hasNext());
		
//		AccessTimeObjectHolder<Integer> holder = holder107.unwrap(AccessTimeObjectHolder.class);
//		holder.setMaxIdleTime(2, TimeUnit.SECONDS);
		
//		assertEquals("Value does not match", 2000, holder.getExpirationTime());
		assertEquals("Value does not match", value, holder.getValue());
		assertEquals("Value does not match", 1, holder.getUseCount());
		
		holder.get();
//		lastAccess = System.currentTimeMillis();
		
//		assertEquals((System.currentTimeMillis() - lastAccess) / 1000.0, holder.getIdleTime(), 0.5);
		assertEquals(inputDate, holder.getInputDate(), 0);
		
	}
*/
	
	@Test
	public void testGetAccessTimeHolderObjects()
	{
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		
		String key = "key-a";
		Integer value = 1;
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime, TimeUnit.SECONDS);
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime, TimeUnit.SECONDS);
		
		TCacheJSR107<String, Integer> jsr107cache = cache.jsr107cache();
		Iterator<Entry<String, Integer>> it = jsr107cache.iterator();

		assertTrue("List is empty", it.hasNext());
	}
	
	@Test
	public void testContainsKey()
	{
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		
		String key = "key-a";
		Integer value = 1;
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime, TimeUnit.SECONDS);
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime, TimeUnit.SECONDS);
		
		assertTrue("Object not in cache", cache.containsKey(key));
	}


	/**
	 * The JSR107 Spec mandates to throw CacheException
	 */
	@Test(expected=CacheException.class)
	public void duplicateRegistration()
	{
		Random rnd = new Random();
		String cacheName = "Duplicated-" + Long.toHexString(rnd.nextLong());
		Cache<String, Integer> cache1 = createCache(cacheName, maxIdleTime, maxCacheTime, 1000);
		Cache<String, Integer> cache2 = createCache(cacheName, maxIdleTime, maxCacheTime, 1000);
		
		assertTrue("Control flow must not pass this line - IllegalStateException must have been thrown already", false);
		cache1.get("foo"); // line is only present to avoid warnings 
		cache2.get("foo"); // line is only present to avoid warnings
	}
	
	/**
	 * Test whether a cache with the same name
	 */
	@Test
	public void testRecreateAfterDestroy()
	{
		TCacheFactory cacheManager = TCacheFactory.standardFactory();
		Random rnd = new Random();
		String cacheName = "RandomCache-" + Long.toHexString(rnd.nextLong());
		String key = Long.toHexString(rnd.nextLong());
		Integer value1 = rnd.nextInt();
		Integer value2 = value1 + 1;

		Cache<String, Integer> cache1 = createCache(cacheName, maxIdleTime, maxCacheTime, 100);
		cache1.put(key, value1);
		assertEquals("Value in Cache is not identical", value1, cache1.get(key));
		cacheManager.destroyCache(cacheName);

		// The next line is the key part of this test. Can we recreate a Cache with the same name after destroying the old one?
		Cache<String, Integer> cache2 = createCache(cacheName, maxIdleTime, maxCacheTime, 100);
		cache2.put(key, value2);
		assertEquals("Value in Cache is not identical", value2, cache2.get(key));
	}
	
	@Test
	public void testNoWriteAfterDestroy()
	{
		TCacheFactory cacheManager = TCacheFactory.standardFactory();
		Random rnd = new Random();
		String cacheName = "RandomCache-" + Long.toHexString(rnd.nextLong());
		String key = Long.toHexString(rnd.nextLong());
		Integer value = rnd.nextInt();

		Cache<String, Integer> cache1 = createCache(cacheName, maxIdleTime, maxCacheTime, 10);
		cache1.put(key,value);
		assertEquals("Value in Cache is not identical", value, cache1.get(key));

		cacheManager.destroyCache(cacheName);
		
		// tCache silently ignores operations after destroying, but JCache requires IllegalStateException. Check both.
		// a) tCache
		cache1.put(key,value);
		TCacheJSR107<String, Integer> jcache1 = cache1.jsr107cache();
		// b) jcache
		boolean correctExceptionThrown = false;
		try
		{
			// Explicitly checking this code position instead of marking the the whole test with expected=IllegalStateException.class 
			jcache1.put(key,value);
		}
		catch (IllegalStateException e)
		{
			correctExceptionThrown = true;
		}
		
		assertTrue("IllegalStateException must be thrown when accessing a closed Cache", correctExceptionThrown);
	}
	
	@Test
	public void testUncaughtException()
	{
		try
		{
			cache.uncaughtException(Thread.currentThread(), new Throwable());
			
			String key = "key-a";
			Integer value = 1;
			
			cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime, TimeUnit.SECONDS);
		}
		catch (Exception e)
		{
			fail(e.getMessage());
		}

	}
	
	@Test
	public void testCacheHitRate()
	{
		if (!runAcceptanceTests)
		{
			// SRT-10403 Skip acceptance tests for normal builds (would also drastically delay release builds)
			//           Acceptance tests need to be moved to a different TestSuite 
			return;
		}
		
		String key = "key-a";
		Integer value = 1;
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime, TimeUnit.SECONDS);
		
		try
		{
			Thread.sleep(1*60*1000 + 10);
		}
		catch (InterruptedException e)
		{
			fail(e.getMessage());
		}
		
		assertEquals(8.0 ,cache.getCacheHitrate(), 0.0);
		
		TCacheStatistics cacheStatistics = cache.statistics();
		
		assertEquals(2.0, cacheStatistics.getHitCount(), 0.0);
	}


}
