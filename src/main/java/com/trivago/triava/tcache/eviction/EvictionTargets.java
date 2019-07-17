package com.trivago.triava.tcache.eviction;

/**
 * This class holds targets for the eviction picker. This includes how many elements the Cache is supposed to have,
 * its maximum weight, how much overfill is allowed, and how much should be evicted for batch-evictions.
 */
public class EvictionTargets {
    private static final double EVICT_FACTOR = 0.1d; // Future directions: Move to instance. Add to Builder.
    private static final double OVERFILL_FACTOR = 0.15d; // Future directions: Move to instance. Add to Builder.


    private final int maxElements;
    private final int blockingElements;

    private final long evictNormallyWeight;
    private final long maxWeight;
    private final long blockingWeight;

    public EvictionTargets(int maxElements, long maxWeight) {
        if (maxElements == 0 && maxWeight == 0) {
            throw new IllegalArgumentException("Both maxElements and maxWeight are 0. At least one of it must be set.");
        }
        if (maxElements == 0) {
            maxElements = (int)Math.max(maxWeight, Integer.MAX_VALUE);
        }
        if (maxWeight == 0) {
            maxWeight = maxElements;
        }

        this.maxElements = maxElements;
        this.maxWeight = maxWeight;
        this.evictNormallyWeight = safeMultiply(maxWeight, EVICT_FACTOR, 1, Long.MAX_VALUE);

        long overfillWeight = safeMultiply(maxWeight, OVERFILL_FACTOR, 0, Long.MAX_VALUE);
        this.blockingWeight = maxWeight + overfillWeight;

        int overfillElements = (int)safeMultiply(maxElements, OVERFILL_FACTOR, 0, Integer.MAX_VALUE);
        this.blockingElements = maxElements + overfillElements;
    }

    public int maxElements() {
        return maxElements;
    }

    public int blockingElements() {
        return blockingElements;
    }

    public long maxWeight() {
        return maxWeight;
    }

    public long blockingWeight() {
        return blockingWeight;
    }

    public long evictNormallyWeight() {
        return evictNormallyWeight;
    }

    private long safeMultiply(long value, double multiplier, long min, long max) {
        double result = (double)value * multiplier;
        if (result < min) {
            return min;
        }
        if (result > max) {
            return max;
        }
        return (long)result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("EvictionTargets{");
        sb.append("maxElements=").append(maxElements);
        sb.append(", blockingElements=").append(blockingElements);
        sb.append(", evictNormallyWeight=").append(evictNormallyWeight);
        sb.append(", maxWeight=").append(maxWeight);
        sb.append(", blockingWeight=").append(blockingWeight);
        sb.append('}');
        return sb.toString();
    }
}
