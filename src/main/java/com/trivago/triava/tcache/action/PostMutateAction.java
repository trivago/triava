/*********************************************************************************
 * Copyright 2016-present trivago GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **********************************************************************************/

package com.trivago.triava.tcache.action;

import com.trivago.triava.tcache.util.ChangeStatus;

/**
 * @deprecated Migrate to {@link ChangeStatus}
 * @author cesken
 *
 */
@Deprecated
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
