package com.trivago.triava.tcache.weigher;

import com.trivago.triava.tcache.eviction.EvictionTargets;

/**
 * Classes that implement this interface provide weight management to the Cache. Values can be added and removed.
 * Implementations are free how to weigh values. Usually they will use a provided {@link Weigher}, but they can also
 * simply use a constant of 1 for any value.
 * @param <V> The value type
 */
public interface WeightLimiter<V> {
    /**
     * Adds the weight of the given value to this WeightLimiter
     * @param value the value
     * @return TODO should we support a return value?
     */
    boolean add(V value);

    /**
     * Removes the weight of the given value from this WeightLimiter
     * @param value the value
     * @return the removed weight
     */
    int remove(V value);

    /**
     * Returns whether this cache is full. This means it has reached its nominal capacity.
     * @return true if this cache is full
     */
    boolean isFull();

    /**
     * Returns whether this cache is overfull. This means it has reached its overfill capacity. A Cache should not add
     * more entries if this method returns true.
     * @return true if this cache is overfull
     */
    boolean isOverfull();

    /**
     * Determine how much weight to remove. The goal is to reach a situation where {@link #isFull()} returns false.
     * It is up to the implementation to decide how much to actually remove. For example it can always remove 1, or
     * try to remove in batches for efficiency.
     *
     * @return The weight to remove
     */
    long weightToRemove();

    EvictionTargets evictionBoundaries();

    /**
     * Returns the current weight, as a sum of all added (and not yet removed) values
     * @return the current weight
     */
    long weight();
}
