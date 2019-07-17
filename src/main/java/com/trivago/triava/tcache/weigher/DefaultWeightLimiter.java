package com.trivago.triava.tcache.weigher;

import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.eviction.EvictionTargets;

import java.util.concurrent.atomic.LongAdder;

public class DefaultWeightLimiter<V> implements WeightLimiter<V> {
    private volatile EvictionTargets evictionTargets;

    private final LongAdder overallWeight = new LongAdder();
    private final String cacheId;
    private final Weigher<V> weigher;

    public DefaultWeightLimiter(Builder<?, V> builder, Weigher<V> weigher) {
        this.cacheId = builder.getId();
        this.weigher = weigher;
        evictionTargets = new EvictionTargets(builder.getMaxElements(), builder.getMaxWeight());
    }

    /**
     * Returns whether the Cache is full. We declare the Cache full, when it has reached the number of expected
     * elements, even though there may be some extra eviction space available.
     *
     * @return true, if the cache is full
     */
    @Override
    public boolean isFull() {
        return overallWeight.longValue() >= evictionTargets.maxWeight();
    }

    /**
     * Returns whether the cache is overfull. We declare the Cache full, when it has reached the blocking position.
     *
     * @return true, if the cache is overfull
     */
    @Override
    public boolean isOverfull() {
        return overallWeight.longValue() >= evictionTargets.blockingWeight();
    }

    @Override
    public boolean add(V value) {
        return addOrSubtract(weigher.weight(value));
    }

    public boolean addOrSubtract(int weightDiff) {
        long targetWeight = overallWeight.longValue() + weightDiff;
        overallWeight.add(weightDiff);
        if (targetWeight <= evictionTargets.maxWeight()) {
            return true;
        }
        return false;
    }

    @Override
    public int remove(V value) {
        int removedWeight = weigher.weight(value);
        overallWeight.add(-removedWeight);
        return removedWeight;
    }

    @Override
    public long weightToRemove()
    {
        long currentWeight = overallWeight.longValue();
        long maxWeight = evictionTargets.maxWeight();
        if (currentWeight < maxWeight)
        {
            // [0, userDataElements-1] means: Not full. Nothing to evict.
            return 0;
        }

        // ----------------------------------------------------------

        long targetWeight = currentWeight - evictionTargets.evictNormallyWeight();
        if (targetWeight > maxWeight)
        {
            // Evict will reach [userDataElements, MAX_INT] : Evicting not enough
            targetWeight = maxWeight - evictionTargets.evictNormallyWeight();
        }

        // targetWeight in now in the interval [-MAX_INT,  userDataElements-1]
        if (targetWeight < 0) {
            // Evict will reach [0, evictUntilAtLeast-1] : Too much
            targetWeight = 0; // TODO Add something again similar to evictUntilAtLeast. For now just make sure we can
            // reach the target (negative weight cannot be readched, but 0 can)
        }

        // else: Make sure we make room for at least until evictUntilAtLeast
        long weightToRemove = currentWeight - targetWeight;

        return weightToRemove < 0 ? 0 : weightToRemove;
    }

    @Override
    public EvictionTargets evictionBoundaries() {
        return evictionTargets;
    }

    @Override
    public long weight() {
        return overallWeight.longValue();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("DefaultWeightLimiter{");
        sb.append("evictionTargets=").append(evictionTargets);
        sb.append(", overallWeight=").append(overallWeight);
        sb.append(", cacheId='").append(cacheId).append('\'');
        sb.append(", weigher=").append(weigher);
        sb.append('}');
        return sb.toString();
    }
}
