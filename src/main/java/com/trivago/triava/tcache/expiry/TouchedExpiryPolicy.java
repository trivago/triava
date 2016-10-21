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
 * A JSR107 compatible {@link TCacheExpiryPolicy}, that reads calls the getters of the ExpiryPolicy each time. 
 * @author cesken
 *
 */
public class TouchedExpiryPolicy implements TCacheExpiryPolicy
{
	private final ExpiryPolicy expiryPolicy;

	public TouchedExpiryPolicy(ExpiryPolicy expiryPolicy)
	{
		this.expiryPolicy = expiryPolicy;
	}
	
	long convertToMillis(Duration expiryDuration, long defaultValue)
	{
		return expiryDuration != null ? expiryDuration.getAdjustedTime(0) : defaultValue;
	}


	public long getExpiryForCreation()
	{
		return convertToMillis(expiryPolicy.getExpiryForCreation(), Constants.EXPIRY_ZERO);
	}

	public long getExpiryForAccess()
	{
		return convertToMillis(expiryPolicy.getExpiryForAccess(), Constants.EXPIRY_NOCHANGE);
	}

	public long getExpiryForUpdate()
	{
		return convertToMillis(expiryPolicy.getExpiryForUpdate(), Constants.EXPIRY_NOCHANGE);
	}
}
