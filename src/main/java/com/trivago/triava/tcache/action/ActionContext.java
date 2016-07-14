package com.trivago.triava.tcache.action;

import javax.cache.integration.CacheWriter;

import com.trivago.triava.tcache.event.ListenerCollection;
import com.trivago.triava.tcache.statistics.StatisticsCalculator;

public interface ActionContext<K,V>
{
	CacheWriter<K, V> cacheWriter();
	ListenerCollection<K, V> listeners();
	StatisticsCalculator statisticsCalculator();
}
