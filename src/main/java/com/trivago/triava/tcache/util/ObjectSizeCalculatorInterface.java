package com.trivago.triava.tcache.util;

/**
 * Interface for an ObjectSizeCalculator, that does a deep calculation of the object trees size in byte.
 * Implementations will typically use Java instrumentation for this calculation, but there are also
 * non-instrumented solutions like Twitter ObjectSizeCalculator. 
 * 
 * @author cesken
 *
 */
public interface ObjectSizeCalculatorInterface
{
	
	/**
	 * Calculates the deep memory footprint of {@code obj} in bytes, i.e. the memory taken by the 
	 * object graph using {@code obj} as a starting node of that graph. 
	 * 
	 * @param obj
	 * @return
	 */
	long calculateObjectSizeDeep(Object obj);
}
