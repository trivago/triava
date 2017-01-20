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

package com.trivago.triava.tcache.integration;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.cache.Cache.Entry;

import org.junit.AfterClass;
import org.junit.Test;

import com.trivago.triava.tcache.Cache;
import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.TCacheHolder;

/**
 * DISCLAIMER: THESE TESTS ARE NOT PART OF THE REGULAR UNIT TESTS. THEY WILL NOT BE EXECUTED IN THE MAVEN TEST SCOPE.
 *             ONLY RUN THEM IF YOU KNOW THE INNER WORKINGS OF TRIAVA CACHE.
 * <p> 
 * These tests verify tCache expiration behavior. They are important for catching critical bugs before a release.
 * They are not tested in the Maven test scope, as the class name does not start or end with "Test".
 * 
 * Please note that some tests are highly implementation specific and exploit knowledge of the internal workings.
 * They may fail at any time, and additionally some of the tests have time based race conditions, e.g. may fail if the test machine is overloaded, does GC or JIT is running.
 * This means: Sporadic test failures are not of concern. But if a test fails all the time, this is an indication of an issue and should be addressed.
 * <p>
 * 
 * <pre>
 * Important to keep in mind:
 *  - The internal time precision in Triava Cache is currently 10ms.
 *  - Expiration and Eviciton are both running in batches.
 *  - The Cache can slightly overfill during batch eviction (size is a soft limit), but never above a hard limit.
 *  </pre>
 * 
 * @author cesken
 *
 */
public class HolderReleaseChecks
{
	final int threadCount = 10;
	final int keyCount = 1000;
	final int iterations = 1000;
	final boolean PRINT_FAILURE_DETAILS = true;
	
	private static final int maxIdleTimeSecs = 100_000;
	private static final int maxCacheTimeSecs = 1000_000;

	boolean runAcceptanceTests = false;

	
	@AfterClass
	public static void tearDown()
	{
		// Not closing, as there is another unit test that also uses the TCacheFactory.standardFactory() 
		//TCacheFactory.standardFactory().close();
	}
	
	class Result
	{
		final boolean haveDeleted;
		final String key;
		private boolean ok;
		
		public Result(boolean haveDeleted2, String key2, boolean ok)
		{
			this.haveDeleted = haveDeleted2;
			this.key = key2;// TODO Auto-generated constructor stub
			this.ok = ok;
		}
	}
	
	class DeleteFromCache implements Callable<Result>
	{
		private final Cache<String, Integer> cache;
		private final String key;
		private final int randomizer;
		
		DeleteFromCache(Cache<String, Integer> cache, String key, int randomizer)
		{
			this.cache = cache;
			this.key = key;
			this.randomizer = randomizer;
		}

		@Override
		public Result call() throws Exception
		{
			Iterator<Entry<String, TCacheHolder<Integer>>> iterator = cache.iterator();
			Entry<String, TCacheHolder<Integer>> entryBeforeDelete = null;
			while (iterator.hasNext())
			{
				Entry<String, TCacheHolder<Integer>> entry = iterator.next();
				if (key.equals(entry.getKey()))
				{
					entryBeforeDelete = entry;
					break;
				}
			}
			boolean wasValidBeforeDelete = entryBeforeDelete != null && !entryBeforeDelete.getValue().isInvalid();
			
			Object oldValue = cache.remove(key);
			
			boolean haveDeleted = oldValue != null;
			final boolean ok;
			if (haveDeleted)
			{
				// It existed before, was valid, and is now not any longer valid
				ok = wasValidBeforeDelete && entryBeforeDelete.getValue().isInvalid();
				if (!ok)
				{
					TCacheHolder<Integer> value = entryBeforeDelete.getValue();
					boolean invalid = value.isInvalid();
					System.out.println("NotOK haveDeleted: " + key + ", wasValidBeforeDelete=" + wasValidBeforeDelete + ", nowInvalid=" + invalid);
				}
			}
			else
			{
				// Did not even exist before (removed by a different Thread), or is now invalid
				boolean okTmp =  entryBeforeDelete == null || entryBeforeDelete.getValue().isInvalid();
				if (!okTmp)
				{
					// This line is reached, if another Thread has called cache.remove(), and is still inside that method: Triava Cache has then removed
					// the entry from the internal Map but not yet invalidated the Holder. This is allowed, as there are no visibility guarantees on the holder.
					//
					// For this internal test it requires special handling: We simply re-validate it by inspecting the actual Map entry
					Integer integer = cache.get(key);
					if (integer != null)
					{
						System.out.println("NotOK not deleted: " + key + ", entryBeforeDelete=" + entryBeforeDelete);
					}
					else
					{
						okTmp = true;
					}
//					while (true)
//					{
//						boolean invalidated = entryBeforeDelete.getValue().isInvalid();
//						if (invalidated)
//						{
//							System.out.println("NotOK not deleted, but invalidatated shortly after: " + key + ", entryBeforeDelete=" + entryBeforeDelete);
//							break;
//						}
//					}
				}
				
				ok = okTmp;
			}
			return new Result(haveDeleted, key, ok);
		}
	}
	
	
	@Test
	public void testConcurrentDeletes() throws InterruptedException, ExecutionException
	{
		for (int iteration=0; iteration < iterations; iteration++)
		{
			concurrentDeleteImpl(0);
			//concurrentDeleteImpl(keyCount*iteration);
		}
	}

	public void concurrentDeleteImpl(int offset) throws InterruptedException, ExecutionException
	{
		final int DEFAULT_CAPACITY = keyCount;

		Cache<String, Integer> cache = TCacheFactory.standardFactory().<String,Integer>builder().setMaxElements(DEFAULT_CAPACITY).setMaxCacheTime(maxCacheTimeSecs, TimeUnit.SECONDS).setMaxIdleTime(maxIdleTimeSecs, TimeUnit.SECONDS) .build();
		for (int k=0; k<keyCount; k++)
		{
			cache.put(Integer.toString(offset+k), offset+k);
		}
		BlockingQueue<Runnable> executorQueue = new LinkedBlockingQueue<>(keyCount * threadCount);
		ExecutorService es = new ThreadPoolExecutor(threadCount, threadCount, 120L, TimeUnit.MINUTES, executorQueue);
		Collection<DeleteFromCache> dfc = new ArrayList<>(threadCount);

		int randomizer = 0;
		for (int k=0; k<keyCount; k++)
		{
			for (int i=0; i<threadCount; i++)
			{
				//es.submit(new DeleteFromCache(cache, Integer.toString(k)));
				dfc.add(new DeleteFromCache(cache, Integer.toString(offset+k), randomizer));
			}
		}
		
		List<Future<Result>> results = es.invokeAll(dfc);
		int succesfulDeletes = 0;
		int notOK = 0;
		for (Future<Result> resultFuture : results)
		{
			Result result = resultFuture.get();
			if (result.haveDeleted == true)
			{
				succesfulDeletes ++;
//				System.out.println("Succesfully deleted: " + result.key);
			}
			if (!result.ok)
				notOK ++;
//			else
//			{
//				System.out.println("FAIL delete: " + result.key);
//			}
		}
		
		assertEquals("succesfulDeletes must be exactly " + keyCount, keyCount, succesfulDeletes); 
		assertEquals("Some values are not OK" , 0, notOK); 
		
		es.shutdownNow();
		cache.close();
	}


}
