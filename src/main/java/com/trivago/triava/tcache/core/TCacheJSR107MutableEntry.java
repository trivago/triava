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

	public enum Operation { NOP, REMOVE, SET }
	private Operation operation = Operation.NOP;
	V valueNew = null;
	
	/**
	 * Creates an instance based on the native tCache entry plus the key.
	 * 
	 * @param key The key
	 * @param value The value
	 */
	public TCacheJSR107MutableEntry(K key, V value)
	{
		super(key, value);
		this.valueNew = value;
	}

	@Override
	public boolean exists()
	{
		return valueNew != null;
	}

	@Override
	public void remove()
	{
		valueNew = null;
		if (oldValue() != null)
			operation = Operation.REMOVE;
		else
			operation = Operation.NOP;  // Was not set before, thus means DELETE is actually a NOP
	}

	@Override
	public V getValue()
	{
		// Overriding, to return the possibly mutated value
		return this.valueNew;
	}
	
	@Override
	public void setValue(V value)
	{
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