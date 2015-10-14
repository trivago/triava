/*********************************************************************************
 * Copyright 2015-present trivago GmbH
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **********************************************************************************/

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
