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

import com.trivago.triava.tcache.eviction.Cache;

/**
 * An ActionRunner with the following behavior:
 * <ul>
 * <li> preMutate() : NOP</li> 
 * <li> postMutate() : If mutated: statistics, notifyListeners, writeThrough</li>
 * </ul>
 * @author cesken
 *
 */

public class WriteBehindActionRunner<K,V> extends ActionRunner<K,V>
{
	public WriteBehindActionRunner(Cache<K,V> actionContext)
	{
		super(actionContext);
	}
	
	@Override
	public boolean preMutate(Action<K,V,?> action)
	{
		return true;
	}

	@Override
	public void postMutate(Action<K,V,?> action, PostMutateAction mutateAction, Object... args)
	{
		if (stats != null && mutateAction.runStatistics())
			action.statistics(this, args);
		if (listeners != null && mutateAction.runListener())
			action.notifyListeners(this, args);
		if (cacheWriter != null && mutateAction.runWriteThrough())
			action.writeThrough(this); // Should run async and possibly batched for efficient write-behind
		action.close();
	}
}
