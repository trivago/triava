package com.trivago.triava.tcache.action;

import javax.cache.event.EventType;

import com.trivago.triava.tcache.statistics.StatisticsCalculator;
import com.trivago.triava.tcache.util.ChangeStatus;

/**
 * An Action that has both Get and Put characteristics, like getAndPut(), and the 3-arg replace().
 * Should be renamed to GetPutAction.
 *  
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 * @param <W> The return type of the writeThroughImpl (unused)
 */
public class GetAndPutAction<K, V, W> extends PutAction<K, V, W>
{

	public GetAndPutAction(K key, V value, EventType eventType)
	{
		super(key, value, eventType, true);
	}

	@Override
	void statisticsImpl(ActionRunner<K, V> actionRunner, Object arg)
	{
		StatisticsCalculator stats = actionRunner.stats;
		ChangeStatus changeStatus = (ChangeStatus) arg;
		System.out.println("actionRunnter " + actionRunner + ", cs=" +changeStatus);
		switch (changeStatus)
		{
			case CHANGED:
				stats.incrementHitCount();
//				stats.incrementPutCount(); // cache.putToMap() still does it. Thus do not increment here
				break;
//			case UNCHANGED:
//				stats.incrementMissCount();
//				break;
			case CREATED:
				System.out.println("actionRunnter " + actionRunner + ", cs=" +changeStatus + ", stats B:" + stats);
				stats.incrementMissCount();
//				stats.incrementPutCount(); // cache.putToMap() still does it. Thus do not increment here
				System.out.println("actionRunnter " + actionRunner + ", cs=" +changeStatus + ", stats A:" + stats);
				break;
//			case CAS_FAILED:
//				stats.incrementHitCount();
//				break;
		}
	}
}
