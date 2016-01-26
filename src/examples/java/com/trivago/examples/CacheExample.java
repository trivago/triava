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

package com.trivago.examples;

import com.trivago.triava.tcache.EvictionPolicy;
import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.CacheLoader;
import com.trivago.triava.tcache.core.FreezingEvictor;
import com.trivago.triava.tcache.eviction.Cache;
import com.trivago.triava.tcache.eviction.TCacheHolder;

/**
 * Examples for creating various types of Caches. Demonstrated features are put() and get() operations,
 * selecting a eviction strategy, and how to use a Loader.
 * @author cesken
 *
 */
public class CacheExample
{
	public static void main(String[] args)
	{
		
		TCacheFactory factory = TCacheFactory.standardFactory();
//		Builder<Integer, String> builder = factory.builder();
//		Cache<Integer, String> cache = builder.build();
//		Cache.setLogger(new TriavaConsoleLogger());  // For some logs to the console, uncomment this line
		
		exampleCacheGetWithLoader(factory);
		exampleCacheGetWithoutLoader(factory);
		exampleCachePut(factory);
		exampleCachePutWithEvictionLRU(factory);
		exampleCachePutWithEvictionLFU(factory);
		
		for (Cache<?, ?> cache : factory.instances())
		{
			System.out.println(cache.statistics());
		}
		
		factory.close();
	}


	private static void exampleCachePut(TCacheFactory factory)
	{
		Builder<Integer, String> builder = factory.builder();
		builder.setId("exampleCachePut").setExpectedMapSize(10);
		Cache<Integer, String> cache = builder.build();
		
		putElements(cache,10);
	}

	private static void exampleCachePutWithEvictionLRU(TCacheFactory factory)
	{
		Builder<Integer, String> builder = factory.builder();
		builder.setId("exampleCachePutWithEvictionLRU").setExpectedMapSize(5).setEvictionPolicy(EvictionPolicy.LRU);
		Cache<Integer, String> cache = builder.build();
		
		putElements(cache,10);
	}
	
	private static void exampleCachePutWithEvictionLFU(TCacheFactory factory)
	{
		Builder<Integer, String> builder = factory.builder();
		builder.setId("exampleCachePutWithEvictionLFU").setExpectedMapSize(5).setEvictionPolicy(EvictionPolicy.LFU);
		Cache<Integer, String> cache = builder.build();
		
		putElements(cache,10);
	}

	/**
	 * Put couint elements in the cache, then get and show them. Addtionally show the Cache Statistics.  
	 * @param cache
	 * @param count
	 */
	private static void putElements(Cache<Integer, String> cache, int count)
	{
		System.out.println("--- " + cache.id() + "---");
		for (int i=0; i<count; i++)
		{
			cache.put(i, "Manual put of " + i);
			if (i%3 == 0)
			{
				cache.get(i); // increase usage counters
			}
		}
		for (int i=0; i<count; i++)
		{
			System.out.println(i + ":" + cache.get(i));
		}

		System.out.println(cache.statistics());
		System.out.println();
	}


	private static void exampleCacheGetWithoutLoader(TCacheFactory factory)
	{
		Builder<Integer, String> builder = factory.builder();
		builder.setId("exampleCacheGetWithoutLoader");
		Cache<Integer, String> cache = builder.build();
		
		getElements(cache);
	}



	private static void getElements(Cache<Integer, String> cache)
	{
		System.out.println("--- " + cache.id() + "---");
		for (int i=0; i<10; i++)
		{
			System.out.println(i + ":" + cache.get(i));
		}
		System.out.println(cache.statistics());
		System.out.println();
	}



	private static void exampleCacheGetWithLoader(TCacheFactory factory)
	{
		Builder<Integer, String> builder = factory.builder();
		builder.setId("exampleCacheGetWithLoader").setLoader(new IntToStringLoader());
		Cache<Integer, String> cache = builder.build();
		
		getElements(cache);
	}



	static class IntToStringLoader implements CacheLoader<Integer,String>
	{
		@Override
		public String load(Integer value) throws Exception
		{
			return "Value " + value; // using implict StringBuilder
		}
		
	}
	
	/**
	 * Example for a custom eviction implementation.
	 */
	static class CustomerClassEvictor extends FreezingEvictor<Integer, CustomerType>
	{
		@Override
		public long getFreezeValue(Integer userId, TCacheHolder<CustomerType> customerType)
		{
			return customerType.peek().getPriority();
		}
	}
	
	enum CustomerType
	{
		Guest(0), Registered(5), Premium(9);
		
		public int getPriority() { return priority;	}

		int priority;
		CustomerType(int priority) { this.priority = priority; };
		
	}

}
