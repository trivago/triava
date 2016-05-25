package com.trivago.triava.tcache.action;

public class PostMutateAction
{
	public static final PostMutateAction STATS_ONLY = new PostMutateAction(false, false, false);
	public static final PostMutateAction WRITETHROUGH_ONLY = new PostMutateAction(false, true, false);
	public static final PostMutateAction ALL = new PostMutateAction(true, true, true);

	private final boolean listener;
	private final boolean writeThrough;
	private final boolean statistics;

	PostMutateAction(boolean statistics, boolean writeThrough, boolean listener)
	{
		this.statistics = statistics;
		this.writeThrough = writeThrough;
		this.listener = listener;
	}
	
	public static PostMutateAction statsOrAll(boolean mutated)
	{
		return mutated ? PostMutateAction.ALL : PostMutateAction.STATS_ONLY;
	}

	public boolean runListener()
	{
		return listener;
	}

	public boolean runWriteThrough()
	{
		return writeThrough;
	}

	public boolean runStatistics()
	{
		return statistics;
	}
}
