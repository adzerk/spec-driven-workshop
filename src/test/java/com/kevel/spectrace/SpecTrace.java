package com.kevel.spectrace;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.jqwik.api.lifecycle.AddLifecycleHook;
import net.jqwik.api.lifecycle.PropagationMode;
import org.junit.jupiter.api.extension.ExtendWith;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@ExtendWith(SpecTraceJupiterExtension.class)
@AddLifecycleHook(value = SpecTracePropertyHook.class, propagateTo = PropagationMode.NO_DESCENDANTS)
public @interface SpecTrace {
    String[] value();
}
