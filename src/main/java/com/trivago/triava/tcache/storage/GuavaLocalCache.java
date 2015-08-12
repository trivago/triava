//package com.trivago.triava.tcache.storage;
//
//import java.lang.reflect.Constructor;
//import java.util.concurrent.ConcurrentMap;
//import java.util.concurrent.TimeUnit;
//
//import com.google.common.cache.CacheBuilder;
//import com.google.common.cache.CacheLoader;
//
//import com.trivago.triava.annotations.Alpha;
//import com.trivago.triava.tcache.core.Builder;
//import com.trivago.triava.tcache.core.StorageBackend;
//import com.trivago.triava.tcache.eviction.Cache;
//
///**
// * <b>DO NOT USE THIS FOR PRODUCTIVE CODE. IT USES A NON-PUBLIC GUAVA CLASS.</b>
// * <p>
// * Implements a storage that uses Guava's LocalCache. It is only implemented for performance tests. 
// * <p>
// * <b>DO NOT USE THIS FOR PRODUCTIVE CODE. IT USES A NON-PUBLIC GUAVA CLASS.</b>
// *  
// * @author cesken
// *
// * @param <K>
// * @param <V>
// */
//@Alpha(comment="Has a reference to com.google.common.cache.CacheBuilder. We should not depend on Guava. Also it uses com.google.common.cache.LocalCache, which is a strictly private class within Guava.")
//public class GuavaLocalCache<K,V> implements StorageBackend<K, V>
//{
//	@Override
//	public ConcurrentMap<K, Cache.AccessTimeObjectHolder<V>> createMap(Builder<K,V> builder, double evictionMapSizeFactor)
//	{
//		try
//		{
//			CacheBuilder<Object, Object> guavaBuilder = CacheBuilder.newBuilder().maximumSize(builder.getExpectedMapSize())
//					.expireAfterWrite(builder.getMaxCacheTime(), TimeUnit.SECONDS);
//			Class<?> guavaCacheClass = Class.forName("com.google.common.cache.LocalCache");
//	
//			// Create the non-public class by reflection, making the Constructor public.
//			// This way we can compare the TCache performance using different storages (CHM vs LocalCache vs ...)
//			Constructor<?> cons = guavaCacheClass.getDeclaredConstructor(CacheBuilder.class, CacheLoader.class);
//			cons.setAccessible(true);
//			@SuppressWarnings({ "unchecked", "rawtypes" })
//			ConcurrentMap<K, Cache.AccessTimeObjectHolder<V>> inst = (ConcurrentMap) cons.newInstance(guavaBuilder, null);
//			return inst;
//		}
//		catch (Exception exc)
//		{
//			throw new RuntimeException("Cannot create map for HashImplementation=" + builder.getHashImplementation(), exc);
//		}
//		
//	}
//
//}
