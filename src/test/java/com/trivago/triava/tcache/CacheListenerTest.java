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

package com.trivago.triava.tcache;

import static org.junit.Assert.*;

import java.util.concurrent.TimeUnit;

import javax.cache.Cache;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.MutableCacheEntryListenerConfiguration;

import org.junit.Test;

public class CacheListenerTest extends CacheListenerTestBase
{
	public void testListenerSync()
	{
		testListener(true, 0, TimeUnit.MILLISECONDS);
	}
	
	public void testListener(boolean isSynchronous, int maxWait, TimeUnit unit)
	{
		this.maxWait = maxWait;
		this.unit = unit;
		
		try
		{	
			javax.cache.Cache<Integer, String> cache = createJsr107Cache("CacheListenerTest-1");
			
			Factory<UpdateListener> ulFactory = FactoryBuilder.factoryOf(new UpdateListener());
			CacheEntryListenerConfiguration<Integer, String> listenerConf = new MutableCacheEntryListenerConfiguration<>(ulFactory, null, false, isSynchronous);
			cache.registerCacheEntryListener(listenerConf);
			
			resetListenerCounts();
			cache.put(1, "One");
			// Synchronous => Listener must have been executed before leaving put()
			checkCreated(1);

			resetListenerCounts();
			cache.put(2, "Two");
			cache.put(3, "Three");
			checkCreated(2);


			resetListenerCounts();
			cache.put(2, "Two updated");
//			checkUpdated(1); // TODO implement

			resetListenerCounts();
			// TODO Need to check the Spec, whether we need to fire any event for an "unchanged" entry
			cache.put(1, "One");
//			checkCreated(0);
//			checkUpdated(0);

			
		}
		catch (Exception e)
		{
			fail(e.getMessage() + ": " + e.getCause());
		}
	}
	
	
	
	  @Test
	  public void  testDeregistration() {

			javax.cache.Cache<Integer, String> cache = createJsr107Cache("CacheListenerTest-2");



	    UpdateListener firstListener = new UpdateListener();
	    MutableCacheEntryListenerConfiguration<Integer, String> firstListenerConfiguration = new
	        MutableCacheEntryListenerConfiguration(FactoryBuilder.factoryOf(firstListener), null, false, true);
	    cache.registerCacheEntryListener(firstListenerConfiguration);

	    assertEquals(1, getConfigurationCacheEntryListenerConfigurationSize(cache));

	    UpdateListener secondListener = new UpdateListener();
	    MutableCacheEntryListenerConfiguration<Integer, String> secondListenerConfiguration = new
	        MutableCacheEntryListenerConfiguration(FactoryBuilder.factoryOf(secondListener), null, false, true);
	    cache.registerCacheEntryListener(secondListenerConfiguration);

	    assertEquals(2, getConfigurationCacheEntryListenerConfigurationSize(cache));
	    cache.deregisterCacheEntryListener(secondListenerConfiguration);

	    assertEquals(1, getConfigurationCacheEntryListenerConfigurationSize(cache));

	    //no effect if called after it has been removed
	    cache.deregisterCacheEntryListener(secondListenerConfiguration);
	    assertEquals(1, getConfigurationCacheEntryListenerConfigurationSize(cache));

	    //Deregister the listener registered at configuration time
	    cache.deregisterCacheEntryListener(firstListenerConfiguration);
	    assertEquals(0, getConfigurationCacheEntryListenerConfigurationSize(cache));
	  }
	  
	  private int getConfigurationCacheEntryListenerConfigurationSize(Cache cache) {
		    int i = 0;
		    CompleteConfiguration<Long, String> cacheConfig = (CompleteConfiguration)cache.getConfiguration(CompleteConfiguration.class);
		    for (CacheEntryListenerConfiguration<Long, String> listenerConfig : cacheConfig.getCacheEntryListenerConfigurations()) {
		      i++;
		    }
		    return i;
		  }
}
