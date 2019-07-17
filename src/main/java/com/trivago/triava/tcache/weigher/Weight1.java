package com.trivago.triava.tcache.weigher;

/**
 * A weigher that returns a constant weight of 1 for all values. If a Cache uses this weigher, its overall weight
 * equals the number of cache entries.
 */
public class Weight1 implements Weigher {
    @Override
    public int weight(Object value) {
        return 1;
    }
}
