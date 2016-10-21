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
	final long expiryForCreation;
	final long expiryForAccess;
	final long expiryForUpdate;

	public UntouchedExpiryPolicy(ExpiryPolicy expiryPolicy)
	{
		expiryForCreation = convertToMillis(expiryPolicy.getExpiryForCreation(), Constants.EXPIRY_ZERO);
		expiryForAccess = convertToMillis(expiryPolicy.getExpiryForAccess(), Constants.EXPIRY_NOCHANGE);
		expiryForUpdate = convertToMillis(expiryPolicy.getExpiryForUpdate(), Constants.EXPIRY_NOCHANGE);
	}
	
	long convertToMillis(Duration expiryDuration, long defaultValue)
	{
		return expiryDuration != null ? expiryDuration.getAdjustedTime(0) : defaultValue;
	}

	public long getExpiryForCreation()
	{
		return expiryForCreation;
	}

	public long getExpiryForAccess()
	{
		return expiryForAccess;
	}

	public long getExpiryForUpdate()
	{
		return expiryForUpdate;
	}
}
