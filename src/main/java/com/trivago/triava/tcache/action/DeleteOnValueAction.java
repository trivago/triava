package com.trivago.triava.tcache.action;

import com.trivago.triava.tcache.eviction.Cache;

public class DeleteOnValueAction<K,V,W> extends DeleteAction<K,V,W>
{
	V value;
	
	public DeleteOnValueAction(K key, Cache<K,V> actionContext)
	{
		super(key, actionContext);
	}

	/**
	 * Cache.remove(key, value) should not write through. There is not even a 2-arg version of CacheWriter.delete(key, value).
	 * Also org.jsr107.tck.integration.CacheWriterTest#shouldWriteThroughRemove_SpecificEntry() enforces that no write-through is being done.
	 * at org.jsr107.tck.integration.CacheWriterTest.shouldWriteThroughRemove_SpecificEntry(CacheWriterTest.java:808)
	 */
	@Override
	W writeThroughImpl()
	{
		return null;
	}
}
