package com.trivago.triava.tcache.core;

import java.util.Collection;

import javax.cache.Cache;
import javax.cache.integration.CacheWriter;
import javax.cache.integration.CacheWriterException;

/**
 * A CacheWriter that does nothing
 * @author cesken
 *
 * @param <K> The key class
 * @param <V> The value class
 */
public class NopCacheWriter<K,V> implements CacheWriter<K, V>
{
	@Override
	public void write(Cache.Entry<? extends K, ? extends V> entry) throws CacheWriterException
	{
	}

	@Override
	public void writeAll(Collection<Cache.Entry<? extends K, ? extends V>> entries) throws CacheWriterException
	{
	}

	@Override
	public void delete(Object key) throws CacheWriterException
	{
	}

	@Override
	public void deleteAll(Collection<?> keys) throws CacheWriterException
	{
	}

}
