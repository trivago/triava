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

package com.trivago.examples;

import com.trivago.triava.logging.TriavaNullLogger;
import com.trivago.triava.time.EstimatorTimeSource;
import com.trivago.triava.time.OffsetTimeSource;
import com.trivago.triava.time.SystemTimeSource;
import com.trivago.triava.time.TimeSource;


/**
 * Examples for creating and using time sources.
 * 
 * @author cesken
 *
 */
public class TimeSourceExample
{
	static final long Y2000 = 946684800;
	public static void main(String[] args)
	{
		offsetTimeSource();
		estimatorTimeSource();
		chainedTimeSource();
	}

	/**
	 * Demonstrate to move the time back 86400 seconds, which is the typical length of a day.
	 */
	private static void offsetTimeSource()
	{
		TimeSource tsToday = new SystemTimeSource();
		TimeSource tsYesterday = new OffsetTimeSource(tsToday.millis() - 86400, tsToday);
		
		compareTimeSources("System", "Offset", tsToday, tsYesterday);
	}

	private static void compareTimeSources(String ts1name, String ts2name, TimeSource ts1, TimeSource ts2)
	{
		System.out.println("--- " + ts1name + " vs " + ts2name + " START ---");
		for (int i=0; i<30; i++)
		{
			long ts1millis = ts1.millis();
			long ts2millis = ts2.millis();
			long millisDiff = ts1millis - ts2millis;
			System.out.printf("diff=%5d %s %d %s %d%n",  millisDiff, ts1name, ts1millis, ts2name, ts2millis);
			//+  ", " + ts1name + "=" + today + ", " + ts2name + "=" + yesterday
			try
			{
				Thread.sleep(25);
			}
			catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}
		System.out.println("--- " + ts1name + " vs " + ts2name + " END ---");
		System.out.println();
	}

	
	/**
	 * Demonstrates the difference between estimated time and real time
	 */
	private static void estimatorTimeSource()
	{
		TimeSource tsNow = new SystemTimeSource();
		TimeSource tsEstimated = new EstimatorTimeSource(100);
		
		compareTimeSources("System", "Estimator", tsNow, tsEstimated);
	}
	
	/**
	 * Demonstrates TimeSource chaining. Decorates one TimeSource with another.
	 */
	private static void chainedTimeSource()
	{
		TimeSource tsNow = new SystemTimeSource();
		TimeSource tsMinus1Day = new OffsetTimeSource(tsNow.millis() - 86400, tsNow); // yesterday = - 24h
		TimeSource tsMinus1DayEst = new EstimatorTimeSource(tsMinus1Day, 100, new TriavaNullLogger()); // -24h and estimating
		
		compareTimeSources("System", "Chained", tsNow, tsMinus1DayEst);
		
	}


}
