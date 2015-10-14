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

import java.util.Arrays;

/**
 * Created by astamenov on 29.05.2015.
 */
public class SlidingWindowCounter
{
    // A single slot would collect data for a time span in seconds defined by this field
    private int slotWidth;

    // The time span covered by all slots of this counter in seconds
    private int counterWidth;

    // The time difference in seconds that the timePointer is allowed to remain behind the 'clock'.
    private int driftLimitHead;

    // Pointer to the slot holding data for the current time border
    private int head = 0;

    // Each slot contains the number of events that happened in a slotWidth seconds
    private int[] slots;

    /**
     *  Marks the time border between slot X and slot X+1.
     *  Slot X is the one referred to by head.
     *  
     *  Seconds from epoch.
     */
    private long timePointer = 0;


    /**
     *
     * @param slotCount the number of slots.
     * @param slotWidth the time span covered by a single slot in seconds.
     */
    public SlidingWindowCounter(int slotCount, int slotWidth)
    {
        this.slotWidth = slotWidth;
        this.slots = new int[slotCount];

        this.counterWidth = this.slotWidth * slotCount;
        this.driftLimitHead = -1 * this.counterWidth / 2;
    }

    public void registerEvents(long timestampInSeconds, int countOfEvents)
    {
        if (this.timePointer == 0)
        {
            // First event to register.
            this.timePointer = timestampInSeconds + this.slotWidth;
            this.head = 0;
        }

        int diff = secondsBetween(timestampInSeconds, this.timePointer);

        if (diff < 0)
        {
            diff = -1 * diff;

            if (diff > this.counterWidth)
            {
                // An event outside of the aggregate time span of this Counter.
                // Such events could be registered as errors or otherwise reported.                
                return;
            }

            registerEventBeforeTimeBorder(diff, countOfEvents);
        }
        else
        {
            registerEventAfterTimeBorder(diff, countOfEvents);
        }
    }

    private int secondsBetween(long timePointA, long timePointB)
	{		
		return (int) (timePointA - timePointB);
	}

	/**
     *
     * @param diffInSeconds the difference in seconds between the current time border and event to register
     */
    private void registerEventBeforeTimeBorder(int diffInSeconds, int countOfEvents)
    {
        // How many slots "back in time" this event should be registered
        int deltaSlots = diffInSeconds / this.slotWidth;

        if (diffInSeconds % this.slotWidth == 0)
        {
            deltaSlots--;
        }

        int slotToRegister = this.head - deltaSlots;

        if (slotToRegister < 0)
        {
            slotToRegister = this.slots.length + slotToRegister;
        }

        this.slots[slotToRegister] = this.slots[slotToRegister] + countOfEvents;
    }

    private void registerEventAfterTimeBorder(int diffInSeconds, int countOfEvents)
    {
        moveHeadForward(diffInSeconds);

        // Register event
        this.slots[this.head] = countOfEvents;
    }

    private void moveHeadForward(int diffInSeconds)
    {
        // How many slots "ahead in time" this event should be registered
        int deltaSlots = 0;

        if (diffInSeconds == 0)
        {
            // The new event is exactly at the current time-pointer. We need to move the head forward only once.
            deltaSlots = 1;
        }
        else
        {
            deltaSlots = diffInSeconds / this.slotWidth;

            if (diffInSeconds % this.slotWidth != 0)
            {
                deltaSlots++;
            }
        }

        for (int i = 0; i < deltaSlots; i++)
        {
            // Move the current slot one position ahead
            this.head = (this.head + 1) % this.slots.length;

            // Clean head
            this.slots[this.head] = 0;

            // Move time border ahead
            this.timePointer += this.slotWidth;
        }
    }

    public int getRateTotal(long timeNowInSeconds)
    {
    	synchronizeTimePointer(timeNowInSeconds);
    	return getRateTotal();
    }
    
    public int getRateTotal()
    {
        int rate = 0;

        for (int i = 0; i < this.slots.length; i++)
        {
            int nextSlot = this.head - i;

            if (nextSlot < 0)
            {
                nextSlot = this.slots.length + nextSlot;
            }

            rate += this.slots[nextSlot];
        }

        return rate;
    }

    /**
     *  Synchronizes {@code counter.timePointer} with {@code now}.
     *
     *  There are three scenarios possible:
     *  1. abs(now - couner.timePointer) is within predefined limits
     *  2. counter.timePointer is too far back in the past
     *  3. counter.timePointer is too far ahead in the future
     *
     *  Scenario 1: No action. Should be the most common scenario
     *
     *  Scenario 2: Move counter.timePointer to now. Will happen for less active IPs.
     *
     *  Scenario 3: No action. Could happen when server clocks drift too much from each other.
     *
     * @param timeNowInSeconds
     */
    public void synchronizeTimePointer(long timeNowInSeconds)
    {
    	// Perform synchronization only if timePointer was initialized.
    	if (this.timePointer > 0)
    	{
	        int diffInSeconds = secondsBetween(this.timePointer, timeNowInSeconds);
	
	        // time now has passed to the next-next slot
	        if (diffInSeconds < this.driftLimitHead)
	        {
	            diffInSeconds = -1 * diffInSeconds;
	
	            moveHeadForward(diffInSeconds);
	        }
    	}
    }

    public String toString()
    {
        StringBuilder builder = new StringBuilder();

        builder.append('(');

        for (int i = 0; i < this.slots.length; i++)
        {
            int slotValue = this.slots[i];

            if (i == this.head)
            {
                builder.append('[');
            }

            builder.append(slotValue);

            if (i == this.head)
            {
                builder.append(']');
            }

            builder.append(',');
        }

        builder.replace(builder.length() - 1, builder.length(), ") / ");

        builder.append(getRateTotal());
        builder.append(" / ");
        builder.append(this.timePointer);

        return builder.toString();
    }

    public int[] getCopyOfSlots()
    {
        return Arrays.copyOf(this.slots, this.slots.length);
    }
}