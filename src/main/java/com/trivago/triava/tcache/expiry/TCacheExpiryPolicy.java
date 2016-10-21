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

/**
 * A lighter version of the JSR107 ExpiryPolicy class. It returns native long values in milliseconds instead of a Duration.
 * A value of {@link Constants#EXPIRY_NOCHANGE} represents the return value null from any of the ExpiryPolicy methods.
 */
public interface TCacheExpiryPolicy
{
	public long getExpiryForCreation();
	public long getExpiryForAccess();
	public long getExpiryForUpdate();
}
