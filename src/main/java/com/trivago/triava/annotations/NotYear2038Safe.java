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
