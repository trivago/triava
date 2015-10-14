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

/**
 * The TimeSource interface allows the integration of arbitrary time sources. Simple implementations can deliver
 * the system time from System.nanoTime(), more complex implementations can do for example the following:
 * <ul>
 * <li>Cache the time</li>
 * <li>Use a specific time when replaying error situations or to check behavior on DST time borders.</li>
 * <li>Read it from custom hardware devices</li>
 * <li>Skew the time to check the effects when ntp adjusts the time to go faster, slower or backwards.</li>
 * </ul>
 * <p>
 * Any implementation must be able to deliver the time in seconds and milliseconds since epoch, and may
 * support more TimeUnit's.
 *  
 * @author cesken
 *
 */
public interface TimeSource
{
	/**
	 * Returns the time in the given time unit. It is recommended for unsupported TimeUnit values to throw
	 * an UnsupportedOperation exception.
	 * 
	 * @return time in milliseconds since epoch
	 */
	long time(TimeUnit tu);
	
	/**
	 * Returns the time in seconds. Convenience method, with the same effect as {@link #time(TimeUnit.SECONDS)}
	 * @return time in seconds since epoch
	 */
	long seconds();
	
	/**
	 * Returns the time in milliseconds. Convenience method, with the same effect as {@link #time(TimeUnit.MILLISECONDS)}
	 * @return time in milliseconds since epoch
	 */
	long millis();
	
	/**
	 * Shutdown the time source. Implementations should release all resources they use,
	 * like background threads, memory and connections to hardware clocks.
	 */
	void shutdown();
}
