package com.trivago.triava.annotations;

/**
 * Documents how a field or method is made Injection safe. Mostly we talk about SQL injection, but input might
 * be also used for other systems like Cache servers or non-relational or non-SQL databases, depending on
 * their query language. Normalizing or discarding input is the preferred style. Quoting the output is
 * dangerous, because you have to do it for each SQL statement separately.
 */
public @interface InjectionSafe
{
	enum Reason
	{
		/**
		 * The value gets normalized (e.g. dangerous characters removed)
		 */
		InputNormalized,
		/**
		 * The whole request will be rejected.
		 */
		RequestRejected,
		/**
		 * The value gets quoted wherever it is used in external systems. A proper class like Apache's
		 * StringEscapeUtils should be used for quoting.
		 */
		OutputQuoted,
		/**
		 * The value is not used in external systems. Only use this if you are very sure it will not change in
		 * the future.
		 */
		NoExternalUsage,
		/**
		 * The input comes from a 100% trusted source. A trustworthy source may be data from a config file shipping
		 * with the software. In general be very careful with TrustedSource. Values that stem from a URL <b>SHOULD NOT</b> be
		 * trusted, as a caller might send arbitrary values in an URL. Other sources like a DB may look more trustworthy, but
		 * a malicious user may have found a way to compromise also DB content. 
		 */
		TrustedSource
	}

	String comment() default "";

	/**
	 * @return Why the field or method is injection safe.
	 */
	Reason reason();
}
