package com.trivago.triava.tcache.eviction;

import com.trivago.triava.tcache.core.Builder;

/**
 * Implementation of a Least Frequently Used (LFU) Cache. Based on {@link Cache}.
 * 
 * @author cesken
 *
 * @param <T>
 */
public class CacheLimitLFUv2<T> extends CacheLimit<T>
{
	private static final long serialVersionUID = 6678178712081760715L;

	public CacheLimitLFUv2(Builder builder)
	{
		super(builder.setEvictionClass(new LFUEviction<Object,T>()));
	}

	/**
	 * @param maxIdleTime
	 *            Maximum idle time in seconds
	 * @param maxCacheTime
	 *            Maximum Cache time in seconds
	 * @param expectedElements
	 *            Size limit in number of elements
	 **/
	CacheLimitLFUv2(String id, long maxIdleTime, long maxCacheTime, int expectedElements)
	{
		super(id, maxIdleTime, maxCacheTime, expectedElements);
		evictionClass = new LFUEviction<>(); // TODO Move it to the super() call above
	}

}

