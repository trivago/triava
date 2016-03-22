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

package com.trivago.triava.tcache.util;

import com.trivago.triava.util.UnitSystem;
import com.trivago.triava.util.UnitTools;

/**
 * Data class which stores  information of the cache size, namely number of elements and
 * size in bytes. It is used in {@link com.trivago.triava.tcache.eviction.Cache#reportSize(ObjectSizeCalculatorInterface)}.
 * As reportSize() can take a serious amount of time, the number of elements is stored twice: Once before
 * the actual byte count is calculated, and one after. This makes it easier to see whether important changes
 * took place during calculation, like expiration, eviction or a mass-insert of elements.
 *  
 * @author cesken
 *
 */
public class CacheSizeInfo
{
	private final String id;
	private final int elemsBefore;

	private final long sizeInByte;
	private final int elemsAfter;

	public CacheSizeInfo(String id, int elemsBefore, long sizeInByte, int elemsAfter)
	{
		this.id = id;
		this.elemsBefore = elemsBefore;
		this.sizeInByte = sizeInByte;
		this.elemsAfter = elemsAfter;
	}

	public String getId()
	{
		return id;
	}

	public int getElemsBefore()
	{
		return elemsBefore;
	}

	public long getSizeInByte()
	{
		return sizeInByte;
	}

	public int getElemsAfter()
	{
		return elemsAfter;
	}

	@Override
	public String toString()
	{
		String humanReadableSize = UnitTools.formatAsUnit(sizeInByte, UnitSystem.SI, "B");
		return "CacheSizeInfo [id=" + id + ", elemsBefore=" + elemsBefore + ", elemsAfter=" + elemsAfter
				+ ", elemsDiff=" + (elemsAfter - elemsBefore) + ", sizeInByte=" + sizeInByte + ", sizeReadable="
				+ humanReadableSize + "]";
	}

}