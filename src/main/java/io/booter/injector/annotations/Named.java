package io.booter.injector.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

/**
 * Convenient annotation for marking dependencies using simple strings. For example:
 * <pre>{@code
 * ...
 * public class MyClass {
 *     ...
 *     public MyClass(@Named("command-line") String[] arguments) {
 *         ...
 *     }
 * }
 * ...
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({PARAMETER, FIELD})
@Documented
@BindingAnnotation
public @interface Named {
    String value();
}