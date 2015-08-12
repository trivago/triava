package com.trivago.commons.annotations;

import com.trivago.triava.annotations.TriavaCandidate;
import com.trivago.triava.annotations.TriavaCandidate.JavaPackage;

/**
 * Documents why a field or method is or might be Injection <b>unsafe</b> Mostly we talk about SQL injection, but input might be
 * also used for other systems like Cache servers or non-relational or non-SQL databases.
 */
@TriavaCandidate(javapackage=JavaPackage.Annotation)
public @interface InjectionUnsafe
{
	enum UnsafeReason { UsedInTooManyPlaces, SentToExternalSystem }
		
	String comment() default "";
	/**
	 * Whether why the given field is SQL injection <b>unsafe</b>.
	 * <ul>
	 * <li>
	 * UsedInTooManyPlaces = The value is used in so many places that we have doubts on the safety.
	 * </li>
	 * <li>
	 * SentToExternalSystem = We send the value unquoted to an external system where it has not been verified for injection safety. 
	 * </li>
	 * </ul>
	 * 
	 * @return Why the field or method is injection <b>unsafe</b>.
	 */
	UnsafeReason reason();
}
