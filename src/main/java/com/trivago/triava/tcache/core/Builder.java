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

package com.trivago.triava.tcache.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.Factory;
import javax.cache.configuration.FactoryBuilder;
import javax.cache.configuration.FactoryBuilder.SingletonFactory;
import javax.cache.expiry.Duration;
import javax.cache.expiry.EternalExpiryPolicy;
import javax.cache.expiry.ExpiryPolicy;
import javax.cache.expiry.TouchedExpiryPolicy;
import javax.cache.integration.CacheWriter;

import com.trivago.triava.annotations.Beta;
import com.trivago.triava.tcache.Cache;
import com.trivago.triava.tcache.CacheWriteMode;
import com.trivago.triava.tcache.EvictionPolicy;
import com.trivago.triava.tcache.HashImplementation;
import com.trivago.triava.tcache.JamPolicy;
import com.trivago.triava.tcache.eviction.EvictionInterface;
import com.trivago.triava.tcache.storage.HighscalelibNonBlockingHashMap;
import com.trivago.triava.tcache.storage.JavaConcurrentHashMap;

/**
 * A Builder to create Cache instances. A Builder instance must be retrieved via a TCacheFactory,
 * to guarantee each created Cache will be registered in a CacheManager (Hint: The TCacheFactory
 * implements CacheManager). 
 * 
 * @author cesken
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class Builder<K,V>  implements TriavaCacheConfiguration<K, V, Builder<K,V>>
{
	private static final long serialVersionUID = -4430382287782891844L;

	static final AtomicInteger anonymousCacheId = new AtomicInteger();

	private String id;
	private boolean strictJSR107 = true;
	private long maxCacheTime = 3600_000; // In MILLISECONDS. 60 minutes
	private long maxCacheTimeSpread = 0; // In MILLISECONDS. 0 seconds
	private int expectedMapSize = 10000;
	private int concurrencyLevel = 14;
	private int mapConcurrencyLevel = 16;
	private long cleanUpIntervalMillis = 0; // 0 = auto-tuning

	private EvictionPolicy evictionPolicy = EvictionPolicy.LFU;
	private EvictionInterface<K, V> evictionClass = null;
	private HashImplementation hashImplementation = HashImplementation.ConcurrentHashMap;
	private JamPolicy jamPolicy = JamPolicy.WAIT;
	private boolean statistics = false; // off by JSR107 default
	private boolean management = false; // off by JSR107 default
	private CacheWriteMode writeMode = CacheWriteMode.Identity;
	private Class<K> keyType = objectKeyType();
	private Class<V> valueType = objectValueType();
	
	private Collection<CacheEntryListenerConfiguration<K, V>> listenerConfigurations = new ArrayList<>(0);
	private Factory<CacheWriter<? super K, ? super V>> writerFactory = null;
	private Factory<ExpiryPolicy> expiryPolicyFactory = EternalExpiryPolicy.factoryOf();

	private CacheLoader<K, V> loader = null;
	private Factory<javax.cache.integration.CacheLoader<K, V>> loaderFactory = null;
	
	private boolean writeThrough = false;
	private boolean readThrough = false;

	/**
	 * Native Builder for creating Cache instances. The returned object is initialized with default values.
	 * The native Builder by default uses a STORE_BY_REFERENCE model instead of the JSR107 default of STORE_BY_VALUE. 
	 * <p>
	 * Any Cache created by {@link #build()} is registered in the given factory/CacheManager. 
	 */
	public Builder()
	{
		management = true;
		statistics = true;
		strictJSR107 = false;
	}

	/**
	 * A Builder that is target for usage in JSR107 scenarios.
	 * It takes a JSR107 Configuration object to define defaults, and its isStoreByValue() value
	 * is converted to a CacheWriteMode using {@link CacheWriteMode#fromStoreByValue(boolean)}.
	 * <p>
	 * Defaults for this Builder are taken from the given
	 * configuration, which can be a plain JSR107 Configuration or a Builder itself. The
	 * given configuration is copied in both cases, so subsequent changes to the original object will have
	 * no effect on this Builder or any Cache created from it.
	 * <p>
	 * Any Cache created by {@link #build()} is registered in the given factory/CacheManager. 
	 * 
	 * @param configuration Cache Configuration, which can also be a Builder
	 */
	public Builder(Configuration<K,V> configuration)
	{
		this.writeMode = null; // null means to derive it from configuration.isStoreByValue()
		this.maxCacheTime = Long.MAX_VALUE; // JSR107 operates on ExpiryPolicy only
		copyBuilder(configuration, this);
	}


	/**
	 * Builds a Cache from the parameters that were set. evictionType defines the eviction policy. Any not
	 * explicitly set parameters will get a default values, which are:
	 * 
	 * <pre>
	 * private long maxIdleTime = 1800;
	 * private long maxCacheTime = 3600;
	 * private int expectedMapSize = 10000;
	 * private int concurrencyLevel = 16;
	 * private TCacheEvictionType evictionType = TCacheEvictionType.LFU;
	 * 
	 * </pre>
	 * 
	 * Any Cache created by {@link #build()} is registered in the associated factory/CacheManager. 
	 * 
	 * @return The Cache
	 */
	public Cache<K, V> build()
	{
		throw new UnsupportedOperationException("build() is only supported by internal subclasses.");
	}
	
	static <Arg> Arg verifyNotNull(String name, Arg arg)
	{
		if (arg == null)
		{
			throw new IllegalArgumentException(name + " is null");
		}
		return arg;
	}


	@Override
	public Builder<K,V> setId(String id)
	{
		this.id = id;
		return this;
	}

	@Override
	public Builder<K,V> setMaxIdleTime(int maxIdleTime, TimeUnit timeUnit)
	{
		if (maxIdleTime <= 0)
			throw new IllegalArgumentException("Invalid maxIdleTime: " + maxIdleTime);
		
		TouchedExpiryPolicy ep = new TouchedExpiryPolicy(new Duration(timeUnit, maxIdleTime));
		SingletonFactory<ExpiryPolicy> singletonFactory = new FactoryBuilder.SingletonFactory<ExpiryPolicy>(ep);
		this.expiryPolicyFactory = (Factory<ExpiryPolicy>)singletonFactory;
		
		return this;
	}

	@Override
	public Builder<K,V> setMaxCacheTime(int maxCacheTime, int interval, TimeUnit timeUnit)
	{
		if (interval <= 0)
			throw new IllegalArgumentException("Invalid interval: " + interval);
		setMaxCacheTime(maxCacheTime, timeUnit);
		this.maxCacheTimeSpread = timeUnit.toMillis(interval);
		return this;
	}

	@Override
	public Builder<K, V> setMaxCacheTime(int maxCacheTime, TimeUnit timeUnit)
	{
		if (maxCacheTime <= 0)
			throw new IllegalArgumentException("Invalid maxCacheTime: " + maxCacheTime);
		this.maxCacheTime = timeUnit.toMillis(maxCacheTime);
		return this;
	}

	@Override
	public Builder<K, V> setCleanupInterval(int cleanupInterval, TimeUnit timeUnit)
	{
		this.cleanUpIntervalMillis = timeUnit.toMillis(cleanupInterval);
		return this;
	}

	@Override
	public Builder<K,V> setExpectedMapSize(int expectedMapSize)
	{
		if (expectedMapSize < 0)
			throw new IllegalArgumentException("Invalid expectedMapSize: " + expectedMapSize);
		this.expectedMapSize = expectedMapSize;
		return this;
	}

	@Override
	public Builder<K,V> setConcurrencyLevel(int concurrencyLevel)
	{
		this.concurrencyLevel = concurrencyLevel;
		this.mapConcurrencyLevel = Math.max(concurrencyLevel + 2, 8);
		return this;
	}

	@Override
	public Builder<K,V> setEvictionPolicy(EvictionPolicy evictionPolicy)
	{
		this.evictionPolicy = evictionPolicy;
		this.evictionClass = null;
		return this;
	}

	@Override
	public Builder<K,V> setEvictionClass(EvictionInterface<K, V> clazz)
	{
		this.evictionPolicy = EvictionPolicy.CUSTOM;
		this.evictionClass = clazz;
		return this;
	}

	/**
	 * @return the evictionClass. null if there is no custom class
	 */
	public EvictionInterface<K, V> getEvictionClass()
	{
		return evictionClass;
	}


	/**
	 * Set the StorageBackend for the underlying ConcurrentMap. If this method is not called,
	 * ConcurrentHashMap will be used.
	 *  
	 * @param hashImplementation The {@link HashImplementation}
	 * @return This Builder
	 * 
	 */
	@Beta(comment="To be replaced by a method to set a StorageBackend")
	public Builder<K,V> setHashImplementation(HashImplementation hashImplementation)
	{
		this.hashImplementation = hashImplementation;
		return this;
	}

	/**
	 * Sets the policy, how a Thread that calls put() will behave the cache is full.
	 * Either the Thread will WAIT or DROP the element and not put it in the cache.
	 * The default is WAIT. The {@link JamPolicy} has no effect on caches of unlimited size
	 * {@link EvictionPolicy}}.NONE.
	 * 
	 * @param jamPolicy The {@link JamPolicy}
	 * @return This Builder
	 */
	public Builder<K,V> setJamPolicy(JamPolicy jamPolicy)
	{
		this.jamPolicy = jamPolicy;
		return this;
	}

//	/**
//	 * @return the factory
//	 */
//	public TCacheFactory getFactory()
//	{
//		return factory;
//	}

	public boolean getStatistics()
	{
		return statistics;
	}

	/**
	 * Sets whether statistics should be gathered. The performance impact on creating statistics is very low,
	 * so it is safe to activate statistics. If this method is not called, the default is to collect statistics.
	 * 
	 * @param statistics true, if you want to switch on statistics 
	 * @return This Builder
	 */
	public Builder<K,V> setStatistics(boolean statistics)
	{
		this.statistics = statistics;
		return this;
	}

	/**
	 * Sets whether management should be enabled.
	 * 
	 * @param management true, if you want to switch on management 
	 * @return This Builder
	 */
	public Builder<K,V> setManagement(boolean management)
	{
		this.management = management;
		return this;
	}

	/**
	 * @return the id
	 */
	public String getId()
	{
		return id;
	}

	/**
	 * @return the maxIdleTime in milliseconds.
	 */
	public long getMaxIdleTime()
	{
		Duration accessExpiry = expiryPolicyFactory.create().getExpiryForAccess();
		return accessExpiry.getAdjustedTime(0);
	}

	/**
	 * @return The lower bound of the "maximum cache time interval" [maxCacheTime, maxCacheTime+maxCacheTimeSpread] in milliseconds.
	 */
	public long getMaxCacheTime()
	{
		return maxCacheTime;
	}

	/**
	 * 
	 * @return The interval size of the cache time interval in milliseconds.
	 */
	public long getMaxCacheTimeSpread()
	{
		return maxCacheTimeSpread;
	}

	/**
	 * Returns the proposed cleanup interval in ms. See {@link #setCleanupInterval(int, TimeUnit)} for details on how the Cache uses this value.   
	 * @return The proposed cleanup interval in ms. 0 means auto-tuning
	 */
	public long getCleanUpIntervalMillis()
	{
		return cleanUpIntervalMillis;
	}
	
	/**
	 * @return the expectedMapSize
	 */
	public int getExpectedMapSize()
	{
		return expectedMapSize;
	}

	/**
	 * @return the concurrencyLevel
	 */
	public int getConcurrencyLevel()
	{
		return concurrencyLevel;
	}

	public int getMapConcurrencyLevel()
	{
		return mapConcurrencyLevel;
	}

	/**
	 * @return the evictionPolicy
	 */
	public EvictionPolicy getEvictionPolicy()
	{
		return evictionPolicy;
	}

	/**
	 * @return the hashImplementation
	 */
	public HashImplementation getHashImplementation()
	{
		return hashImplementation;
	}

	public StorageBackend<K, V> storageFactory()
	{
		switch (hashImplementation)
		{
			case ConcurrentHashMap:
				return new JavaConcurrentHashMap<K, V>();
//			case PerfTestGuavaLocalCache:
//				return new GuavaLocalCache<K, V>();
			case HighscalelibNonBlockingHashMap:
				return new HighscalelibNonBlockingHashMap<K, V>();
			default:
				return null;
		}
	}

	public JamPolicy getJamPolicy()
	{
		return jamPolicy;
	}

	public CacheLoader<K, V> getLoader()
	{
		return (CacheLoader<K, V>) loader;
	}

	public Builder<K, V> setLoader(CacheLoader<K, V> loader)
	{
		this.loader = loader;
		return this;
	}

	@Override
	public Factory<javax.cache.integration.CacheLoader<K, V>> getCacheLoaderFactory()
	{
		return loaderFactory;
	}


	@SuppressWarnings("unchecked")
	@Override // JSR107
	public Class<K> getKeyType()
	{
		return keyType == null ? (Class<K>)Object.class : keyType;
	}

	@Override // JSR107
	public Class<V> getValueType()
	{
		return valueType;
	}

	public void setKeyType(Class<K> keyType)
	{
		this.keyType = keyType;
	}

	public void setValueType(Class<V> valueType)
	{
		this.valueType = valueType;
	}

	@Override // JSR107
	public boolean isStoreByValue()
	{
		
		return writeMode.isStoreByValue();
	}
	
	public CacheWriteMode getCacheWriteMode()
	{
		return writeMode;
	}

	public Builder<K, V> setCacheWriteMode(CacheWriteMode writeMode)
	{
		this.writeMode = writeMode;
		return this;
	}

	/**
	 * Returns a representation of the Configuration as Properties.
	 * The returned properties are a private copy for the caller and thus not shared amongst different callers.
	 * Changes to the returned Properties have no effect on the Cache.
	 * 
	 * @param propsType If PropsType.CacheManager, the Cache specific properties (cacheName, cacheLoaderClass) are excluded
	 * @return The current configuration
	 */
	@Override
	public Properties asProperties(PropsType propsType)
	{
		boolean propsForCache = propsType == PropsType.Cache;
		Properties props = new Properties();
		
		if (propsForCache)
			props.setProperty("cacheName", id);
		props.setProperty("maxIdleTime", Long.toString(getMaxIdleTime()));
		props.setProperty("maxCacheTime", Long.toString(maxCacheTime));
		props.setProperty("maxCacheTimeSpread", Long.toString(maxCacheTimeSpread));
		props.setProperty("expectedMapSize", Integer.toString(expectedMapSize));
		props.setProperty("concurrencyLevel", Integer.toString(concurrencyLevel));
		props.setProperty("evictionPolicy", evictionPolicy.toString());
		props.setProperty("hashMapClass", hashImplementation.toString());
		props.setProperty("jamPolicy", jamPolicy.toString());
		props.setProperty("statistics", Boolean.toString(statistics));
		if (propsForCache)
			props.setProperty("cacheLoaderClass", loader == null ? "null" : loader.getClass().getName());
		props.setProperty("writeMode", writeMode.toString());
		
		return props;
	}

	/**
	 * Copies the configuration to the target Builder. If the source (configuration)
	 * is also a Builder, its fields also get copied. Any null-value in the
	 * configuration is ignored in the copying process, leaving the corresponding
	 * target value unchanged. The CacheWriteMode can be
	 * defined in two ways: If configuration is a Builder it is copied plainly,
	 * otherwise it is derived from configuration.isStoreByValue().
	 * 
	 * @param configuration The source builder
	 * @param target The target builder
	 */
	private void copyBuilder(Configuration<K, V> configuration, Builder<K, V> target)
	{
		CacheWriteMode tcacheWriteMode = null;
		if (configuration instanceof Builder)
		{
			Builder<K, V> sourceB = (Builder<K, V>)configuration;
			// tCache native configuration
			if (sourceB.id != null)
				target.id = sourceB.id;
			target.strictJSR107 = sourceB.strictJSR107;
			target.maxCacheTime = sourceB.maxCacheTime;
			target.maxCacheTimeSpread = sourceB.maxCacheTimeSpread;
			this.cleanUpIntervalMillis = sourceB.cleanUpIntervalMillis;
			target.expectedMapSize = sourceB.expectedMapSize;
			target.concurrencyLevel = sourceB.concurrencyLevel;
			if (sourceB.evictionPolicy != null)
				target.evictionPolicy = sourceB.evictionPolicy;
			if (sourceB.evictionClass != null)
				target.evictionClass = sourceB.evictionClass;			
			if (sourceB.hashImplementation != null)
				target.hashImplementation = sourceB.hashImplementation;
			if (sourceB.jamPolicy != null)
				target.jamPolicy = sourceB.jamPolicy;
			if (sourceB.loader != null)
				target.loader = sourceB.loader; // loader vs loaderFactory

			tcacheWriteMode = sourceB.writeMode;
		}
		
		if (configuration instanceof CompleteConfiguration)
		{
			CompleteConfiguration<K,V> cc = (CompleteConfiguration<K,V>)configuration;
			target.statistics = cc.isStatisticsEnabled();
			target.management = cc.isManagementEnabled();
			
			target.expiryPolicyFactory = cc.getExpiryPolicyFactory();
			target.writerFactory = cc.getCacheWriterFactory();
			Factory<javax.cache.integration.CacheLoader<K, V>> lf = cc.getCacheLoaderFactory();
			if (lf != null)
			{
				target.loader = null; // loader vs loaderFactory
				target.loaderFactory = lf;
			}
			
			Collection<CacheEntryListenerConfiguration<K, V>> listenerConfsCopy = new ArrayList<>(0);
			for (CacheEntryListenerConfiguration<K, V> entry : cc.getCacheEntryListenerConfigurations())
			{
				listenerConfsCopy.add(entry);
			}
			target.listenerConfigurations = listenerConfsCopy;
			
			target.writeThrough = cc.isWriteThrough();
			target.readThrough =  cc.isReadThrough();
		}
		
		// JSR107 configuration follows
		if (tcacheWriteMode != null)
			target.writeMode = tcacheWriteMode;
		else
			target.writeMode = CacheWriteMode.fromStoreByValue(configuration.isStoreByValue());
		
		target.keyType = configuration.getKeyType();
		target.valueType = configuration.getValueType();

	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + concurrencyLevel;
		result = prime * result + ((evictionClass == null) ? 0 : evictionClass.hashCode());
		result = prime * result + ((evictionPolicy == null) ? 0 : evictionPolicy.hashCode());
		result = prime * result + expectedMapSize;
		result = prime * result + ((hashImplementation == null) ? 0 : hashImplementation.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((jamPolicy == null) ? 0 : jamPolicy.hashCode());
		result = prime * result + ((keyType == null) ? 0 : keyType.hashCode());
		result = prime * result + ((loader == null) ? 0 : loader.hashCode());
		result = prime * result + mapConcurrencyLevel;
		result = prime * result + (int) (maxCacheTime ^ (maxCacheTime >>> 32));
		result = prime * result + (int) (maxCacheTimeSpread ^ (maxCacheTimeSpread >>> 32));
		result = prime * result + (int) (cleanUpIntervalMillis ^ (cleanUpIntervalMillis >>> 32));
		result = prime * result + expiryPolicyFactory.hashCode();
		result = prime * result + (statistics ? 1231 : 1237);
		result = prime * result + ((valueType == null) ? 0 : valueType.hashCode());
		result = prime * result + ((writeMode == null) ? 0 : writeMode.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		if (! (obj instanceof Builder))
			return false;

		Builder<?,?> other = (Builder<?,?>) obj;
		if (concurrencyLevel != other.concurrencyLevel)
			return false;
		if (evictionClass == null)
		{
			if (other.evictionClass != null)
				return false;
		}
		else if (!evictionClass.equals(other.evictionClass))
			return false;
		if (evictionPolicy != other.evictionPolicy)
			return false;
		if (expectedMapSize != other.expectedMapSize)
			return false;
		if (hashImplementation != other.hashImplementation)
			return false;
		if (id == null)
		{
			if (other.id != null)
				return false;
		}
		else if (!id.equals(other.id))
			return false;
		if (jamPolicy != other.jamPolicy)
			return false;
		if (keyType == null)
		{
			if (other.keyType != null)
				return false;
		}
		else if (!keyType.equals(other.keyType))
			return false;
		if (loader == null)
		{
			if (other.loader != null)
				return false;
		}
		else if (!loader.equals(other.loader))
			return false;
		if (mapConcurrencyLevel != other.mapConcurrencyLevel)
			return false;
		if (maxCacheTime != other.maxCacheTime)
			return false;
		if (maxCacheTimeSpread != other.maxCacheTimeSpread)
			return false;
		if (cleanUpIntervalMillis != other.cleanUpIntervalMillis)
			return false;
		if (! expiryPolicyFactory.equals(other.expiryPolicyFactory))
			return false;
		if (statistics != other.statistics)
			return false;
		if (valueType == null)
		{
			if (other.valueType != null)
				return false;
		}
		else if (!valueType.equals(other.valueType))
			return false;
		if (writeMode != other.writeMode)
			return false;
		return true;
	}

	@Override
	public boolean isReadThrough()
	{
		return readThrough;
	}

	
	public Builder<K, V> setCacheLoaderFactory(Factory<javax.cache.integration.CacheLoader<K, V>> loaderFactory)
	{
		this.loaderFactory = loaderFactory;
		return this;
	}

	@SuppressWarnings("unchecked")
	public Builder<K, V> setCacheWriterFactory(Factory<? extends CacheWriter<? super K, ? super V>> factory)
	{
		this.writerFactory = (Factory<CacheWriter<? super K, ? super V>>) factory;
		return this;
	}
	
	public Builder<K, V> setReadThrough(boolean isReadThrough)
	{
		this.readThrough = isReadThrough;
		return this;
	}

	public Builder<K, V> setWriteThrough(boolean isWriteThrough)
	{
		this.writeThrough = isWriteThrough;
		return this;
	}

	@Override
	public boolean isWriteThrough()
	{
		return writeThrough;
	}

	@Override
	public boolean isStatisticsEnabled()
	{
		return statistics;
	}

	@Override
	public boolean isManagementEnabled()
	{
		return management;
	}
	/**
	 * Returns whether the Cache behaves strictly JSR107 compliant
	 * 
	 * @return true if the Cache behaves strictly JSR107 compliant
	 */
	public boolean isStrictJSR107()
	{
		return strictJSR107;
	}

	public void setStrictJSR107(boolean strictJSR107)
	{
		this.strictJSR107 = strictJSR107;
	}

	@Override
	public Iterable<CacheEntryListenerConfiguration<K, V>> getCacheEntryListenerConfigurations()
	{
		return listenerConfigurations;
	}

	@Override
	public Factory<CacheWriter<? super K, ? super V>> getCacheWriterFactory()
	{
		return writerFactory;
	}

	@Override
	public Factory<ExpiryPolicy> getExpiryPolicyFactory()
	{
		return expiryPolicyFactory;
	}

	/**
	 * Sets the ExpiryPolicyFactory. It will overwrite any values set before via {@link #setMaxIdleTime(int, TimeUnit)}.
	 * @param factory The factory
	 * @return This Builder
	 */
	public Builder<K,V> setExpiryPolicyFactory(Factory<? extends ExpiryPolicy> factory)
	{
		if (expiryPolicyFactory == null)
		{
			this.expiryPolicyFactory = EternalExpiryPolicy.factoryOf();
		}
		else
		{
			@SuppressWarnings("unchecked")
			Factory<ExpiryPolicy> factoryCasted = (Factory<ExpiryPolicy>) factory;
			this.expiryPolicyFactory = (factoryCasted);
		}
		
		return this;
	}	

	/**
	 * Return Object.class casted suitably for {@link #getValueType()}
	 * @return Object.class
	 */
	@SuppressWarnings("unchecked")
	private Class<V> objectValueType()
	{
		return (Class<V>)Object.class;
	}

	/**
	 * Return Object.class casted suitably for {@link #getKeyType()}
	 * @return Object.class
	 */
	@SuppressWarnings("unchecked")
	private Class<K> objectKeyType()
	{
		return (Class<K>)Object.class;
	}

	public void addCacheEntryListenerConfiguration(CacheEntryListenerConfiguration<K, V> listenerConfiguration)
	{
		listenerConfigurations.add(listenerConfiguration);
	}

	public void removeCacheEntryListenerConfiguration(CacheEntryListenerConfiguration<K, V> listenerConfiguration)
	{
		listenerConfigurations.remove(listenerConfiguration);
	}
}
