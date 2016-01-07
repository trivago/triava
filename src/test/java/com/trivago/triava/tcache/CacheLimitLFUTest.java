package com.trivago.triava.tcache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.trivago.triava.tcache.EvictionPolicy;
import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.FreezingEvictor;
import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.eviction.TCacheHolder;

/**
 * Tests covering LFU eviction
 * 
 * @author cesken
 *
 */
public class CacheLimitLFUTest
{
	
	private static final long maxIdleTime = 1L;
	private static final long maxCacheTime = 1L;
	private static Cache<String, Integer> cache = buildLfuCache("CacheLFUTest", maxIdleTime, maxCacheTime, 10);

	boolean runAcceptanceTests = false;
	
	@BeforeClass
	public static void setUp()
	{
//		Cache.setLogger(new TriavaConsoleLogger());
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

	private static Cache<String, Integer> buildLfuCache(String string, long maxidletime2, long maxcachetime2, int expectedMapSize)
	{
		Builder<String, Integer> builder = cacheBuilder(string, maxidletime2, maxcachetime2, expectedMapSize);
		builder.setEvictionPolicy(EvictionPolicy.LFU);
		return builder.build();
	}

	private static Builder<String, Integer> cacheBuilder(String string, long maxidletime2, long maxcachetime2, int expectedMapSize)
	{
		Builder<String, Integer> builder = TCacheFactory.standardFactory().builder();
		builder.setId(string).setExpectedMapSize(expectedMapSize);
		builder.setMaxIdleTime(maxidletime2).setMaxCacheTime(maxcachetime2);
		return builder;
	}

	@AfterClass
	public static void tearDown()
	{
		// Not closing, as there is another unit test that also uses the TCacheFactory.standardFactory() 
//		TCacheFactory.standardFactory().close();
	}
	
	@Before
	public void setUpEach()
	{
		cache.clear();
		
		exceedingMaxElements();
	}

	@Test
	public void testCacheLimitLFUv2()
	{
		try
		{		
			assertEquals("Value does not match", 1L, buildLfuCache("CacheLFUTest-2", maxIdleTime, maxCacheTime, 10).getMaxCacheTime());
		}
		catch (Exception e)
		{
			fail(e.getMessage());
		}
	}

	@Test
	public void testKeySet()
	{
		assertTrue("Key set is empty", !cache.keySet().isEmpty());
	}
	
	@Test
	public void endlessExpiration0() throws InterruptedException
	{
		if (!runAcceptanceTests)
			return;
		
		Builder<String, Integer> builder = cacheBuilder("endlessExpiration0", 0, 0, 10);
		Cache<String, Integer> cache = builder.build();

		checkAfterTime(cache, "a", 12, 2000, true);
	}
	
	@Test
	public void endlessExpirationMAXINT() throws InterruptedException
	{
		if (!runAcceptanceTests)
			return;

		Builder<String, Integer> builder = cacheBuilder("endlessExpiration1", Integer.MAX_VALUE, Integer.MAX_VALUE, 10);
		Cache<String, Integer> cache = builder.build();

		checkAfterTime(cache, "a", 12, 2000, true);
	}

	@Test
	public void expirationAfter1second() throws InterruptedException
	{
		if (!runAcceptanceTests)
			return;

		Builder<String, Integer> builder = cacheBuilder("endlessExpiration1", 1, 1, 10);
		Cache<String, Integer> cache = builder.build();

		checkAfterTime(cache, "a", 12, 2000, false);
	}
	
	private void checkAfterTime(Cache<String, Integer> cache2, String key, int value, int delayMills, boolean shouldBePresent) throws InterruptedException
	{
		cache2.put(key, value);
		Thread.sleep(delayMills);
		Integer val = cache2.get(key);
		
		if (shouldBePresent)
		{
			if (val == null)
			{
				fail("Cache " + cache2.id() + " must have a value for key=" + key + " after delay of " + delayMills + "ms");
			}
			else
			{
				assertEquals("Cache " + cache2.id() + " value for key=" + key + " not present or same after delay of " + delayMills + "ms", value, val.intValue());
			}
		}
		else
		{
			if (val != null)
			{
				fail("Cache " + cache2.id() + " must not have a value for key=" + key + " after delay of " + delayMills + "ms");
			}
		}
	}

	private void exceedingMaxElements()
	{
		int count = 12000;
		
		for (int i = 0; i < count; i++)
		{
			cache.putIfAbsent(String.valueOf(i), new Integer(i), maxIdleTime, maxCacheTime);
		}
	}

	// ----------------------------- CUSTOM EVICITON TEST FOLLOWS BEWLOW -----------------------------
	
	/**
	 * Example for a custom eviction implementation.
	 */
	static class CustomerClassEvictor extends FreezingEvictor<Integer, CustomerType>
	{
		@Override
		public long getFreezeValue(Integer userId, TCacheHolder<CustomerType> customerType)
		{
			int priority = customerType.peek().getPriority();
			if (priority == 0)
			{
//				System.out.println("0");
			}
			return priority;
		}
	}
	
	enum CustomerType
	{
		Guest(0), Registered(5), Premium(9);
		
		public int getPriority() { return priority;	}

		int priority;
		CustomerType(int priority) { this.priority = priority; };
	}
		
//	@Test
	public void customEvictionTestForward()
	{
		customEvictionTest(false);
	}
	
//	@Test
	public void customEvictionTestReverse()
	{
		customEvictionTest(true);
	}
	
	private void customEvictionTest(boolean reverse)
	{
		int MAP_SIZE = 1000;		
		int WRITES_PER_TYPE = 100 * MAP_SIZE;
		int ACCEPTABLE_NON_PREMIUM_RATE = 26; // 25% (can happen, tCache can get overfilled wit 15% and also after eviction there is 10% extra space)
		
		Builder<Integer, CustomerType> builder = TCacheFactory.standardFactory().builder();
		builder.setId("customEviction-reverse=" + reverse).setExpectedMapSize(MAP_SIZE);
		builder.setEvictionClass(new CustomerClassEvictor());
		Cache<Integer, CustomerType> ccache = builder.build();
		
		int autoindex = 0;
		CustomerType[] ctForward = { CustomerType.Guest, CustomerType.Premium};
		CustomerType[] ctBackward = { CustomerType.Premium, CustomerType.Guest};
		
		CustomerType[] customerTypes = reverse ?  ctBackward : ctForward;
		
		for (CustomerType custType : customerTypes)
		{
			for (int i=0; i<WRITES_PER_TYPE; i++)
			{
				ccache.put(autoindex++, custType);
//				try
//				{
//					Thread.sleep(0,1);
//				}
//				catch (InterruptedException e)
//				{
//				}
			}
		}
		
		try
		{
			Thread.sleep(100);
		}
		catch (InterruptedException e)
		{
		}
//		ccache.put(autoindex++, CustomerType.Premium);

		int countNonPremium = 0;
		for (Integer key : ccache.keySet())
		{
			CustomerType customerType = ccache.get(key);
			if (customerType != CustomerType.Premium)
			{
				System.out.println(key + " => " + customerType);
				// Hier bleibt gerne folgendes übrig:
				// 16383 => Guest   16383 = 0x3FFF
				// 18431 => Guest   18431 = 0x47FF
				countNonPremium++;
			}
		}
		
		System.out.println(ccache.statistics());

		if (reverse)
		{
			int size = ccache.size();
//			System.out.println("size=" +size);
			int allowedNonPremium = size * ACCEPTABLE_NON_PREMIUM_RATE / 100;
			assertTrue("Most CustomerType != Premium must be evicted for type 'backward'. countNonPremium=" + countNonPremium + ", allowedNonPremium=" + allowedNonPremium, countNonPremium < allowedNonPremium);
		}
		else
		{
			int allowedNonPremium = 0;
			assertTrue("All CustomerType != Premium must be evicted for type 'forward'. countNonPremium=" + countNonPremium + ", allowedNonPremium=" + allowedNonPremium, countNonPremium == 0);			
		}
		
	}
}
