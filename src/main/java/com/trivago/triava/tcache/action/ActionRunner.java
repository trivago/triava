package com.trivago.triava.tcache.action;

public abstract class ActionRunner
{
	public abstract boolean preMutate(Action<?,?,?> action);
	public abstract void postMutate(Action<?,?,?> action, boolean removed, Object... args);
}
