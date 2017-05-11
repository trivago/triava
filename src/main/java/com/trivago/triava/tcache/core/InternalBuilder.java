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

package com.trivago.triava.tcache.core;

import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;

import com.trivago.triava.tcache.Cache;
import com.trivago.triava.tcache.CacheLimit;
import com.trivago.triava.tcache.TCacheFactory;
import com.trivago.triava.tcache.eviction.LFUEviction;
import com.trivago.triava.tcache.eviction.LRUEviction;

/**
 * A Builder that additionally stores the TCacheFactory. The TCacheFactory is only used for internal purposes in the {@link #build()} call. It cannot
 * be transmitted over the network, as it contains a Classloader and other non-serializable fields. If you want to transmit a {@link CompleteConfiguration}
 * you should instantiate a {@link Builder} instead, which contains a {@link CompleteConfiguration}.
 * <p>
 * Implementation note: The factory has been moved from Builder to this class, to keep Builder a pure configuration class. The factory was in fact never copied in
 * Builder.copyBuilder(), and thus it was removed  from Serialization in the course of finalizing the v1.0 API.
 * 
 * 
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> Thed value class
 */
public class InternalBuilder<K, V> extends Builder<K, V>
{
	private static final long serialVersionUID = 242912192026515310L;
	transient private TCacheFactory factory = null;

	public InternalBuilder(TCacheFactory factory)
	{
		super();
		this.factory = Builder.verifyNotNull("factory", factory);
	}

	public InternalBuilder(TCacheFactory factory, Configuration<K, V> configuration)
	{
		super(configuration);
		this.factory = verifyNotNull("factory", factory);
	}

	public Cache<K, V> build()
	{
		if (getId() == null)
		{
			setId("tcache-" + anonymousCacheId.incrementAndGet());
		}

		final Cache<K, V> cache;
		if (getEvictionClass() != null)
		{
			cache = new CacheLimit<>(factory, this);
		}
		else
		{
			switch (getEvictionPolicy())
			{
				case LFU:
					cache = new CacheLimit<>(factory, this.setEvictionClass(new LFUEviction<K,V>()));
					break;
				case LRU:
					cache = new CacheLimit<>(factory, this.setEvictionClass(new LRUEviction<K,V>()));
					break;
//				case CLOCK:
//					throw new UnsupportedOperationException("Experimental option is not activated: eviciton.CLOCK");
//					break;
//					// ClockEviction requires a TimeSource, but it may not be active yet (or even worse will change)
//					// => either we need to activate the TimeSource here, or introduce an "Expiration Context" that provides the TimeSource
//					cache = new CacheLimit<>(this.setEvictionClass(new ClockEviction<K,V>()));
				case CUSTOM:
					cache = new CacheLimit<>(factory, this);
					break;
				case NONE:
					cache = new Cache<>(factory, this);
					break;
				default:
					throw new IllegalArgumentException("Invalid evictionPolicy=" + getEvictionPolicy());
			}
		}
		
		return cache;
	}


}
