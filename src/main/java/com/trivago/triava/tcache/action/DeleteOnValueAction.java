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

public class DeleteOnValueAction<K,V,W> extends DeleteAction<K,V,W>
{
	final boolean writeThrough;
	
	public DeleteOnValueAction(K key, boolean writeThrough)
	{
		super(key);
		this.writeThrough = writeThrough;
	}

	/**
	 * Cache.remove(key, value) should not write through. There is not even a 2-arg version of CacheWriter.delete(key, value).
	 * Also org.jsr107.tck.integration.CacheWriterTest#shouldWriteThroughRemove_SpecificEntry() enforces that no write-through is being done.
	 * at org.jsr107.tck.integration.CacheWriterTest.shouldWriteThroughRemove_SpecificEntry(CacheWriterTest.java:808)
	 */
	@Override
	W writeThroughImpl(ActionRunner<K,V> actionRunner, Object arg)
	{
		if (writeThrough)
			return super.writeThroughImpl(actionRunner, arg);
		else
			return null;
	}
	
	@Override
	void statisticsImpl(ActionRunner<K, V> actionRunner, Object arg)
	{
		// DeletOnValue is a "delete-if".Thus we need hit counting for the "if value"
		if (removed)
		{
			actionRunner.stats.incrementHitCount();
		}
		else
			actionRunner.stats.incrementMissCount();
		
		super.statisticsImpl(actionRunner, arg);
	}
}
