package com.trivago.triava.tcache.event;

public enum DispatchMode
{
	/**
	 * Synchronous event with each generated event. Excactly one event per dispatch
	 */
	SYNC(false),
	/**
	 * Asynchronous, time based event dispatching.
	 */
	ASYNC_TIMED(true),
	/**
	 * Multiple events per dispatch.
	 */
	COUNTED(true);

	boolean async;

	DispatchMode(boolean async)
	{
		this.async = async;
	}

	public boolean isAsync()
	{
		return async;
	}
}