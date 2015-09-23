package com.trivago.triava.tcache.eviction;


/**
 * Freezes all statistics that may change
 * @author cesken
 *
 */
public class HolderFreezer<K, V>
{
	private static int tiebreakerVal = (byte)System.currentTimeMillis();

	private final K key;
	private final TCacheHolder<V> holder;
	private final long frozenValue;
	private final int tiebreaker = tiebreakerVal++;

	
	HolderFreezer(K key, TCacheHolder<V> holder, long frozenValue)
	{
		this.key = key;
		this.holder = holder;
		this.frozenValue = frozenValue;
	}

	public K getKey()
	{
		return key;
	}

	public int getTiebreaker()
	{
		return tiebreaker;
	}

	public long getFrozenValue()
	{
		return frozenValue;
	}

	public TCacheHolder<V> getHolder()
	{
		return holder;
	}
}


