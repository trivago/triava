package com.trivago.triava.collections;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Hash based implementation of {@link InternerInterface}. 
 * Identity is determined via {@link #equals(Object)} and {@link #hashCode()}, thus stored instances
 * must guarantee that hashCode is consistent with equals. This implementation can return different object
 * references in the case of two concurrent {@link #get(Object)} calls
 * 
 * <p>
 * <b>Speed</b>: The implementation is twice as fast as Guavas Interner, and 5 times as fast as String.intern().
 * Measurement via bb Microbenchmark tool (ticket SRT-6053). Time in microseconds (us) for 1000 interns:
 * <ul>
 * <li>18us triava HashInterner</li>
 * <li>32us Guava Weak Interner</li>
 * <li>39us Guava Strong Interner</li>
 * <li>88us String.intern() - the 88us average had a very high deviation.</li>
 * </ul>
 * 
 * @author cesken
 * @since 2012-08-10
 * 
 */
public class HashInterner<T> implements InternerInterface<T>
{
	// The implementation uses a ConcurrentMap, so we do not need to synchronize.
	private final ConcurrentMap<T, T> interningMap;

	/**
	 * Creates an Interner with 100 expected elements as sizing hint for the underlying Map.
	 */
	public HashInterner()
	{
		this(100);
	}
	
	/**
	 * Creates an Interner with expectedElements as sizing hint for the underlying Map.
	 */
	public HashInterner(int expectedElements)
	{
		interningMap = new ConcurrentHashMap<>(expectedElements);
	}
	
	@Override
	public T get(T value)
	{
		if ( value == null )
		{
			// Special code path: Return immediately for null value, as they are not allowed in ConcurrentHashMap.
			return null;
		}
		
		T sharedRefString = interningMap.get(value);
		if (sharedRefString != null)
			return sharedRefString;

		/*
		 * Not yet in interned. Add to Map and return new value.
		 * 
		 * Hint for concurrency: If multiple callers enter it with the same not-yet-contained
		 * value, then both will write. The later put() will win, and the former will use a non-shared value.
		 */
		interningMap.put(value, value);
		return value;
	}

	/**
	 * Returns the number of elements this instance has interned.
	 * @return
	 */
	public int size()
	{
		return interningMap.size();
	}

	@Override
	public String toString()
	{
		return "Interner " + this.hashCode() + " [" + size() + " elements]";
	}
	
	
}

