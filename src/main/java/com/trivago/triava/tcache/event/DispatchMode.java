package com.trivago.triava.tcache.event;

public enum DispatchMode
{
	/**
	 * Synchronous event with each generated event. Excactly one event per dispatch
	 */
	SYNC,
	/**
	 * Asynchronous, time based event dispatching.
	 */
	ASYNC_TIMED,
	/**
	 * Multiple events per dispatch.
	 */
	COUNTED
}