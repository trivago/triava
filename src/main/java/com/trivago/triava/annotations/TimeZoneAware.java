package com.trivago.triava.annotations;

import com.trivago.triava.annotations.TriavaCandidate;
import com.trivago.triava.annotations.TriavaCandidate.JavaPackage;

/**
 * The purpose  of this annotation is to document how TimeZone aware Trivago's methods are, especially those in
 * TimeUtil. The background is that the time zone of several dates are not documented, including the booking period
 * fromDate and toDate in the request from PHP. PHP does not send UTC, but actually the current local time, which is
 * most of the times either CET, or CEST, resulting in UTC+1 or UTC+2.
 * <p>
 * <b>Aftermath</b>: After annotating several methods in the TimeUtil class the result is disastrous. <b>Only because
 * the MS/PS is Timezone unaware as PHP, the whole system works as expected</b>. If MS and PHP have different Timezone
 * descriptions or changing CET to CEST differently due to unequal clocks, the results will blow: fromDate and toDate
 * will shift back one day. 
 */
@TriavaCandidate(javapackage=JavaPackage.Annotation)
public @interface TimeZoneAware
{
	enum State { Independent, Aware, NotAware, CheckPending }
		
	String comment() default "";
	/**
	 * Whether the method is aware of timezones.
	 * <ul>
	 * <li>
	 * Aware = Yes, it is aware and treats the timezone as documented.
	 * </li>
	 * <li>
	 * NotAware = No, timezone is not taken into account. The default timezone is used.
	 * </li>
	 * <li>
	 * Independent = Timezone doesn't matter. Method does not use Timezone relevant methods or objects.
	 * </li>
	 * <li>
	 * CheckPending = We don't know yet. But the method should be checked.
	 * </li>
	 * </ul>
	 * 
	 * @return Whether the method is aware of timezones
	 */
	State aware();
}
