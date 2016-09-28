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

public class Constants
{
	public final static int EXPIRY_ZERO = 0;
	public final static int EXPIRY_NOCHANGE = -1; // TODO make these constants only available to tcache (package private) 
//	public final static int EXPIRY_CREATE_OR_UPDATE = -2;
	public final static int EXPIRY_MAX = Integer.MAX_VALUE;
}
