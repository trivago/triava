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

public class DeleteOnValueAction<K,V,W> extends DeleteAction<K,V,W>
{
	V value;
	
	public DeleteOnValueAction(K key, Cache<K,V> actionContext)
	{
		super(key, actionContext);
	}

	/**
	 * Cache.remove(key, value) should not write through. There is not even a 2-arg version of CacheWriter.delete(key, value).
	 * Also org.jsr107.tck.integration.CacheWriterTest#shouldWriteThroughRemove_SpecificEntry() enforces that no write-through is being done.
	 * at org.jsr107.tck.integration.CacheWriterTest.shouldWriteThroughRemove_SpecificEntry(CacheWriterTest.java:808)
	 */
	@Override
	W writeThroughImpl()
	{
		return null;
	}
}
