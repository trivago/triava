package com.trivago.triava.tcache.util;

import com.trivago.triava.tcache.TCacheJSR107;

public class JCacheWrapper<K, V> implements BasicCacheInterface<K, V> {
    final TCacheJSR107<K, V> cache;

    public JCacheWrapper(TCacheJSR107<K, V> cache) {
        this.cache = cache;
    }

    @Override
    public V get(K key) {
        return cache.get(key);
    }

    @Override
    public V getAndPut(K key, V value) {
        return cache.getAndPut(key, value);
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }
}
