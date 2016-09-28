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

package com.trivago.triava.tcache.expiry;

import javax.cache.expiry.Duration;
import javax.cache.expiry.ExpiryPolicy;

/**
 * Am {@link ExpiryPolicy} with fixed expiry times. This TCacheExpiryPolicy is NOT JSR107 compatible,
 * as it uses internal fields instead of calling the getters of the ExpiryPolicy each time.
 * 
 * @author cesken
 *
 */
public class UntouchedExpiryPolicy implements TCacheExpiryPolicy
{
	final int expiryForCreation;
	final int expiryForAccess;
	final int expiryForUpdate;

	public UntouchedExpiryPolicy(ExpiryPolicy expiryPolicy)
	{
		expiryForCreation = convertToSecs(expiryPolicy.getExpiryForCreation(), 0);
		expiryForAccess = convertToSecs(expiryPolicy.getExpiryForAccess(), -1);
		expiryForUpdate = convertToSecs(expiryPolicy.getExpiryForUpdate(), -1);
	}
	
	int convertToSecs(Duration expiryDuration, int defaultValue)
	{
		final long expiryForCreation;
		if (expiryDuration != null)
		{
			expiryForCreation = expiryDuration.getAdjustedTime(0) / 1000;
			return limitToPositiveInt(expiryForCreation);
		}
		else
		{
			return defaultValue;
		}
	}
	
	private static int limitToPositiveInt(long value)
	{
		if (value > (long)Integer.MAX_VALUE)
		{
			return Integer.MAX_VALUE;
		}
		else if  (value < 0)
		{
			return 0;
		}
		return (int)value;
	}

	public int getExpiryForCreation()
	{
		return expiryForCreation;
	}

	public int getExpiryForAccess()
	{
		return expiryForAccess;
	}

	public int getExpiryForUpdate()
	{
		return expiryForUpdate;
	}
}
