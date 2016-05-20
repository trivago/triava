package com.trivago.triava.tcache.action;

import javax.cache.event.EventType;
import javax.cache.integration.CacheWriterException;

import com.trivago.triava.tcache.core.TCacheJSR107Entry;
import com.trivago.triava.tcache.eviction.Cache;

public class PutAction<K,V,W> extends Action<K,V,W>
{

	public PutAction(K key, V value, EventType eventType, Cache<K,V> actionContext)
	{
		super(key, value, eventType, actionContext);
	}

	@Override
	W writeThroughImpl()
	{
		try
		{
			cacheWriter.write(new TCacheJSR107Entry<K,V>(key, value));
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
		listeners.dispatchEvent(eventType, key, value);
	}

	@Override
	void statisticsImpl()
	{
		stats.incrementPutCount();
	}

}
