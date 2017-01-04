/*********************************************************************************
 * Copyright 2016-present trivago GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 **********************************************************************************/

package com.trivago.triava.tcache;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.concurrent.TimeUnit;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;

import com.trivago.triava.logging.TriavaConsoleLogger;
import com.trivago.triava.tcache.eviction.Cache;

/**
 * Helper class for tests. Do not put @Test in any of these methods, instead put them in 
 * CacheListenerTestAsync and/or CacheListenerTestSync. This is required, as they initialize the test configuration properly. 
 * 
 * @author cesken
 *
 */
public class CacheListenerTest extends CacheListenerTestBase
{
	static boolean DEBUG_OUTPUT = false; // not final, to be able to adjust it per UnitTest
	
	private static final long serialVersionUID = -7831605500466126577L;

	CacheListenerTest()
	{
	}
	
	CacheListenerTest(int maxWait, TimeUnit unit)
	{
		super(maxWait, unit);
	}

	public void testListener()
	{
		try
		{
			javax.cache.Cache<Integer, String> cache = createCache("testListener", null);

			resetListenerCounts();
			cache.put(1, "One");
			// Synchronous => Listener must have been executed before leaving put()
			checkCreated(1);

			resetListenerCounts();
			cache.put(2, "Two");
			cache.put(3, "Three");
			checkCreated(2);

			resetListenerCounts();
			cache.put(2, "Two updated");
			checkUpdated(1);

			resetListenerCounts();
			cache.put(1, "One");
			checkCreated(0);
			checkUpdated(1); // Unchanged, but touched => updated

		}
		catch (Exception e)
		{
			fail(e.getMessage() + ": " + e.getCause());
		}
	}

	private javax.cache.Cache<Integer, String> createCache(String name, Integer size)
	{
		javax.cache.Cache<Integer, String> cache = createJsr107Cache(name + "-sync-" + runMode, size, null);
		Factory<UpdateListener> ulFactory = FactoryBuilder.factoryOf(new UpdateListener());
		CacheEntryListenerConfiguration<Integer, String> listenerConf = new MutableCacheEntryListenerConfiguration<>(ulFactory, null, false, runMode == RunMode.SYNC);
		cache.registerCacheEntryListener(listenerConf);
		return cache;
	}
	
	private javax.cache.Cache<Integer, String> createCacheWithExpiredListener(String name, Integer size)
	{
		return createCacheWithExpiredListener(name, size, null, false);
	}
	
	private javax.cache.Cache<Integer, String> createCacheWithExpiredListener(String name, Integer size, Integer expirationMillis, boolean printAllEvents)
	{
		javax.cache.Cache<Integer, String> cache = createJsr107Cache(name + "-" + runMode, size, expirationMillis);
		Factory<MyExpiredListener> ulFactory = FactoryBuilder.factoryOf(new MyExpiredListener(printAllEvents, name));
		CacheEntryListenerConfiguration<Integer, String> listenerConf = new MutableCacheEntryListenerConfiguration<>(ulFactory, null, false, runMode == RunMode.SYNC);
		cache.registerCacheEntryListener(listenerConf);
		return cache;
	}
	
	public void testWriteMoreThanCapacity()
	{
		if (DEBUG_OUTPUT)
			Cache.setLogger(new TriavaConsoleLogger());
		
		int capacity = 10000;
		javax.cache.Cache<Integer, String> cache = createCacheWithExpiredListener("testWriteMoreThanCapacity", capacity);

		resetListenerCounts();

		int puts = 0;
		for (int i = 0; i < 100 * capacity; i++)
		{
			cache.put(i, "Value " + i);
			puts ++;
		}
		
		int expireAtLeast = 10 * capacity; // Actually 99*capacity or at least 90*capacity could be checked
		checkEventCountAtLeast(expireAtLeast, expiredListenerFiredCount);
		
		if (DEBUG_OUTPUT)
			System.out.println("puts=" + puts + ", evictedListenerFiredCount=" + expiredListenerFiredCount + ", diff=" + (puts - expiredListenerFiredCount.get()) );
		
		// Verify we have not notified more than we have written.
		int expiredNotifications = expiredListenerFiredCount.get();
		assertTrue("More evicted than put, put=" + puts + ", expired = " + expiredNotifications, expiredNotifications <= puts);
	}

	public void testExpiryListenerWithAllExpiring()
	{
		if (DEBUG_OUTPUT)
			Cache.setLogger(new TriavaConsoleLogger());
		int capacity = 1_000_000;
		javax.cache.Cache<Integer, String> cache = createCacheWithExpiredListener("testExpiryListener", capacity+1, 1, false);
		Cache<?,?> tcache = cache.unwrap(Cache.class);
		tcache.setCleanUpIntervalMillis(1);

		resetListenerCounts();

		int puts = 0;
		for (int i = 0; i < capacity ; i++)
		{
			cache.put(i, "Value " + i);
			puts ++;
		}
		
		checkEventCount(capacity, expiredListenerFiredCount);
		if (DEBUG_OUTPUT)
			System.out.println("puts=" + puts + ", expiredListenerFiredCount=" + expiredListenerFiredCount + ", diff=" + (puts - expiredListenerFiredCount.get()) );
	}
	
	
	/**
	 * @deprecated Do not use at this point of time!. See {@link #testExpiryListenerOverwrite(boolean)} documentation
	 */
	public void testExpiryListenerOverwriteWithPut0()
	{
		testExpiryListenerOverwrite(false);
	}

	public void testExpiryListenerOverwriteWithPutifabsent0()
	{
		testExpiryListenerOverwrite(true);
	}

	/**
	 * Writes the same element lots of times, and checks if notification for overwritten expired elements are done. This test
	 * is not fully safe, so you may want to re-run it on failures. As the test is important we keep it around, and hopefully
	 * it can be enhanced to work fully relibale in the future.
	 * 
	 * <p>
	 * WARNING. This test contains a timing component, and may fail
	 * in case of unlucky timing. A failed test could happen when a put or putIfAbsent is done, but the entry expires before returning from the put()/putIfAbsent() code.
	 * This will typically not happen for putIfAbsent, as it tends to write at the start of a new millisecond due to the test design. This needs an explanation, so read on:
	 * 
	 * 1) putIfAbsent() is shown below with "A" (successful writes), and "." (no writes). All successfully written
	 *  entries will be notified as EXPIRED, as soon as the next write occurs.
	 * see, the first write is somewhere between Millisecond 0 and 1, and the following writes are directly
	 * or shortly after millisecond switches. Thus there is a full Millisecond between writing and returning,
	 * so the test typically will not fail. Fails may happen if there is GC activity, JIT or anything else that
	 * makes the Thread not progress fast enough. Chances are low, but not impossible. 
	 *    <pre>
	 *           A . . A . . . . . . A  . . . . . . A . . .. . . A . .
	 *  -0-------------1-------------2-------------3-------------4-----> time in ms
	 *   P P P P P P P E P P P P P P E P P P P P  X P P P P P P  E P P
	 *   </pre>
	 * 2) put() is shown above with "P" (successful write that overwrite a non-expired entry),
	 * "E" (successful write that overwrite an expired entry),
	 * and "X" (successful write that overwrite a non-expired entry, WHICH EXPIRES before put() returns).
	 * All writes of type "E" will trigger a notification about the former "P" entry.
	 * As "X" is not expired when being overwritten, there is no EXPIRY notification (good, correct!). The
	 * unit test sees the expired value and believes an EXPIRY notification should have been sent (bad, incorrect!).
	 *  
	 * @param ifAbsent true, for testing with putIfAbsent(). False fore using put()
	 */
	public void testExpiryListenerOverwrite(boolean ifAbsent)
	{
		if (DEBUG_OUTPUT)
			Cache.setLogger(new TriavaConsoleLogger());
		int count = 50;
		final int expectedNotifications;
		
		// There may be 1 notification less than count, as the last putIfAbsent will expire after the expiration thread runs
		// We do not want to wait for that, so we 
		expectedNotifications = count - 1;

		javax.cache.Cache<Integer, String> cache = createCacheWithExpiredListener("testExpitestExpiryListenerOverwrite-" + (ifAbsent ? "putIfAbsent" : "put"), 1000, 1, true);
		Cache<?,?> tcache = cache.unwrap(Cache.class);
		tcache.setCleanUpIntervalMillis(100_000);

		resetListenerCounts();

//		@SuppressWarnings("unused")  // only for 		if (DEBUG_OUTPUT)
		int puts = 0, tries = 0;
		while (puts < count)
		{
			int i = puts;
			
			final boolean put;
			if (ifAbsent)
			{
				put = cache.putIfAbsent(1, "Value " + i);
			}
			else
			{
				boolean replaced = cache.getAndPut(1, "Value " + i) != null;
				// We count it is put, if getAndPut() added a new value (not replacing the old one). Reason is, that this means that the old value expired (or not existed at all). 
				put = !replaced; 
			}
			
			if (put)
				puts++;
			tries ++;
		}
		
		checkEventCountAtLeast(expectedNotifications, expiredListenerFiredCount);
		if (DEBUG_OUTPUT)
			System.out.println(cache.getName() + ": tries=" + tries + ": puts=" + puts + ", expiredListenerFiredCount=" + expiredListenerFiredCount + ", diff=" + (puts - expiredListenerFiredCount.get()) );
	}
}
