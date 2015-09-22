package com.trivago.triava.tcache.eviction;

public interface TCacheHolder<V>
{
	V get();
	V peek();
	long getLastAccess();
	int getUseCount();
	long getInputDate();
	int getMaxIdleTime();
	boolean isInvalid();
}
