package com.trivago.triava.tcache.statistics;

import javax.cache.management.CacheStatisticsMXBean;

public interface TCacheStatisticsMXBean extends CacheStatisticsMXBean
{
	int getSize();
}
