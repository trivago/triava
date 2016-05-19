package com.trivago.triava.tcache.core;

import javax.cache.event.EventType;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;

import com.trivago.triava.tcache.event.ListenerCollection;

/**
 * An ActionDispatchher delivers actions to all interested parties.
 * This implementation delivers actions to CacheWriter and ListenerCollection
 * @author cesken
 *
 * @param <K> The key type
 * @param <V> The value type
 */
public class ActionDispatcher<K,V>
{
	private final ListenerCollection<K, V> listeners;
	private final CacheWriter<K, V> cacheWriter;

	
	public ActionDispatcher(ListenerCollection<K, V> listeners, CacheWriter<K, V> cacheWriter)
	{
		this.listeners = listeners;
		this.cacheWriter = cacheWriter;
	}

	public void write(K key, V value, EventType eventType)
	{
		if (eventType != null)
			listeners.dispatchEvent(eventType, key, value);
		
		if (cacheWriter != null)
		{
			try
			{
				cacheWriter.write(new TCacheJSR107MutableEntry<K,V>(key, value));
			}
//			catch (CacheWriterException cwe)
//			{
//				//  Quoting the JavaDocs of CacheWriterException:
//				//    "A Caching Implementation must wrap any {@link Exception} thrown by a {@link CacheWriter} in this exception".
//				// Not sure whether this also applies to  CacheWriterException itself
//				throw cwe;
//			}
			catch (Exception exc)
			{
				//  Quoting the JavaDocs of CacheWriterException:
				//    "A Caching Implementation must wrap any {@link Exception} thrown by a {@link CacheWriter} in this exception".
				throw new CacheWriterException(exc);
			}
		}
	}

	public void delete(K key, V oldValue, boolean notifyListeners)
	{
//		System.out.println("ActionDispatcher.delete(" + key + ", " + oldValue + ", " + notifyListeners + "cacheWriter=" + cacheWriter);
		if (notifyListeners)
			listeners.dispatchEvent(EventType.REMOVED, key, oldValue);
		
		if (cacheWriter != null)
		{
			try
			{
				cacheWriter.delete(key);
			}
			catch (Exception exc)
			{
				//  Quoting the JavaDocs of CacheWriterException:
				//    "A Caching Implementation must wrap any {@link Exception} thrown by a {@link CacheWriter} in this exception".
				throw new CacheWriterException(exc);
			}
		}
	}
}
