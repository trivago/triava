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

package com.trivago.triava.tcache.action;

import javax.cache.integration.CacheWriter;

import com.trivago.triava.tcache.event.ListenerCollection;
import com.trivago.triava.tcache.statistics.StatisticsCalculator;

public interface ActionContext<K,V>
{
	CacheWriter<K, V> cacheWriter();
	ListenerCollection<K, V> listeners();
	StatisticsCalculator statisticsCalculator();
}
