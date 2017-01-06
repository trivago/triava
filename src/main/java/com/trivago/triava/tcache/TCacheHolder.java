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

import java.io.Serializable;

/**
 * Represents a Cache value with associated metadata like use count and access count.
 * 
 * @author cesken

 * @param <V> The value type
 */
public interface TCacheHolder<V> extends Serializable
{
	/**
	 * Returns the value, that this holder holds. <b>Statistics effects</b>: Updates the access time and the use count.
	 * @return The value
	 */
	V get();
	
	/**
	 * Returns the value, that this holder holds. This method differs from {@link #get()} in that peek has no side effects on entry statistics.
	 * @return The value
	 */
	V peek();
	
	/**
	 * Returns the last access time, given in MILLISECONDS since EPOCH
	 * @return the last access time
	 */
	long getLastAccessTime();
	
	/**
	 * Returns the number of times this holder was accessed.
	 * @return the use count
	 */
	int getUseCount();
	
	/**
	 * Returns the creation time of this holder, given in ms since EPOCH.
	 * The creation time represents the time the holder was put into the cache. Any put including replacing an existing entry
	 * will create a new holder with the current time as creation time.  
	 * @return The creation time in ms
	 */
	long getCreationTime(); 
	
	/**
	 * Returns the expiration time, given in ms since EPOCH.
	 * @return The expiration time in ms
	 */
	long getExpirationTime();
	
	/**
	 * Returns whether this holder is valid. A formerly valid holder can become invalid when it expires, and this method reflects that: An expired
	 * holder is invalid.    
	 * @return true if it is invalid. This includes expired holders.
	 */
	boolean isInvalid();
}
