package com.trivago.triava.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Use this annotation if your class or field is not Year 2038 safe. Usually this happens if you use a 32 bit
 * unix timestamp.
 * <br>
 * <b>Usage directions</b>: Use this annotation sparsely! If it is easy, rather fix the issue instead of annotating it.
 * 
 * See https://en.wikipedia.org/wiki/Year_2038_problem for the background.
 */
@Retention(RetentionPolicy.SOURCE)
public @interface NotYear2038Safe
{
	String comment();
}
