package com.trivago.triava.annotations;

import java.lang.annotation.*;

import com.trivago.triava.annotations.TriavaCandidate;
import com.trivago.triava.annotations.TriavaCandidate.JavaPackage;

/**
* A flag annotation to tag fields which should not get counted in the ObjectSizeCalculator. This is useful
* for references to other objects not of interest. For example, references to objects that are from a in-heap Cache
* may be not of interest, to avoid duplicated counting when one is doing an extra measurement for that Cache.
* <p>
* Using this annotation REQUIRES it to be available in the runtime classpath, as it uses RetentionPolicy.RUNTIME.
* When usage of this annotation is not feasible, an own annotation can be used. It must be set with
* ObjectSizeCalculator#setIgnoreFieldAnnotation(Class<? extends Annotation> annotation). 
* 
* <p>
* References are counted with the size of a reference in the memory model. Primitive types cannot be ignored,
* they are always counted.
*
* @author Christian Esken, trivago GmbH
*
*/
@TriavaCandidate(javapackage=JavaPackage.Annotation)
@Retention(RetentionPolicy.RUNTIME)
public @interface ObjectSizeCalculatorIgnore
{
	String reason() default "";
}

