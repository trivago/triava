/*********************************************************************************
 * Copyright 2015-present trivago GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **********************************************************************************/

package com.trivago.triava.tcache.core;

import java.util.Comparator;

import com.trivago.triava.tcache.eviction.HolderFreezer;
import com.trivago.triava.tcache.eviction.TCacheHolder;

/**
 * Convenience class that implements EvictionInterface. The {@link #evictionComparator()} returns a default
 * Comparator, so custom implementations that extend FreezingEvictor only need to implement
 * {@link #getFreezeValue(Object, TCacheHolder)}
 * 
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
abstract public class FreezingEvictor<K, V> implements EvictionInterface<K, V>
{
	private StandardComparator comparator = new StandardComparator();

	/**
	 * Returns the standard comparator, which compares first the frozen values, and (on ties) uses the
	 * tiebreaker. When overriding this comparator, one can still use this functionality, by calling
	 * {@link #compareByFreezer(HolderFreezer, HolderFreezer, boolean)} in the own comparator.
	 */
	@Override
	public Comparator<HolderFreezer<K, V>> evictionComparator()
	{
		return comparator;
	}

	/**
	 * Default implementation for {@link EvictionInterface#beforeEviction()}. It does nothing.
	 */
	@Override
	public void beforeEviction()
	{	
	}
	
	/**
	 * Default implementation for {@link EvictionInterface#afterEviction()}. It does nothing.
	 */
	@Override
	public void afterEviction()
	{	
	}

	private class StandardComparator implements Comparator<HolderFreezer<K,V>>
	{
		@Override
		public int compare(HolderFreezer<K, V> o1, HolderFreezer<K, V> o2)
		{
			// Use standard comparison, including tie-breaker
			return compareByFreezer(o1, o2, true);
		}
	}


	/**
	 * Returns a value for the given key, that may be used by the concrete implementation of the #evictionComparator().
	 * The default comparator returned by FreezingEvictor#evictionComparator() chooses keys with smaller values
	 * to be evicted sooner than those with bigger values (comparable to {@link Comparator#compare(Object, Object)}).
	 * While it is recommended to conform to this, it is not required - own implementations may use any other strategy,
	 * but should document this clearly.
	 * 
	 * <p>
	 * There are three typical usage patterns:
	 * <ul>
	 * <li>MUST implement: There is a relevant mutable value, for example metadata like <i>use count</i> or the <i>last use timestamp</i>.
	 *   In this case you MUST implement this method.</li>
	 * <li>SHOULD implement: The relevant value is expensive to calculate. In this case you SHOULD implement this method for performance
	 * reasons, as the value is likely required many times during sorting.</li>
	 * <li>NOT USED: For other cases, this method should return 0.</li>
	 * </ul>
	 * If the implementing class does not require such a value, it should return 0. 
	 * Typical examples are element metadata like <i>use count</i> or the <i>last use timestamp</i>. Content-aware
	 * eviction implementations  can also derive a value from the data, like session status ("logged out", "idle").
	 * 
	 * @param key
	 * @param holder
	 * @return
	 */
	@Override
	public abstract long getFreezeValue(K key, TCacheHolder<V> holder);

	/**
	 * Compare objects by their frozen value {@link HolderFreezer#getFrozenValue()}. The returned value
	 * is negative, 0, or positive, similar to o1.getFrozenValue().compareTo(o2.getFrozenValue()).
	 * The tiebreaker in the HolderFreezer will be taken into account, if includeTiebreaker is set.
	 * 
	 * @param o1
	 * @param o2
	 * @param includeTiebreaker
	 * @return
	 */
	public int compareByFreezer(HolderFreezer<K, V> o1, HolderFreezer<K, V> o2, boolean includeTiebreaker)
	{
		// Check LFU value. Please note that it is taken from the "frozen" value, as the actual value may change
		// over the course of a sorting process.
		/**
		 * Example:
		 * o1.frozen = 2, o2.frozen = 5
		 *  =>  o2 has been used more often, and o1 should be evicted => sort o1 before o2
		 *  
		 *  frozenUsetimeCount = o1.frozenValue - o2.frozenValue;  // 2-5 = -3
		 *  Value is < 0 => o1 is smaller than o2 => o1 is put before o2, and is evicted first. 
		 */
		long frozenValueDiff = o1.getFrozenValue() - o2.getFrozenValue();
		if (frozenValueDiff != 0)
			return Long.signum(frozenValueDiff);

		if (!includeTiebreaker)
		{
			return 0;
		}
		
		int tiebreaker = o1.getTiebreaker() - o2.getTiebreaker();
		return tiebreaker;
	}


}
