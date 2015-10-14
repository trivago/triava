package com.trivago.triava.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Use this annotation if your class or field is not Year 2038 safe. Usually this happens if a 32 bit
 * unix timestamp is used. Problem can happen for calculation of future events (pension plan), if the calculation is done
 * on a timestamp rather than a calendar.
 * <p>
 * <b>Usage directions</b>: Use this annotation sparsely! If it is easy, rather fix the issue instead of annotating it.
 * A proper use of this annotation is to actively document decisions to not make it safe (efficency reasons) 
 * 
 * See https://en.wikipedia.org/wiki/Year_2038_problem for the background.
 */
@Retention(RetentionPolicy.SOURCE)
public @interface NotYear2038Safe
{
	String comment();
}
