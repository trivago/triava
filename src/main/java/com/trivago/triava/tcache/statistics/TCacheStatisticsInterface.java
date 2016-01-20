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

/**
 * The statistics interface contains setters for Cache statistics.
 * Standard application code will not need to implement this interface, as
 * they can call {@link com.trivago.triava.tcache.eviction.Cache#statistics()}
 * to retrieve the current Cache Statistics.
 * <p>  
 * A reason to implement this instead of using TCacheStatistics is to
 * allow to write adapters to existing statistics systems. 
 * 
 * @author cesken
 *
 */
public interface TCacheStatisticsInterface
{
	void setHitCount(long count);
	void setMissCount(long count);
	void setPutCount(long count);
	void setRemoveCount(long removeCount);	
	void setEvictionCount(long count);
	void setEvictionRounds(long count);
	void setEvictionHalts(long count);
	void setEvictionRate(long rate);
	void setHitRatio(float count);
	void setElementCount(long count);
	void setDropCount(long dropCount);
}
