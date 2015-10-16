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
* A flag annotation to tag fields which should not get counted in the ObjectSizeCalculator.
* This annotation is to be used with a modified ObjectSizeCalculator from
* https://github.com/twitter/commons/pull/373.  It must be set with
* ObjectSizeCalculator#setIgnoreFieldAnnotation(Class&lt;? extends Annotation&gt; annotation).
* <p>
* Target use is to ignore object
* references not of interest. For example one can annotate Thread instances, to avoid
* measuring the whole Heap due to the ClassLoader reference in the Thread class.
* Another use case when one is doing multiple measurements which share references:
* Annotate one of the references to avoid duplicated object counting.
* 
* <p>
* References are counted with the size of a reference in the memory model. Primitive types cannot be ignored,
* they are always counted.
*
* @author Christian Esken, trivago GmbH
*
*/
@Beta(comment="Pull request 373 has not yet been processed")
@Retention(RetentionPolicy.RUNTIME)
public @interface ObjectSizeCalculatorIgnore
{
	String reason() default "";
}

