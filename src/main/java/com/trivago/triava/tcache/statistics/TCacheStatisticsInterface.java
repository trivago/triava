package com.trivago.triava.tcache.statistics;

/**
 * 
 * @author cesken
 *
 */
public interface TCacheStatisticsInterface
{
	void setHitCount(long count);
	void setMissCount(long count);
	void setPutCount(long count);
	void setEvictionCount(long count);
	void setEvictionRounds(long count);
	void setEvictionHalts(long count);
	void setEvictionRate(long rate);
	void setHitRatio(float count);
	void setElementCount(long count);
	void setDropCount(long dropCount);	
}
