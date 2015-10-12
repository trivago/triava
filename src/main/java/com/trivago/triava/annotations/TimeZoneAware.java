package com.trivago.triava.annotations;

/**
 * Documents whether a certain method is time zone aware.
 */
public @interface TimeZoneAware
{
	enum State
	{
		/**
		 * Time zone doesn't matter. Method does not use time zone relevant methods or objects.
		 */
		Independent,
		/**
		 * The method is aware and treats the time zone as documented.
		 */
		Aware,
		/**
		 * The time zone is not taken into account. The default time zone is used.
		 */
		NotAware,
		/**
		 * The method includes time functionality, but has not yet been checked.
		 */
		CheckPending
	}

	String comment() default "";

	/**
	 * 
	 * @return Whether the method is aware of time zones
	 */
	State aware();
}
