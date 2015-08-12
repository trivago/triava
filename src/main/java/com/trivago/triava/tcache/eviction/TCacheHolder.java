package com.trivago.triava.tcache.eviction;

public interface TCacheHolder<V>
{
	
	public V get();
	public V peek();
	public long getLastAccess();
	public int getUseCount();
	public long getInputDate();
	public int getMaxIdleTime();
	public boolean isInvalid();
}
