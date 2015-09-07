package com.trivago.examples;

import com.trivago.triava.tcache.EvictionPolicy;
import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.CacheLoader;
import com.trivago.triava.tcache.eviction.Cache;

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
//		Cache.setLogger(new TriavaConsoleLogger());  // For some logs to the console, uncomment this line
		
		exampleCacheGetWithLoader(factory);
		exampleCacheGetWithoutLoader(factory);
		exampleCachePut(factory);
		exampleCachePutWithEvictionLRU(factory);
		exampleCachePutWithEvictionLFU(factory);
		
		for (Cache<?> cache : factory.instances())
		{
			System.out.println(cache.statistics());
		}
		
		factory.shutdownAll();
	}


	private static void exampleCachePut(TCacheFactory factory)
	{
		Builder<Integer, String> builder = factory.<Integer,String>builder();
		builder.setId("exampleCachePut").setExpectedMapSize(10);
		Cache<String> cache = builder.build();
		
		putElements(cache,10);
	}

	private static void exampleCachePutWithEvictionLRU(TCacheFactory factory)
	{
		Builder<Integer, String> builder = factory.<Integer,String>builder();
		builder.setId("exampleCachePutWithEvictionLRU").setExpectedMapSize(5).setEvictionPolicy(EvictionPolicy.LRU);
		Cache<String> cache = builder.build();
		
		putElements(cache,10);
	}
	
	private static void exampleCachePutWithEvictionLFU(TCacheFactory factory)
	{
		Builder<Integer, String> builder = factory.<Integer,String>builder();
		builder.setId("exampleCachePutWithEvictionLFU").setExpectedMapSize(5).setEvictionPolicy(EvictionPolicy.LFU);
		Cache<String> cache = builder.build();
		
		putElements(cache,10);
	}

	/**
	 * Put couint elements in the cache, then get and show them. Addtionally show the Cache Statistics.  
	 * @param cache
	 * @param count
	 */
	private static void putElements(Cache<String> cache, int count)
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
		Builder<Integer, String> builder = factory.<Integer,String>builder();
		builder.setId("exampleCacheGetWithoutLoader");
		Cache<String> cache = builder.build();
		
		getElements(cache);
	}



	private static void getElements(Cache<String> cache)
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
		Builder<Integer, String> builder = factory.<Integer,String>builder();
		builder.setId("exampleCacheGetWithLoader").setLoader(new IntToStringLoader());
		Cache<String> cache = builder.build();
		
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
}
