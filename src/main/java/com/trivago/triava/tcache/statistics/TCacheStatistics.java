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

package com.trivago.triava.tcache.statistics;

import java.io.Serializable;




/**
 * Hold statistics for tCache, for example number of elements and hit count.
 */
public class TCacheStatistics implements TCacheStatisticsInterface, Serializable
{
	/**
	 * Must be Serializable due to JSR107 requirements (MXBean)
	 */
	private static final long serialVersionUID = 4437080071261415107L;
	private String id;
	private float hitRatio; 
	/**
	 * @return the hitRatio
	 */
	public float getHitRatio()
	{
		return hitRatio;
	}

	private long hitCount;
	private long missCount;
	private long putCount;
	private long removeCount;
	private long dropCount;
	private long evictionCount;
	private long elementCount;
	private long evictionRounds;
	private long evictionHalts;
	private long evictionRate;


	/**
	 * Creates empty Statistics.
	 * @param id The ID of the cache instance 
	 */
	public TCacheStatistics(String id)
	{
		this.setId(id);
	}

	
	public long getHitCount()
	{
		return hitCount;
	}

	@Override
	public void setHitCount(long hitCount)
	{
		this.hitCount = hitCount;
	}

	public long getMissCount()
	{
		return missCount;
	}

	@Override
	public void setMissCount(long missCount)
	{
		this.missCount = missCount;
	}

	public long getPutCount()
	{
		return putCount;
	}

	@Override
	public void setPutCount(long putCount)
	{
		this.putCount = putCount;
	}

	public long getRemoveCount()
	{
		return removeCount;
	}
	
	@Override
	public void setRemoveCount(long removeCount)
	{
		this.removeCount = removeCount;
	}


	public long getEvictionCount()
	{
		return evictionCount;
	}

	@Override
	public void setEvictionCount(long evictionCount)
	{
		this.evictionCount = evictionCount;
	}
	
	public long getEvictionRate()
	{
		return evictionRate;
	}

	public void setEvictionRate(long evictionRate)
	{
		this.evictionRate = evictionRate;
	}

	@Override
	public void setHitRatio(float hitRatio)
	{
		this.hitRatio = hitRatio;
	}

	@Override
	public void setElementCount(long count)
	{
		elementCount = count;
	}

	public long getElementCount()
	{
		return elementCount;
	}

	public long getDropCount()
	{
		return dropCount;
	}

	public void setDropCount(long dropCount)
	{
		this.dropCount = dropCount;
	}


	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append("Statistics [id=");
		builder.append(id);
		builder.append(", hitRatio=");
		builder.append(hitRatio);
		builder.append(", hitCount=");
		builder.append(hitCount);
		builder.append(", missCount=");
		builder.append(missCount);
		builder.append(", putCount=");
		builder.append(putCount);
		builder.append(", removeCount=");
		builder.append(removeCount);
		builder.append(", dropCount=");
		builder.append(dropCount);
		builder.append(", evictionCount=");
		builder.append(evictionCount);
		builder.append(", evictionRounds=");
		builder.append(evictionRounds);
		builder.append(", evictionHalts=");
		builder.append(evictionHalts);
		builder.append(", elementCount=");
		builder.append(elementCount);
		builder.append("]");
		return builder.toString();
	}

	@Override
	public void setEvictionRounds(long count)
	{
		this.evictionRounds = count;
	}

	/**
	 * @return the evictionRounds
	 */
	public long getEvictionRounds()
	{
		return evictionRounds;
	}

	@Override
	public void setEvictionHalts(long count)
	{
		evictionHalts  = count;
	}

	/**
	 * @return the evictionHalts
	 */
	public long getEvictionHalts()
	{
		return evictionHalts;
	}

	public String getId()
	{
		return id;
	}

	public void setId(String id)
	{
		this.id = id;
	}	
}
