package rhino.annotations;

import rhino.*;

import java.lang.annotation.*;

/**
 * An annotation that marks a Java method as JavaScript setter. This can
 * be used as an alternative to the <code>jsSet_</code> prefix desribed in
 * {@link ScriptableObject#defineClass(Scriptable, java.lang.Class)}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface JSSetter{
    String value() default "";
}
