package com.trivago.triava.tcache.action;

public class WriteThroughActionRunner extends ActionRunner
{
	@Override
	public
	boolean preMutate(Action<?,?,?> action)
	{
		action.writeThrough();
		return action.successful();
	}

	@Override
	public
	void postMutate(Action<?,?,?> action, boolean mutated, Object... args)
	{
		if (mutated)
		{
			action.statistics();
			action.notifyListeners(args);
		}
		action.close();
	}

}
