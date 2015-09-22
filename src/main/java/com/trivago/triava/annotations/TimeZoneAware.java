package com.trivago.triava.annotations;


/**
 * Documents whether a certain method is time zone aware.  
 */
public @interface TimeZoneAware
{
	enum State { Independent, Aware, NotAware, CheckPending }
		
	String comment() default "";
	/**
	 * Whether the method is aware of time zones.
	 * <ul>
	 * <li>
	 * Aware = Yes. The method is aware and treats the time zone as documented.
	 * </li>
	 * <li>
	 * NotAware = No. The time zone is not taken into account. The default time zone is used.
	 * </li>
	 * <li>
	 * Independent = Time zone doesn't matter. Method does not use time zone relevant methods or objects.
	 * </li>
	 * <li>
	 * CheckPending = The method includes time functionality, but has not yet been checked.
	 * </li>
	 * </ul>
	 * 
	 * @return Whether the method is aware of time zones
	 */
	State aware();
}
