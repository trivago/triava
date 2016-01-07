package com.trivago.triava.tcache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Random;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.eviction.Cache.AccessTimeObjectHolder;
import com.trivago.triava.tcache.statistics.TCacheStatistics;

public class CacheTest
{
	private static final long maxIdleTime = 100L;
	private static final long maxCacheTime = 1000L;
	private static Cache<String, Integer> cache = createCache("CacheTest", maxIdleTime, maxCacheTime, 10);

	boolean runAcceptanceTests = false;

	@BeforeClass
	public static void setUp()
	{
		cache.setCleanUpIntervalMillis(300);
		cache.put("", 0);
		
		try 
		{
			Thread.sleep(500);
		} 
		catch (InterruptedException e) 
		{
			e.printStackTrace();
		}
	}
	
	@AfterClass
	public static void tearDown()
	{
		TCacheFactory.standardFactory().close();
	}
	
	@Before
	public void setUpEach()
	{
		cache = TCacheFactory.standardFactory().<String,Integer>builder().setExpectedMapSize(10).setMaxCacheTime(maxCacheTime).setMaxIdleTime(maxIdleTime) .build();

	}
	
	@Test
	public void testCacheConstructor()
	{
		try
		{
			//Test constructor with all parameters and idle time
			Cache<String, Integer> tmpCache = createCache("CacheTest-1", maxIdleTime, maxCacheTime, 10);

			assertEquals("Cache idle time not set correctly", maxIdleTime, tmpCache.getDefaultMaxIdleTime());
		}
		catch (Exception e)
		{
			fail(e.getMessage());
		}
	}

	private static Cache<String, Integer> createCache(String id, long idleTime, long cacheTime, int size)
	{
		Builder<String, Integer> cacheB = TCacheFactory.standardFactory().builder();
		cacheB.setId(id);
		cacheB	.setMaxIdleTime(idleTime)
                .setMaxCacheTime(cacheTime)
                .setExpectedMapSize(size);

		return cacheB.build();
	}

	@Test
	public void testCacheConstructorDefaultIdle()
	{
		try
		{	
			//Test constructor with all parameters and idle time
			Cache<String, Integer> tmpCache = createCache("CacheTest-2", 0, maxCacheTime, 10);

			assertEquals("Cache object should have idle time of 1800", 1800, tmpCache.getDefaultMaxIdleTime());
		}
		catch (Exception e)
		{
			fail(e.getMessage());
		}
	}
	
	@Test
	public void autoCleanCacheEntry()
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
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
		assertEquals("Retrived value do not match.", value, cache.get(key));
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
		assertEquals("Retrived value do not match.", value, cache.get(key));
	}
	
	@Test
	public void removeEntry()
	{
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		
		String key = "key-c";
		Integer value = 1;
		
		// Add key-value
		cache.put(key, value, 5L, 5L);
		
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
	
	@Test
	public void testDefaultAccessTimeOjectHolder()
	{
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		
		String key = "key-a";
		Integer value = 1;
//		long lastAccess = 0L;
		long inputDate = 0L;
		
		inputDate = System.currentTimeMillis();
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
		
		Collection<AccessTimeObjectHolder<Integer>> list = cache.getAccessTimeHolderObjects();
		
		assertTrue("List should contain exactly one holder", !list.isEmpty() && list.size() == 1);
		
		AccessTimeObjectHolder<Integer> holder = list.iterator().next(); // Get  first (and only) element
		
		holder.setMaxIdleTime(2);
		
		assertEquals("Value does not match", 2, holder.getMaxIdleTime());
		assertEquals("Value does not match", value, holder.peek());
		assertEquals("Value does not match", 1, holder.getUseCount());
		
		holder.get();
//		lastAccess = System.currentTimeMillis();
		
//		assertEquals((System.currentTimeMillis() - lastAccess) / 1000.0, holder.getIdleTime(), 0.5);
		assertEquals(inputDate, holder.getInputDate(), 0);
		
	}
	
	@Test
	public void testGetAccessTimeHolderObjects()
	{
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		
		String key = "key-a";
		Integer value = 1;
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
		
		Collection<AccessTimeObjectHolder<Integer>> list = (Collection<AccessTimeObjectHolder<Integer>>) cache.getAccessTimeHolderObjects();
		
		assertTrue("List is empty", !list.isEmpty());
	}
	
	@Test
	public void testContainsKey()
	{
		assertTrue("Cache is not empty at start of test",  cache.size() == 0);
		
		String key = "key-a";
		Integer value = 1;
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
		
		assertTrue("Object not in cache", cache.containsKey(key));
	}
	
	@Test
	public void testGetMaxCacheTime()
	{
		assertEquals("Value does not match", maxCacheTime, cache.getMaxCacheTime());
	}

	
	@Test(expected=IllegalStateException.class)
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
	
	// TODO JSR107 compliance
	@Ignore("This test is for JSR107 compliance. Right now tcache gracefully returns null instead of IllegalStateException, so we cannot do this test")
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
		boolean correctExcetionThrown = false;
		try
		{
			cache1.put(key,value);
		}
		catch (IllegalStateException e)
		{
			correctExcetionThrown = true;
		}
		
		assertTrue("IllegalStateException must be thrown when accessing a closed Cache", correctExcetionThrown);
	}
	
	@Test
	public void testUncaughtException()
	{
		try
		{
			cache.uncaughtException(Thread.currentThread(), new Throwable());
			
			String key = "key-a";
			Integer value = 1;
			
			cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
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
		
		cache.putIfAbsent(key, value, maxIdleTime, maxCacheTime);
		
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
