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

package com.trivago.triava.tcache.util;

/**
 * Utility class to convert a millisecond value to an internal representation, with precision of either milliseconds or seconds. 
 */
public class SecondsOrMillis
{
	// Max millis is 1073741824 = 12,4 days. Any value below can be represented by itself
	final static int MILLIS_MAX = 0x40000000;
	
	/**
	 * Converts millis to internal value
	 * 
	 * @param millis Millis
	 * @return Internal value
	 */
	public static int fromMillisToInternal(long millis)
	{
		if (millis < 0)
			throw new IllegalArgumentException("millis must be not negative: " + millis);
		
		if (millis < MILLIS_MAX)
			return (int)millis;
		
		long seconds = -(millis / 1000);
		return seconds < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int)seconds;
	}

	/**
	 * Converts internal value to millis
	 * 
	 * @param internal The internal value
	 * @return Millisecond value
	 */
	public static long fromInternalToMillis(int internal)
	{
		if (internal >= 0)
			return internal;
		else if (internal == Integer.MIN_VALUE)
			return Long.MAX_VALUE;
		return ((long)-internal) * 1000L;
	}
}
