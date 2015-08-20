package com.trivago.triava.annotations;

import com.trivago.triava.annotations.TriavaCandidate;
import com.trivago.triava.annotations.TriavaCandidate.JavaPackage;

/**
 * Documents how a field or method is made Injection safe. Mostly we talk about SQL injection, but input might be
 * also used for other systems like Cache servers or non-relational or non-SQL databases.
 * Normalizing or discarding input is the preferred style. Quoting the output is dangerous, because you have to do
 * it for each SQL statement separately. 
 */
@TriavaCandidate(javapackage=JavaPackage.Annotation)
public @interface InjectionSafe
{
	enum Reason { InputNormalized, RequestRejected, OutputQuoted, NoExternalUsage, TrustedSource }
		
	String comment() default "";
	/**
	 * Whether why the given field is SQL injection safe.
	 * <ul>
	 * <li>
	 * InputNormalized = The value gets normalized (e.g. dangerous characters removed)
	 * </li>
	 * <li>
	 * RequestRejected = The whole request will be rejected.
	 * </li>
	 * <li>
	 * OutputQuoted = The value gets quoted wherever it is used in external systems. A proper class like
	 * Apache's StringEscapeUtils should be used for quoting.
	 * </li>
	 * <li>
	 * NoExternalUsage = The value is not used in external systems. Only use this if you are very sure it will not
	 * change in the future.
	 * </li>
	 * <li>
	 * TrustedSource = The input comes from a 100% trusted source. Values that stem from a URL are <b>never</b> to be
	 * trusted, as a caller might send arbitrary values in an URL.
	 * </li>
	 * </ul>
	 * 
	 * @return Why the field or method is injection safe.
	 */
	Reason reason();
}
