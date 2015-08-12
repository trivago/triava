package com.trivago.triava.annotations;

/**
 * A temporary annotation to mark classes or methods that are candidates for the triava library. 
 * @author cesken
 *
 */
public @interface TriavaCandidate
{
	public enum JavaPackage{ Annotation, Collection, Concurrent,  Net, tCache, Util, TO_BE_DETERMINED }
	
	String comment() default "";
	JavaPackage javapackage();
}
