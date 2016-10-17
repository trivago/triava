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

package com.trivago.triava.tcache.eviction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.cache.CacheException;
import javax.cache.CacheManager;
import javax.cache.configuration.CacheEntryListenerConfiguration;
import javax.cache.configuration.CompleteConfiguration;
import javax.cache.configuration.Configuration;
import javax.cache.event.EventType;
import javax.cache.integration.CacheLoader;
import javax.cache.integration.CacheLoaderException;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;
import javax.cache.integration.CompletionListener;
import javax.cache.processor.EntryProcessor;
import javax.cache.processor.EntryProcessorException;
import javax.cache.processor.EntryProcessorResult;

import com.trivago.triava.tcache.action.Action;
import com.trivago.triava.tcache.action.ActionRunner;
import com.trivago.triava.tcache.action.DeleteAction;
import com.trivago.triava.tcache.action.DeleteOnValueAction;
import com.trivago.triava.tcache.action.GetAndPutAction;
import com.trivago.triava.tcache.action.GetAndRemoveAction;
import com.trivago.triava.tcache.action.PutAction;
import com.trivago.triava.tcache.action.ReplaceAction;
import com.trivago.triava.tcache.action.WriteBehindActionRunner;
import com.trivago.triava.tcache.action.WriteThroughActionRunner;
import com.trivago.triava.tcache.core.Builder;
import com.trivago.triava.tcache.core.NopCacheWriter;
import com.trivago.triava.tcache.core.TCacheConfigurationBean;
import com.trivago.triava.tcache.core.TCacheEntryIterator;
import com.trivago.triava.tcache.core.TCacheJSR107Entry;
import com.trivago.triava.tcache.core.TCacheJSR107MutableEntry;
import com.trivago.triava.tcache.event.ListenerCollection;
import com.trivago.triava.tcache.expiry.Constants;
import com.trivago.triava.tcache.statistics.TCacheStatisticsBean;
import com.trivago.triava.tcache.statistics.TCacheStatisticsBean.StatisticsAveragingMode;
import com.trivago.triava.tcache.util.ChangeStatus;
import com.trivago.triava.tcache.util.KeyValueUtil;

/**
 * A Java Caching implementation.
 * 
 * - Read-Through Caching  : 
 * - Write-Through Caching : CHECKED
 * - Statistics            :
 * - Event sending         :
 * - Consistency table     :
 * - ExpiryPolicy table    :
 *  
 * 
 * @author cesken
 *
 * @param <K> The Key type
 * @param <V> The Value type
 */
public class TCacheJSR107<K, V> implements javax.cache.Cache<K, V>
{
	final Cache<K,V> tcache;
	final TCacheConfigurationBean<K,V> configurationBean;
	final KeyValueUtil<K,V> kvUtil;
	volatile ActionRunner<K,V> actionRunner;
	volatile ActionRunner<K,V> actionRunnerWriteBehind;


	TCacheJSR107(Cache<K,V> tcache)
	{
		this.tcache = tcache;
		this.configurationBean = new TCacheConfigurationBean<K,V>(tcache);
		this.kvUtil = new KeyValueUtil<K,V>(tcache.id());
		refreshActionRunners();
	}

	void refreshActionRunners()
	{
		this.actionRunner = new WriteThroughActionRunner<K,V>(tcache);
		this.actionRunnerWriteBehind = new WriteBehindActionRunner<K,V>(tcache);
	}
	
	@Override
	public void clear()
	{
		throwISEwhenClosed();
		tcache.clear();	
	}

	@Override
	public void close()
	{
		// TODO A close must unregister the Cache
		tcache.shutdown();
	}

	@Override
	public boolean containsKey(K key)
	{
		throwISEwhenClosed();
		return tcache.containsKey(key);
	}

	@Override
	public void deregisterCacheEntryListener(CacheEntryListenerConfiguration<K, V> listenerConfiguration)
	{
		throwISEwhenClosed();
		
		tcache.listeners.deregisterCacheEntryListener(listenerConfiguration);
		refreshActionRunners();
	}

	@Override
	public V get(K key)
	{
		throwISEwhenClosed();

		return tcache.get(key);
	}

	@Override
	public Map<K, V> getAll(Set<? extends K> keys)
	{
		throwISEwhenClosed();

		Map<K, V> result = new HashMap<>(keys.size());
		for (K key : keys)
		{
			V value = tcache.get(key);
			if (value != null)
			{
				result.put(key, value);
			}
		}
		
		return result;
	}

	@Override
	public V getAndPut(K key, V value)
	{
		throwISEwhenClosed();

		Action<K, V, Object> action = new GetAndPutAction<K, V, Object>(key, value, null);
		V result = null;
		if (actionRunnerWriteBehind.preMutate(action))
		{
			Holders<V> holders = tcache.putToMapI(key, value, Constants.EXPIRY_NOCHANGE, tcache.cacheTimeSpread(), false);
		
			
			ChangeStatus changeStatus = null;
			if (holders != null)
			{
				if (holders.oldHolder != null)
				{
					action.setEventType(EventType.UPDATED);
					result = holders.oldHolder.peek();
					changeStatus = ChangeStatus.CHANGED;
	//				newHolder.setMaxIdleTime(oldHolder.getMaxIdleTime()); // TODO What to do with expiryForUpdateSecs(). How to apply this here, including the -1 case
				}
				else
				{
					// else: Created or invalid
					if (holders.newHolder != null)
					{
						changeStatus = ChangeStatus.CREATED;
						action.setEventType(EventType.CREATED);
					}
					// else: Keep changeStatus == null
				}
			}

			actionRunnerWriteBehind.postMutate(action, changeStatus);
		}
		
		action.close();
		return result;
	}

	@Override
	public V getAndRemove(K key)
	{
		throwISEwhenClosed();

		GetAndRemoveAction<K, V, Object> action = new GetAndRemoveAction<K, V, Object>(key);
		if (actionRunner.preMutate(action))
		{
			V oldValue = tcache.remove(key);
			action.setRemoved(oldValue != null);

			// TCK CHALLENGE oldValue needs to be passed as (old)Value, otherwise NPE at org.jsr107.tck.event.CacheListenerTest$MyCacheEntryEventFilter.evaluate(CacheListenerTest.java:344)
			actionRunner.postMutate(action, oldValue);			
			return oldValue;
		}
	
		return null;
	}

	@Override
	public V getAndReplace(K key, V value)
	{
		throwISEwhenClosed();

		Action<K, V, Object> action = new ReplaceAction<K, V, Object>(key, value, EventType.UPDATED);
		V oldValue = null;
		if (actionRunnerWriteBehind.preMutate(action))
		{
			oldValue = tcache.getAndReplace(key, value);
			boolean replaced = oldValue != null;
			ChangeStatus changeStatus = replaced ? ChangeStatus.CHANGED : ChangeStatus.UNCHANGED;
			actionRunnerWriteBehind.postMutate(action, changeStatus);
		}
		action.close();
		return oldValue;				
	}

	@Override
	public CacheManager getCacheManager()
	{
		return tcache.builder.getFactory();
	}

	@SuppressWarnings("unchecked")
	@Override
	public <C extends Configuration<K, V>> C getConfiguration(Class<C> clazz)
	{
		Builder<K, V> builder = tcache.builder;

		if (clazz.isAssignableFrom(javax.cache.configuration.Configuration.class))
		{
			return (C)builder;
		}
		if (clazz.isAssignableFrom(CompleteConfiguration.class))
		{
			return (C)builder;
		}
		
		throw new IllegalArgumentException("Unsupported configuration class: " + clazz.toString());
	}

	@Override
	public String getName()
	{
		return tcache.id();
	}

	@Override
	public <T> T invoke(K key, EntryProcessor<K, V, T> entryProcessor, Object... args) throws EntryProcessorException
	{
		throwISEwhenClosed();
		if (entryProcessor == null)
		{
			// TCK CHALLENGE: While not explicitly mentioned in the Javadocs, the TCK requires a NullPointerException.
			// This makes sense, but should be added to the Javadocs.
			throw new NullPointerException("entryProcessor is null");
		}
		
		// -1- Get value (we are not loading it via CacheLoader, even though it is in the Specs. See https://github.com/jsr107/RI/issues/54)
		TCacheJSR107MutableEntry<K, V> me = invokeBuildMutableEntry(key);
		// -2- Run EntryProcessor
		try
		{
			T result = processEntryProcessor(entryProcessor, me, args);
			return result;
		}
		catch (EntryProcessorException epe)
		{
			// Do not wrap EntryProcessorException, to pass the JSR107 TCK. This is a likely a TCK bug. See https://github.com/jsr107/jsr107tck/issues/85
			throw epe;
		}
		catch (Exception exc)
		{
			throw new EntryProcessorException(exc);
		}

	}

	private TCacheJSR107MutableEntry<K, V> invokeBuildMutableEntry(K key)
	{
		AccessTimeObjectHolder<V> holder = tcache.peekHolder(key);
		V value = holder != null ? holder.peek() : null; // Create surrogate "null" if not existing (JSR107)
		
		// Statistics effects are not properly described in the Spec. See https://github.com/jsr107/RI/issues/54
		// Thus I am following here what the RI does. 
		if (value != null)
			tcache.statisticsCalculator.incrementHitCount();
		else
			tcache.statisticsCalculator.incrementMissCount();
		
		CacheLoader<K, V> loader = tcache.builder.isReadThrough() ? tcache.loader : null;
		TCacheJSR107MutableEntry<K, V> me = new TCacheJSR107MutableEntry<K, V>(key, value, loader);
		return me;
	}

	@Override
	public <T> Map<K, EntryProcessorResult<T>> invokeAll(Set<? extends K> keys, EntryProcessor<K, V, T> entryProcessor,
			Object... args)
	{
		throwISEwhenClosed();
		if (entryProcessor == null)
		{
			// TCK CHALLENGE: While not explicitly mentioned in the Javadocs, the TCK requires a NullPointerException.
			// This makes sense, but should be added to the Javadocs.
			throw new NullPointerException("entryProcessor is null");
		}
		Map<K, EntryProcessorResult<T>> resultMap = new HashMap<>();
		for (K key : keys)
		{
			try
			{
				TCacheJSR107MutableEntry<K, V> me = invokeBuildMutableEntry(key);
				// TODO CacheWriter behavior is
				// here not JSR107 compliant.
				// writeAll() must be called
				// instead of individual write()
				// calls
				T result = processEntryProcessor(entryProcessor, me, args);
				if (result != null)
				{
					resultMap.put(key, new EntryProcessorResultTCache<T>(result));
				}
			}
			catch (Exception exc)
			{
				resultMap.put(key, new EntryProcessorResultTCache<T>(exc));
			}
		}
		
		return resultMap;
	}

	/**
	 * Process the given EntryProcessor and apply the change (delete or setValue) requested by that
	 * EntryProcessor. Mutable changes have no direct impact, but are be applied after after the
	 * EntryProcessor has returned, but before this method returns.
	 * 
	 * @param entryProcessor The entry processor to execute
	 * @param me The MutableEntry
	 * @param args The client arguments
	 * @return The result from the EntryProcessor
	 */
	private <T> T processEntryProcessor(EntryProcessor<K, V, T> entryProcessor, TCacheJSR107MutableEntry<K, V> me, Object... args)
	{
		T result = entryProcessor.process(me, args);
		K key;
		V value;
		switch (me.operation())
		{
			case REMOVE:
				key = me.getKey();
				remove(key);
				break;
			case SET:
				key = me.getKey();
				value = me.getValue();
				put(key, value);
				break;
			case LOAD:
				key = me.getKey();
				value = me.getValue();
				// Load must not write through, according to the TCK and RI
				// From the spec it is not clear why. See 		// -1- Get value (we are not loading it via CacheLoader, even though it is in the Specs. See https://github.com/jsr107/RI/issues/54
				putNoWriteThrough(key, value);
				break;				
			case REMOVE_WRITE_THROUGH:
				key = me.getKey();
				remove(key, false);
				break;
			case GET:
				key = me.getKey();
				AccessTimeObjectHolder<V> holder = tcache.peekHolder(key);
				if (holder != null)
				{
					// JSR107 1.0 (p.63) mandates that we call getExpiryForAccess() if we read it (except if was loaded) 
					holder.updateMaxIdleTime(tcache.expiryPolicy.getExpiryForAccess());
				}
				break;
			default:
				break;
		}
		return result;
	}

	private static class EntryProcessorResultTCache<T> implements EntryProcessorResult<T>
	{
		Object result;
		
		EntryProcessorResultTCache(T result)
		{
			this.result = result;
		}

		EntryProcessorResultTCache(Exception exc)
		{
			if (exc instanceof EntryProcessorException)
			{
				// Do not wrap EntryProcessorException, to pass the JSR107 TCK. This is a likely a TCK bug. See https://github.com/jsr107/jsr107tck/issues/85
				this.result = exc;
			}
			else
			{
				this.result = new EntryProcessorException(exc);
			}
		}

		@Override
		public T get() throws EntryProcessorException
		{
			if (result instanceof EntryProcessorException)
				throw (EntryProcessorException)result;
			else
			{
				@SuppressWarnings("unchecked")
				T ret = (T)result;
				return ret;
			}
		}
		
	}
	
	@Override
	public boolean isClosed()
	{
		return tcache.isClosed();
	}

	@Override
	public Iterator<javax.cache.Cache.Entry<K, V>> iterator()
	{
		throwISEwhenClosed();

		TCacheEntryIterator<K, V> it = new TCacheEntryIterator<K,V>(this.tcache, tcache.objects, tcache.expiryPolicy);
		return it;
	}

	// TODO #loadAll() must be implemented ASYNCHRONOUSLY according to JSR107 Spec
	@Override
	public void loadAll(Set<? extends K> keys, boolean replaceExistingValues, CompletionListener listener)
	{
		throwISEwhenClosed();

		for (K key : keys) // implicit null check on keys => will throw NPE
		{
			kvUtil.verifyKeyNotNull(key);
		}
		
		CacheLoader<K, V> loader = tcache.loader;

		if (loader == null)
		{
			if (listener != null)
				listener.onException(new CacheException("Cache does not support loadAll as no CacheLoader is defined: " + this.getName()));
			return;
		}

		try
		{
			Map<K, V> loadedEntries = null;
			try
			{
				if (replaceExistingValues)
				{
					loadedEntries = loader.loadAll(keys);
				}
				else
				{
					final Set<K> finalKeys = new HashSet<>();
					
					// Only a single Thread may iterate keys (may be a not thread-safe Set)
					for (K key : keys)
					{
						if (!containsKey(key))
						{
							finalKeys.add(key);
						}
					}
	
					loadedEntries = loader.loadAll(finalKeys);
				}
			}
			catch (Exception exc)
			{
				// Wrap loader Exceptions in CacheLoaderExcpeption. The TCK requires it, but it is possibly a TCK bug.

				// TODO Check back after clarifying whether this requirement is a TCK bug:
				// https://github.com/jsr107/jsr107tck/issues/99
				String message = "CacheLoader " + tcache.id() + " failed to load keys";
				throw new CacheLoaderException(message + " This is a wrapped exception. See https://github.com/jsr107/jsr107tck/issues/99", exc);
			}
			
			if (loadedEntries != null)
			{
				Map<K, V> cleanedEntries = new HashMap<>(2*loadedEntries.size());
				for (java.util.Map.Entry<K, V> entry: loadedEntries.entrySet())
				{
					K key = entry.getKey();
					V value = entry.getValue();
					if (key == null || value == null)
						continue; //invalid => do not load
					
					cleanedEntries.put(key, value);
				}
				
				putAll(cleanedEntries, false);
			}
			
			if (listener != null)
				listener.onCompletion();
		}
		catch (Exception exc)
		{
			if (listener != null)
				listener.onException(exc);
		}
		
		

	}

	void putNoWriteThrough(K key, V value)
	{
		put0(key, value, false);		
	}
	
	@Override
	public void put(K key, V value)
	{
		put0(key, value, true);
	}

	void put0(K key, V value, boolean writeThrough)
	{
		throwISEwhenClosed();
		kvUtil.verifyKeyAndValueNotNull(key, value);

		Action<K,V,Object> action = new PutAction<>(key, value, EventType.CREATED, false, writeThrough);

		if (actionRunner.preMutate(action))
		{
			Holders<V> holders = tcache.putToMapI(key, value, Constants.EXPIRY_NOCHANGE,
					tcache.cacheTimeSpread(), false);
			final EventType eventType;
			if (holders == null)
				eventType = null;
			else
			{
				if (holders.newHolder == null || holders.newHolder.isInvalid())
					eventType = null; // new is invalid
				else
					eventType = holders.oldHolder == null ? EventType.CREATED : EventType.UPDATED;
			}
			action.setEventType(eventType);
			actionRunner.postMutate(action);	
		}
		action.close();
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> entries)
	{
		putAll(entries, true);
	}
	
	private void putAll(Map<? extends K, ? extends V> entries, boolean callWriter)
	{
		throwISEwhenClosed();

		if (entries.isEmpty())
			return;

		verifyEntriesNotNull(entries);

		// -1- Run Writer ---
		// Hint: A CacheWriter dictates what to put for batch operations. If it fails to put, we must also not add it locally.
		// This is a requirement from JSR107 and possibly meant to keep the Cache consistent.
		// Thus we must execute the CacheWriter first

		CacheWriterException cacheWriterException = null;
		Collection<javax.cache.Cache.Entry<? extends K, ? extends V>> writerEntries = null;
		
		CacheWriter<K, V> writer = tcache.cacheWriter;
		if (writer == null || writer instanceof NopCacheWriter)
		{
			callWriter = false;
		}
		if (callWriter)
		{
			try
			{
				writerEntries = new ArrayList<>(entries.size());
				
				for (java.util.Map.Entry<? extends K, ? extends V> entry : entries.entrySet())
				{
					K key = entry.getKey();
					V value = entry.getValue();
					writerEntries.add(new TCacheJSR107Entry<K,V>(key, value));
				}
				writer.writeAll(writerEntries);
			}
			catch (Exception exc)
			{
				// Q uoting the JavaDocs of CacheWriterException:
				//    "A Caching Implementation must wrap any {@link Exception} thrown by a {@link CacheWriter} in this exception".
				cacheWriterException = new CacheWriterException(exc);
			}
		}

		// -2- Write to Cache -----------------------------------

		ListenerCollection<K, V> listeners = tcache.listeners;
		Map<K,V> createdEntries = listeners.hasListenerFor(EventType.CREATED) ? new HashMap<K,V>() : null;
		Map<K,V> updatedEntries = listeners.hasListenerFor(EventType.UPDATED) ? new HashMap<K,V>() : null;

		final boolean anyListenerInterested = createdEntries != null || updatedEntries != null;
		// Future directions: Micro-Benchmark this loop. Optimize it if necessary with a
		// "no-writer-nor-listener" version that contains no "if" checks
		
		final Map<? extends K, ? extends V> finalEntries;
		if (writerEntries != null)
		{
			finalEntries = new HashMap<>(entries);
			for (javax.cache.Cache.Entry<? extends K, ? extends V> writerEntry : writerEntries)
			{
				K key = writerEntry.getKey();
				if (finalEntries.containsKey(key))
				{
					finalEntries.remove(key);
				}
			}
		}
		else
		{
			finalEntries = entries;
		}
		
		
		for (java.util.Map.Entry<? extends K, ? extends V> entry : finalEntries.entrySet())
		{
			K key = entry.getKey();
			V value = entry.getValue();

			//AccessTimeObjectHolder<V> holder = tcache.putToMap(key, value, tcache.expiryPolicy.getExpiryForCreation(), tcache.cacheTimeSpread(), false, false);
			Holders<V> holders = tcache.putToMapI(key, value, tcache.expiryPolicy.getExpiryForCreation(), tcache.cacheTimeSpread(), false);
			
			if (anyListenerInterested)
			{
				boolean added = holders.newHolder != null;
				if (added)
				{
					Map<K, V> mapToAdd = (holders.oldHolder == null) ? createdEntries : updatedEntries;
					if (mapToAdd != null) // mapToAdd could be null here, if anyListenerInterested == true, but
											// only one (iow: CREATED xor UPDATED)
						mapToAdd.put(key, value);
				}
				// else: immediately expired
			}
		}

		// -2- Notify Listeners -----------------------------------

		if (createdEntries != null)
			tcache.listeners.dispatchEvents(createdEntries, EventType.CREATED, false);
		if (updatedEntries != null)
			tcache.listeners.dispatchEvents(updatedEntries, EventType.UPDATED, false);

		if (cacheWriterException != null)
		{
			throw cacheWriterException;
		}
	}

	private void verifyEntriesNotNull(Map<? extends K, ? extends V> entries)
	{
		// JSR107 spec clarification needed. The TCK check PutTest.putAll_NullKey() requires a total failure, and disallows partial success.
		// This is not explicitly in the Specs. Until this clarification is done, we do an explicit key and value null check before starting to put values in the cache.
		for (java.util.Map.Entry<? extends K, ? extends V> entry : entries.entrySet())
		{
			K key = entry.getKey();
			V value = entry.getValue();
			kvUtil.verifyKeyAndValueNotNull(key, value);
		}
	}

	private void verifyKeysNotNull(Set<? extends K> keys)
	{
		// JSR107 spec clarification needed. The TCK check removeAll_1arg_NullKey(org.jsr107.tck.RemoveTest)
		// requires a total failure, and disallows partial success.
		// This is not explicitly in the Specs. Until this clarification is done, we do an explicit key and
		// value null check before starting to put values in the cache.
		for (K key : keys)
		{
			kvUtil.verifyKeyNotNull(key);
		}
	}

	@Override
	public boolean putIfAbsent(K key, V value)
	{
		throwISEwhenClosed();

		PutAction<K,V,Object> action = new PutAction<>(key, value, EventType.CREATED, false);
		boolean added = false;
		if (actionRunnerWriteBehind.preMutate(action))
		{
			Holders<V> holders = tcache.putIfAbsentH(key, value);
			// For JSR107 putIfAbsent() should return whether a value was set.
			// It is set when the old holder was null AND the new holder is not null.
			// The latter case (new holder == null) can happen if it immediately expires.
			added = holders.oldHolder == null && holders.newHolder != null;
			if (added)
				actionRunnerWriteBehind.postMutate(action);
		}

		return added;
	}

	@Override
	public void registerCacheEntryListener(CacheEntryListenerConfiguration<K, V> listenerConfiguration)
	{
		throwISEwhenClosed();
		
		tcache.listeners.registerCacheEntryListener(listenerConfiguration);
		refreshActionRunners();
	}


	/**
	 * Returns normally with no side effects if this cache is open. Throws IllegalStateException if it is
	 * closed.
	 */
	private void throwISEwhenClosed()
	{
		if (isClosed())
			throw new IllegalStateException("Cache already closed: " + tcache.id());
	}

	@Override
	public boolean remove(K key)
	{
		return remove(key, true);
	}

	boolean remove(K key, boolean mutateLocal)
	{
		throwISEwhenClosed();
		kvUtil.verifyKeyNotNull(key);
		
		return removeInternal(key, null, mutateLocal);
	}

	
	@Override
	public boolean remove(K key, V value)
	{
		throwISEwhenClosed();
		kvUtil.verifyKeyAndValueNotNull(key, value);

		return removeInternal(key, value, true);
	}

	/**
	 * Internal remove code. Handles both 1-arg and 2-arg remove() methods.
	 * @param key
	 * @param value
	 * @param mutateLocal
	 * @return
	 */
	boolean removeInternal(K key, V value, boolean mutateLocal)
	{
		final DeleteAction<K,V,Object> action;
		
		boolean twoArgRemove = (value != null);		
		if (twoArgRemove)
		{
			/**
			 * 
			// TCK challenge
			// RemoveTest.shouldWriteThroughRemove_SpecificEntry() mandates that we only write through, if we happen to have the
			// (key,value) combination in the local cache. This can lead to non-deterministic behavior if the local cache has evicted
			// that Cache entry.
			 * 
			 * The reason could be an omission in the CacheWriter Interface: It simply does not have a write(key,value) method. To be discussed
			 */
			
			AccessTimeObjectHolder<V> holder = tcache.peekHolder(key);
			V valueInCache = holder != null ? holder.peek() : null;
			boolean mustWriteThrough = value.equals(valueInCache);
			action = new DeleteOnValueAction<K,V,Object>(key, mustWriteThrough);
			if (valueInCache != null && !mustWriteThrough)
			{
				// Value will not be removed, thus it is accessed
				holder.updateMaxIdleTime(tcache.expiryPolicy.getExpiryForAccess());
			}
		}
		else
		{
			action =  new DeleteAction<K,V,Object>(key);
		}

		boolean removed = false;
		if (actionRunner.preMutate(action))
		{
			if (mutateLocal)
			{
				if (value == null)
				{
					// 1-arg remove()
					V oldValue = tcache.remove(key);
					removed = oldValue != null;
					action.setRemoved(removed);
					actionRunner.postMutate(action, oldValue);
				}
				else
				{
					// 2-arg remove()
					boolean log = false; // value.equals("Sooty");
					if (log) System.out.println("About ot remove Sooty: " + key + "stats: " + tcache.statisticsCalculator);
					removed = tcache.remove(key, value);
					action.setRemoved(removed);
					if (log) System.out.println("After remove Sooty: " + key + "removed=" + removed + ", action=" + action + "stats: " + tcache.statisticsCalculator);
					actionRunner.postMutate(action, value);
					if (log) System.out.println("After postMutate Sooty: " + key + "removed=" + removed + ", action=" + action + "stats: " + tcache.statisticsCalculator);
				}
			}
			else
			{
				// Only Write-Through should be done in this case, to be JSR107 compliant. Normally this has already been performed done by actionRunner.preMutate().
				// In the future, when actionRunner can be WriteBehind, this code here can come into play. The instanceof check is a bit ugly, and we will
				// likely refine it when fully implementing Write-Behind.
				if (actionRunner instanceof WriteBehindActionRunner)
				{
					action.setRemoved(removed); // We are presuming(!) a local removal
					action.writeThrough(actionRunner, null);
				}
			}
		}

		action.close();
		
		// JSR107 Return whether a value was removed
		return removed;
	}

	@Override
	public void removeAll()
	{
		throwISEwhenClosed();
		
		removeAll(tcache.objects.keySet());
	}

	@Override
	public void removeAll(Set<? extends K> keys)
	{
		throwISEwhenClosed();
		if (keys.isEmpty())
		{
			// If keys are empty, the CacheWriter must not be called => do an explicit check
			return;
		}
		verifyKeysNotNull(keys);

		CacheWriterException cacheWriterException = null;

		// A CacheWriter dictates what to delete for batch operations. If it fails to delete, we must keep it
		// also locally.
		// This is a requirement from JSR107 and possibly meant to keep the Cache consistent.
		Set<? extends K> keysFromCacheWriter = null;
		CacheWriter<K, V> writer = tcache.cacheWriter;
		boolean callWriter = !(writer instanceof NopCacheWriter);

		try
		{
			if (callWriter)
			{
				keysFromCacheWriter = new HashSet<>(keys);
				writer.deleteAll(keysFromCacheWriter);
			}
		}
		catch (Exception exc)
		{
			// Quoting the JavaDocs of CacheWriterException:
			// "A Caching Implementation must wrap any {@link Exception} thrown by a {@link CacheWriter} in
			// this exception".
			cacheWriterException = new CacheWriterException(exc);
		}

		for (K key : keys)
		{
			if (keysFromCacheWriter != null)
			{
				if (keysFromCacheWriter.contains(key))
				{
					// keysFromCacheWriter are the failed deletes. We must skip to delete locally as well.
					continue;
				}
			}
			V oldValue = tcache.remove(key);
			boolean removed = oldValue != null;
			if (removed)
			{
				tcache.statisticsCalculator.incrementRemoveCount();
				// Future direction: This could be optimized to do dispatchEvents(Event-List).
				// To be done with the next major refactoring, as event lists need to be supported until the
				// very bottom of the call-stack, namely until ListenerCacheEventManager.
				tcache.listeners.dispatchEvent(EventType.REMOVED, key, null, oldValue);
			}
		}

		if (cacheWriterException != null)
		{
			throw cacheWriterException;
		}
	}

	@Override
	public boolean replace(K key, V value)
	{
		throwISEwhenClosed();
		kvUtil.verifyKeyAndValueNotNull(key, value);
		
		V oldHolder = getAndReplace(key, value);
		boolean replaced = (oldHolder != null);
		
		return replaced;
	}

	@Override
	public boolean replace(K key, V oldValue, V newValue)
	{
		throwISEwhenClosed();
		kvUtil.verifyKeyAndValueNotNull(key, newValue);
		kvUtil.verifyValueNotNull(oldValue);
		
		boolean replaced = false;
		Action<K, V, Object> action = new ReplaceAction<K, V, Object>(key, newValue, EventType.UPDATED);
		if (actionRunnerWriteBehind.preMutate(action))
		{
			ChangeStatus changeStatus = tcache.replace(key, oldValue, newValue);
			actionRunnerWriteBehind.postMutate(action, changeStatus);
			replaced = changeStatus == ChangeStatus.CHANGED;
		}

		return replaced;
	}

	@Override
	public <T> T unwrap(Class<T> clazz)
	{
		if (!(clazz.isAssignableFrom(Cache.class) || clazz.isAssignableFrom(TCacheJSR107.class)))
			throw new IllegalArgumentException("Cannot unwrap Cache to unsupported Class " + clazz);
		
		@SuppressWarnings("unchecked")
		T cacheCasted = (T)tcache;
		return cacheCasted;
	}

	public Object getCacheConfigMBean()
	{
		return configurationBean;
	}

	public Object getCacheStatisticsMBean()
	{
		return new TCacheStatisticsBean(tcache, tcache.statisticsCalculator, StatisticsAveragingMode.JSR107);
	}

}
