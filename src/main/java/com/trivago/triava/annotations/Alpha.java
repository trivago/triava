package com.trivago.triava.annotations;

/**
 * Classes or methods marked with this Annotation indicate that this API is not stable, and may change anytime,
 * even within minor versions.
 * 
 * @author cesken
 *
 */
public @interface Alpha
{
	String comment() default "";
}
