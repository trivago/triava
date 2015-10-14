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

package com.trivago.triava.tcache;

public enum HashImplementation
{
	ConcurrentHashMap, // Default storage: java.util.concurrent.ConcurrentHashMap
	// Performance test storage: com.google.common.cache.LocalCache
	// Do NOT use this live. LocalCache is NOT part of official Guava API.
	// LocalCache always drops in case of a jam (like TCacheJamPolicy.DROP)
//	PerfTestGuavaLocalCache, // com.google.common.cache.LocalCache  
	HighscalelibNonBlockingHashMap, // org.cliffc.high_scale_lib.NonBlockingHashMap.java
}