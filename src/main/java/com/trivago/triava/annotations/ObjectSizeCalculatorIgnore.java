package com.trivago.triava.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
* A flag annotation to tag fields which should not get counted in the ObjectSizeCalculator.
* This annotation is to be used with a modified ObjectSizeCalculator from
* https://github.com/twitter/commons/pull/373.  It must be set with
* ObjectSizeCalculator#setIgnoreFieldAnnotation(Class<? extends Annotation> annotation).
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

