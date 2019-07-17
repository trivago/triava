package com.trivago.triava.tcache.core;

/**
 *
 */
public interface CacheMetadataInterface {
    /**
     * Returns the number of entries currently present in the Cache.
     * @return number of elements
     */
    int elementCount();
    /**
     * Returns the weight of the Cache, as sum of all cache entries
     * @return weight of the Cache
     */
    long weight();
}
