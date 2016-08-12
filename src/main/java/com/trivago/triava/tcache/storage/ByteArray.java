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
 * A data structure holding a byte array and caches its hashCode 
 */
public class ByteArray
{
	final byte[] bytes;
	final int hashCode;

	public ByteArray(byte[] bytes)
	{
		this.bytes = bytes;
		
		int step;
		int count;
		if (bytes.length < 32)
		{
			count = bytes.length;
			step = 1;
		}
		else
		{
			count = 32;
			step = bytes.length / count;
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
