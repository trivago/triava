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

package com.trivago.triava.time;

import java.util.concurrent.TimeUnit;

import com.trivago.triava.annotations.Alpha;

/**
 * A TimeSource that produces times with an offset to the actual time.
 * Target use case for this class is to simulate running at a different time, e.g. when replaying
 * an error scenario.
 * 
 * @author cesken
 *
 */
@Alpha(comment="Class naming and implementation check pending")
public class OffsetTimeSource implements TimeSource
{
	final TimeSource parentTimeSource;
	final long offsetMillis;
	
	/**
	 * Creates a TimeSource that produces times starting with the given startTimeMillis.
	 * All times returned are using an offset of "startTimeMillis - now", with now being
	 * the time of construction.
	 * 
	 * @param startTimeMillis The start timestamp (artificial "now time")
	 * @param parentTimeSource The TimeSource on which the offset will be applied
	 */
	public OffsetTimeSource(long startTimeMillis, TimeSource parentTimeSource)
	{
		if (parentTimeSource == null)
			throw new NullPointerException("parentTimeSource must not be null");
		this.parentTimeSource = parentTimeSource;
		this.offsetMillis = startTimeMillis - getMillisFromSource();
	}

	private long getMillisFromSource()
	{
		return parentTimeSource.millis() + offsetMillis;
	}

	@Override
	public long time(TimeUnit tu)
	{
		return tu.convert(getMillisFromSource(), TimeUnit.MILLISECONDS);
	}

	@Override
	public long seconds()
	{
		return time(TimeUnit.SECONDS);
	}

	@Override
	public long millis()
	{
		return getMillisFromSource();
	}

	@Override
	public void shutdown()
	{
	}

}
