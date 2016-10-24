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

package com.trivago.triava.tcache.core;

import java.io.Serializable;

import javax.cache.integration.CacheLoader;
import javax.cache.processor.MutableEntry;

/**
 * tCache implementation of {@link javax.cache.processor.MutableEntry}. Any mutable changes have no direct impact, but
 * they will be applied after after the EntryProcessor has returned.
 * 
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public final class TCacheJSR107MutableEntry<K, V> extends TCacheJSR107Entry<K, V> implements MutableEntry<K, V>, Serializable
{
	private static final long serialVersionUID = 6472791726900271842L;

	public enum Operation { NOP, REMOVE, SET, LOAD, REMOVE_WRITE_THROUGH, GET }
	private Operation operation = Operation.NOP;
	V valueNew = null;
	final CacheLoader<K, V> loader; // TODO Must be Serializable, as TCacheJSR107MutableEntry is Serializable. How does the RI do it?
	
	/**
	 * Creates an instance based on the native tCache entry plus the key.
	 * 
	 * @param key The key
	 * @param value The value
	 * @param loader The CacheLoader, or null if there is no CacheLoader
	 */
	public TCacheJSR107MutableEntry(K key, V value, CacheLoader<K, V> loader)
	{
		super(key, value);
		this.valueNew = value;
		this.loader = loader;
	}

	@Override
	public boolean exists()
	{
		return valueNew != null;
	}

	@Override
	public void remove()
	{
		if (operation == Operation.SET || operation == Operation.LOAD)
		{
			operation = Operation.NOP;
		}
		else
		{
			if (oldValue() != null)
				operation = Operation.REMOVE;
			else
			{
				// TCK Challenge
				// CacheWriterTest.shouldWriteThroughUsingInvoke_setValue_CreateEntryThenRemove()
				// It demands that no action (INCLUDING write-through) is done, if the State-transistion is:
				// SURROGATE -> SET -> REMOVED     : Result NOP
				// SURROGATE -> REMOVED            : Result REMOVE_WRITE_THROUGH
				// I do not see why we should skip write-through in this case
				if (valueNew != null)
					operation = Operation.NOP;
				else
				{
					// Was not set before, thus means DELETE is actually a NOP. Write-through to propagate this
					operation = Operation.REMOVE_WRITE_THROUGH;
				}
			}
		}

		valueNew = null;
	}

	@Override
	public V getValue()
	{
		if (valueNew == null && loader != null)
		{
			valueNew = loader.load(key);
			operation = Operation.LOAD; // treat load and set the same
		}
		
		if (operation == Operation.NOP)
			operation = Operation.GET; // (Only) remember the GET if we do not have more relevant operations 
		return this.valueNew;
	}
	
	@Override
	public void setValue(V value)
	{
		if (value == null)
			throw new NullPointerException("value is null in setValue()");
		
		operation = Operation.SET;
		this.valueNew = value;
	}

	private V oldValue()
	{
		return value;
	}
	
	/**
	 * Returns the operation that should be performed on this MutableEntry after the EntryProcessor has returned.
	 * A entry that was not changed by an EntryProcessor returns {@link Operation#NOP}, which means no operation
	 * should take place
	 *  
	 * @return The desired Operation
	 */
	public Operation operation()
	{
		return operation;
	}
}