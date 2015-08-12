package com.trivago.commons.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.trivago.triava.annotations.TriavaCandidate;
import com.trivago.triava.annotations.TriavaCandidate.JavaPackage;

/**
 * Use this annotation if your class or field is not Year 2038 safe. Usually this happens if you use a 32 bit
 * unix timestamp.
 * <br>
 * <b>Usage directions</b>: Use this annotation sparesly! If it is easy, rather fix the issue instead of annotating it.
 * 
 * See http://de.wikipedia.org/wiki/Jahr-2038-Problem for the background.
 */
@TriavaCandidate(javapackage=JavaPackage.Annotation)
@Retention(RetentionPolicy.SOURCE)
public @interface NotYear2038Safe
{
	String comment();
}
