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

import java.io.PrintStream;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Arrays;

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
	private static final String SYSTEM_SOURCE_NAME = "System";
	static final long Y2000 = 946684800_000L; // Year 2000 new year midnight
	static final long DAY_LENGTH_MILLIS = 86400_000;
	
	static PrintStream out = System.out;

	public static void main(String[] args)
	{
		offsetTimeSource();
		offsetY2000TimeSource();
		estimatorTimeSource();
		chainedTimeSource();
	}

	/**
	 * Demonstrate to move the time back 86400 seconds, which is the typical length of a day.
	 */
	private static void offsetTimeSource()
	{
		TimeSource tsToday = new SystemTimeSource();
		TimeSource tsYesterday = new OffsetTimeSource(tsToday.millis() - DAY_LENGTH_MILLIS, tsToday);
		
		compareTimeSources(SYSTEM_SOURCE_NAME, "Offset", tsToday, tsYesterday);
	}

	/**
	 * Demonstrate to move the time back to a fixed date
	 */
	private static void offsetY2000TimeSource()
	{
		TimeSource tsToday = new SystemTimeSource();
		TimeSource tsYesterday = new OffsetTimeSource(Y2000, tsToday);
		
		compareTimeSources(SYSTEM_SOURCE_NAME, "Y2000", tsToday, tsYesterday);
	}

	/**
	 * Demonstrates the difference between estimated time and real time
	 */
	private static void estimatorTimeSource()
	{
		TimeSource tsNow = new SystemTimeSource();
		TimeSource tsEstimated = new EstimatorTimeSource(100);
		
		compareTimeSources(SYSTEM_SOURCE_NAME, "Estimator", tsNow, tsEstimated);
	}
	
	/**
	 * Demonstrates TimeSource chaining. Decorates one TimeSource with another.
	 */
	private static void chainedTimeSource()
	{
		TimeSource tsNow = new SystemTimeSource();
		TimeSource tsMinus1Day = new OffsetTimeSource(tsNow.millis() - DAY_LENGTH_MILLIS, tsNow); // yesterday = - 24h
		TimeSource tsMinus1DayEst = new EstimatorTimeSource(tsMinus1Day, 100, new TriavaNullLogger()); // -24h and estimating
		
		compareTimeSources(SYSTEM_SOURCE_NAME, "Chained", tsNow, tsMinus1DayEst);
	}

	/**
	 * Shows the time and time differences between two TimeSource implementations 
	 */
	private static void compareTimeSources(String ts1name, String ts2name, TimeSource ts1, TimeSource ts2)
	{
		out.println("--- " + ts1name + " vs " + ts2name + " START ---");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS");
		for (int i=0; i<30; i++)
		{
			long ts1millis = ts1.millis();
			long ts2millis = ts2.millis();
			String ts1date = sdf.format(new Date(ts1millis));
			String ts2date = sdf.format(new Date(ts2millis));
			long millisDiff = ts1millis - ts2millis;
			out.printf("diff=%5d %s %s %s %s%n",  millisDiff, ts1name, ts1date.toString(), ts2name, ts2date.toString());
			try
			{
				Thread.sleep(75);
			}
			catch (InterruptedException e)
			{
				// This should never happen, as we do not interrupt and there should not be any spurious interrupts/wakeups like in wait()
				out.println("Interrupted: " + Arrays.toString(e.getStackTrace()));
			}
		}
		out.println("--- " + ts1name + " vs " + ts2name + " END ---");
		out.println();
	}

	

}
