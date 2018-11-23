package com.trivago.triava.tcache.util;

/**
 * A Weigher weighs an Object. It is used for caches with a weight limit. For other caches a Weigher is ignored,
 * this includes non-limited caches and caches limited by element count. Implementations are free to return any
 * exact or estimated weight, as long as  they following the general contract.
 * <ol>
 *     <li>If Object h is heavier than l, then h.weight() >= l.weight(). Typically an implementation SHOULD do
 *     h.weight() > l.weight()</li>
 *     <li>h.weight() > 0</li>
 * </ol>
 */
public interface Weigher {
    /**
     * Returns a positive weight for the implemented Object. See the class description for the general contract
     * of a Weigher. Any implementation should be lightweight, as the weight() message may be called frequently
     * by the cache on insertion, removal, and multiple times on demand for eviction, expiration, statistics or other
     * administrative purposes.
     * If this method is slow it may slow down the whole cache. In such a case, you may want to keep a cached
     * copy of the weight, similar to the cached hashCode in {@link String}.
     * @return The weight of this Object.
     */
    int weight();
}
