package com.trivago.triava.annotations;

/**
 * Classes or methods marked with this Annotation indicate that this API is not stable, and may change with
 * major versions.
 * 
 * @author cesken
 *
 */
public @interface Beta
{
	String comment() default "";
}
