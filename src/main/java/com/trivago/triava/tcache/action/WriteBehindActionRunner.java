package com.trivago.triava.tcache.action;

public class WriteBehindActionRunner extends ActionRunner
{
	@Override
	public
	boolean preMutate(Action<?,?,?> action)
	{
		return true;
	}

	@Override
	public
	void postMutate(Action<?,?,?> action, boolean mutated, Object... args)
	{
		if (mutated)
		{
			action.statistics();
			action.notifyListeners(args);
			action.writeThrough(); // Should run async and possibly batched for efficient write-behind
		}
	}
}
