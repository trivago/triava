package com.trivago.triava.tcache.eviction;

import com.trivago.triava.tcache.core.Builder;

/**
 * Implementation of a Least Recently Used (LRU) Cache. Based on {@link Cache}.
 * 
 * @author cesken
 * @since March 2015
 * 
 * @param <T> Data (value) type
 */
public class CacheLimitLRU<T> extends CacheLimit<T>
{
	private static final long serialVersionUID = 6678178712081760715L;

	public CacheLimitLRU(Builder builder)
	{
		super(builder.setEvictionClass(new LRUEviction<>()));
	}
}
	
