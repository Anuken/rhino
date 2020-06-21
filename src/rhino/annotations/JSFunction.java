package rhino.annotations;

import rhino.*;

import java.lang.annotation.*;

/**
 * An annotation that marks a Java method as JavaScript function. This can
 * be used as an alternative to the <code>jsFunction_</code> prefix desribed in
 * {@link ScriptableObject#defineClass(Scriptable, java.lang.Class)}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JSFunction{
    String value() default "";
}
