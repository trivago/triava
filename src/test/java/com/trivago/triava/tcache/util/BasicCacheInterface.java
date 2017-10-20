package com.trivago.triava.tcache.util;

/**
 * A basic cache interface, with common methods of the triava native interface and the JCache interface.
 * This eases unit testing both interaces in the same way.
 * @param <K> The Key class
 * @param <V> The Value class
 */
public interface BasicCacheInterface<K, V> {
    V get(K key);

    V getAndPut(K key, V value);

    void put(K key, V value);
}
