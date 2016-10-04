/*********************************************************************************
 * Copyright 2015-present trivago GmbH
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

package com.trivago.triava.tcache;

/**
 * Defines how entries are put in the cache. Either they are put using the reference of the instance (Identity),
 * cloned via clone() (Clone) or interned similar to {@link String#intern()}  (Intern). Call {@link #isStoreByValue()}
 * to determine whether the store method is store-by-value.
 * 
 * @author cesken
 *
 */
public enum CacheWriteMode
{
	Identity(false),
	/**
	 * Best-effort serialization, e.g. using Serializable or Externizable
	 */
	Serialize(true),
	Intern(false);
	
	final boolean jsr107compatibleStoreByValue;
	
	CacheWriteMode(boolean jsr107compatibleCopying)
	{
		this.jsr107compatibleStoreByValue = jsr107compatibleCopying;
	}

	/**
	 * Converts the JSR isStoreByValue to the corresponding CacheWriteMode.
	 * 
	 * @param storeByValue true for store by value, false otherwise
	 * @return The corresponding CacheWriteMode
	 */
	public static CacheWriteMode fromStoreByValue(boolean storeByValue)
	{
		return storeByValue ? Serialize : Identity;
	}
	
	/**
	 * Returns whether this CacheWriteMode is using store-by-value as defined by JSR107.
	 * Only for {@link #Serialize} true is returned. Mode {@link #Identity} always shares Objects, and {@link #Intern} also most of the time.
	 * @return true, if the mode is to store by value
	 */
	public boolean isStoreByValue()
	{
		return jsr107compatibleStoreByValue;
	}
}
