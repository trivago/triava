package com.trivago.triava.tcache.util;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.spi.CachingProvider;

public class TestUtils {
    public static CacheManager getDefaultCacheManager()
    {
        CachingProvider cachingProvider = Caching.getCachingProvider();
        return cachingProvider.getCacheManager();
    }

    public static Cache<Integer, Integer> createJsrCacheIntInt(String name, CompleteConfiguration configuration) {
        CacheManager cm = getDefaultCacheManager();
        final javax.cache.Cache<Integer, Integer> cache = cm.createCache(name, configuration);
        return cache;
    }

    public static void destroyJsrCache(String name) {
        getDefaultCacheManager().destroyCache(name);
    }
}
