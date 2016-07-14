package com.trivago.triava.tcache.action;

import javax.cache.event.EventType;

public class ReplaceAction<K, V, W> extends PutAction<K, V, W>
{

	public ReplaceAction(K key, V value, EventType eventType)
	{
		super(key, value, eventType, true);
	}

	@Override
	void statisticsImpl(ActionRunner<K, V> actionRunner, Object... args)
	{
		boolean changed = Boolean.TRUE.equals(args[0]);
		if (changed)
		{
			// TODO Implement and add to replace methods
		}
		super.statisticsImpl(actionRunner, args);
	}
}
