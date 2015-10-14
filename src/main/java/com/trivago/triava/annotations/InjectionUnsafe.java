package com.trivago.triava.annotations;

/**
 * Documents why a field or method is or might be Injection <b>unsafe</b>. Main use case for this annotation
 * are SQL injections, but is also useful for REST services, Cache servers or NOSQL databases.
 */
public @interface InjectionUnsafe
{
	enum UnsafeReason
	{
		/**
		 * The value is used in a complex manner or in so many places that there serious doubts on the safety.
		 */
		UnclearUsage,
		/**
		 * The value is sent unquoted to an external service which has not been verified for injection safety.
		 * This means, the request ot the external system is properly quoted (er.g. URL-Encoding for a REST
		 * service), but the external service is not injection safe or has not yet been fully checked.
		 */
		ForwardedToUnsafeService
	}

	/**
	 * The risk, if an attack would succeed
	 */
	enum Risk
	{
		Low, Mid, High
	}

	/**
	 * The likelyhood of a successful attack 
	 */
	enum Exploitability
	{
		/**
		 * An exploit is easy to perform.
		 */
		Easy,
		
		/**
		 * Exploit code or instructions are already known
		 */
		ExploitCodeExists,

		/**
		 * Even with full knowledge of all facts, like version numbers, it is hard to perform an attack.
		 * For example because it requires a vast amount of requests or a microsecond-precise timing over
		 * networks.
		 */
		Difficult 
	}
	
	String comment() default "";
	Exploitability exploitability();
	Risk risk();

	/**
	 * @return Why the field or method is injection <b>unsafe</b>.
	 */
	UnsafeReason reason();
}
