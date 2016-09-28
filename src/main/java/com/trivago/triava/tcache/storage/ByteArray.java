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

package com.trivago.triava.tcache.storage;

import java.util.Arrays;

/**
 * A data structure holding a byte array, and provides  a {@link #hashCode} based on the byte array content.
 * Important note: The arrays used for creating instances of this class must not be modified. See {@link ByteArray#ByteArray(byte[])} for details.
 */
public class ByteArray // TODO Should not be public, as it is not (yet) a method of general interest.
{
	final byte[] bytes;
	final int hashCode;

	/**
	 * Creates a new ByteArray from the given bytes. It is not allowed to modify the given byte array after
	 * running this constructor. The reason is that the given byte array is not copied and the hashCode is
	 * being cached. When using ByteArray instances in hash based data structures like HashMap, objects can get "lost".
	 * 
	 * @param bytes The byte array
	 */
	public ByteArray(byte[] bytes)
	{
		this.bytes = bytes;
		
		int step;
		int count;
		if (bytes.length < 32)
		{
			// Not much data. Read all bytes
			count = bytes.length;
			step = 1;
		}
		else
		{
			/**
			 * Much data. Only sample the bytes, to match the following goals:
			 * 1) Do not read all bytes as the byte array could be huge (MB, GB)
			 * 2) We do not simply take the first or last bytes, as they could be a header trailer that is always identical (e.g. with gzip one often sees the same 10 identical header bytes). => use "step"
			 * 3) We want to read as "local" as possible, e.g. read everything from one memory page (typically 4 or 8KB)  => limit using a "count"
			 * 4) Try to read also uneven positions (e.g. 0, 31, 62, 93), because it can easily happen to hit similar data (e.g. there is often a 0 in the "high byte/MSB's" of integers in an an int[] array).
			 */
			count = 32; // 1), 3)
			step = Math.min(31, bytes.length / count); // 2), 4) 
			// 31*32 = 992 => we stay within 1KB
		}
		
		final int prime = 31;
		int result = 1;
		for (int pos=0, i=0; i<count; i++, pos+=step)
		{
			result = prime * result + bytes[pos];
		}
		this.hashCode = result;
	}
	

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (!(obj instanceof ByteArray))
			return false;
		

		ByteArray other = (ByteArray) obj;
		if (hashCode != other.hashCode)
			return false;

		if (!Arrays.equals(bytes, other.bytes))
			return false;
		
		return true;
	}

	@Override
	public int hashCode()
	{
		return hashCode;
	}
}
