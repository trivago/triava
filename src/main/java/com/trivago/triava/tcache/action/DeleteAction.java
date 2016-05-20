package com.trivago.triava.tcache.action;

import javax.cache.event.EventType;
import javax.cache.integration.CacheWriterException;

import com.trivago.triava.tcache.eviction.Cache;

public class DeleteAction<K,V,W> extends Action<K,V,W>
{

	public DeleteAction(K key, Cache<K,V> actionContext)
	{
		super(key, null, EventType.REMOVED, actionContext);
	}

	@Override
	W writeThroughImpl()
	{
		try
		{
			cacheWriter.delete(key);
		}
		catch (Exception exc)
		{
			writeThroughException = new CacheWriterException(exc);
		}
		return null;
	}

	@Override
	void notifyListenersImpl(Object... args)
	{
		@SuppressWarnings("unchecked")
		V oldValue = (V)(args[0]); // This can actually be oldValue or value
		listeners.dispatchEvent(eventType, key, oldValue);
	}

	@Override
	void statisticsImpl()
	{
		// TODO Move statistics from Cache to here for the Delete operation 
//		stats.incrementRemoveCount();
	}

}
