package com.trivago.triava.annotations;


/**
 * Documents why a field or method is or might be Injection <b>unsafe</b> Mostly we talk about SQL injection, but input might be
 * also used for other systems like Cache servers or non-relational or non-SQL databases.
 */
public @interface InjectionUnsafe
{
	enum UnsafeReason { UnclearUsage, ForwardedToUnsafeService }
		
	String comment() default "";
	/**
	 * Whether why the given field is SQL injection <b>unsafe</b>.
	 * <ul>
	 * <li>
	 * UnclearUsage = The value is used in a complex manner or in so many places that there serious doubts on the safety.
	 * </li>
	 * <li>
	 * ForwardedToUnsafeService = The value is sent unquoted to an external service which has not been verified for injection safety.
	 * This means, the request ot the external system is properly quoted (er.g. URL-Encoding for a REST service), but the external service
	 * is not injection safe or has not yet been fully checked.  
	 * </li>
	 * </ul>
	 * 
	 * @return Why the field or method is injection <b>unsafe</b>.
	 */
	UnsafeReason reason();
}
