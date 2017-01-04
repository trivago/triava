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

package com.trivago.triava.tcache.util;

import java.lang.management.ManagementFactory;
import java.util.Set;

import javax.cache.CacheException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import com.trivago.triava.tcache.Cache;
import com.trivago.triava.tcache.TCacheJSR107;

/**
 * Utility functions to register MBean objects. Concrete implementations for Configuration and
 * Statistics are excluded and are implemented in subclasses.
 * <p>
 * Implementation note: Modeled after the JSR107 RI
 * 
 * @author cesken
 *
 */
public abstract class TCacheMBean
{
	private final static MBeanServer mBeanServer;
	
	abstract public String objectNameType();
	abstract public Object getMBean(TCacheJSR107<?, ?> jsr107cache);

	static
	{
		/**
		 *  JSR107 RI creates its own MBeanServer. There was a discussion on the JSR107 ML about this.
		 *  There is also a ticket that indicates a JSR107 compliant implementation should
		 *  inspect the system property "org.jsr107.tck.management.agentId":
		 *      https://github.com/jsr107/jsr107tck/issues/83
		 *      
		 *  tCache follows the logic in the ticket in the implementation and either registers
		 *  on the PlatformMBeanServer or creates one with the domain from the system property.
		 */
		String agentId = System.getProperty("org.jsr107.tck.management.agentId");
		if (agentId == null)
		{
			mBeanServer = ManagementFactory.getPlatformMBeanServer();
		}
		else
		{
			mBeanServer = MBeanServerFactory.createMBeanServer(agentId);
		}
	}
	public void register(Cache<?,?> cache)
	{
		TCacheJSR107<?, ?> jsr107cache = cache.jsr107cache();
		// these can change during runtime, so always look it up
		ObjectName registeredObjectName = calculateObjectName(jsr107cache, objectNameType());
		if (isRegistered(registeredObjectName))
		{
			// Do not register twice. Actually this contains a race condition due to a
			// check-then-act "isRegistered() => registerMBean()" action, but it should not be
			// a problem for us, because there are never two caches with the same name alive at the
			// same time.
			return;
		}
		
		try
		{
			mBeanServer.registerMBean(getMBean(jsr107cache), registeredObjectName);
		}
		catch (Exception e)
		{
			throw new CacheException("Error registering cache MXBeans for CacheManager " + registeredObjectName
					+ " . Error was " + e.getMessage(), e);
		}
	}

	
	public void unregister(Cache<?, ?> cache)
	{
		TCacheJSR107<?, ?> jsr107cache = cache.jsr107cache();

		ObjectName objectName = calculateObjectName(jsr107cache, objectNameType());
		Set<ObjectName> registeredObjectNames = mBeanServer.queryNames(objectName, null);

		// should just be one
		for (ObjectName registeredObjectName : registeredObjectNames)
		{
			try
			{
				mBeanServer.unregisterMBean(registeredObjectName);
			}
			catch (Exception e)
			{
				throw new CacheException("Error unregistering object instance " + registeredObjectName
						+ " . Error was " + e.getMessage(), e);
			}
		}

	}

	/**
	 * Creates an object name using the scheme
	 * "javax.cache:type=Cache&lt;Statistics|Configuration&gt;,CacheManager=&lt;cacheManagerName&gt;,name=&lt;cacheName&gt;"
	 * <p>
	 * Implementation note: Method modeled after the JSR107 RI
	 */
	private static ObjectName calculateObjectName(javax.cache.Cache<?, ?> cache, String objectNameType)
	{
		String cacheManagerName = mbeanSafe(cache.getCacheManager().getURI().toString());
		String cacheName = mbeanSafe(cache.getName());

		try
		{
			/**
			 * JSR107 There seems to be a naming mismatch.
			 * - (correct) The RI uses "type=CacheConfiguration" and "type=CacheStatistics" 
			 * - (wrong) The API docs of CacheManager.enableManagement() specify "type=Cache" and "type=CacheStatistics" 
			 */
			return new ObjectName("javax.cache:type=Cache" + objectNameType + ",CacheManager=" + cacheManagerName
					+ ",Cache=" + cacheName);
		}
		catch (MalformedObjectNameException e)
		{
			throw new CacheException("Illegal ObjectName for Management Bean. " + "CacheManager=[" + cacheManagerName
					+ "], Cache=[" + cacheName + "] type=[" + objectNameType + "]", e);
		}
	}
	
	  /**
	   * Checks whether an ObjectName is already registered.
	   *
	   * @throws CacheException - all exceptions are wrapped in CacheException
	   */
	  static boolean isRegistered(ObjectName objectName)
	  {
	    Set<ObjectName> registeredObjectNames = mBeanServer.queryNames(objectName, null);
	    return !registeredObjectNames.isEmpty();
	  }
	  
	  /**
	   * Filter out invalid ObjectName characters from string.
	   *
	   * @param string input string
	   * @return A valid JMX ObjectName attribute value.
	   */
	  private static String mbeanSafe(String string)
	  {
	    return string == null ? "" : string.replaceAll(",|:|=|\n", ".");
	  }
}
