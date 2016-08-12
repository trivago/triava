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

/**
 * An ActionRunner with the following behavior:
 * <ul>
 * <li> preMutate() : writeThrough</li>
 * <li> postMutate() : If mutated: statistics, notifyListeners</li>
 * </ul>
 * @author cesken
 *
 */
public class WriteThroughActionRunner<K,V> extends ActionRunner<K,V>
{
	public WriteThroughActionRunner(ActionContext<K,V> actionContext)
	{
		super(actionContext);
	}
	
	@Override
	public boolean preMutate(Action<K,V,?> action, Object arg)
	{
		if (cacheWriter != null)
			action.writeThrough(this, arg);
		return action.successful();
	}

	@Override
	public void postMutate(Action<K, V, ?> action, Object arg)
	{
		action.statistics(this, arg);
		action.notifyListeners(this, arg);
		action.close();
	}

}
