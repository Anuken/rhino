package rhino;

import java.lang.reflect.*;

/**
 * Creates the classes of JavaAdapter objects without generating bytecode, for platforms that can't load it.
 * Set {@link JavaAdapter#adapterFactory} to use one.
 */
public interface AdapterFactory{
    /**
     * Creates a subclass of superClass implementing interfaces, which calls the handler of each instance for the overrides.
     * @param name a unique binary name for the class
     * @param overrides public or protected methods of superClass and interfaces
     */
    Class<?> createClass(String name, Class<?> superClass, Class<?>[] interfaces, Method[] overrides);

    /**
     * Creates an instance of a class from {@link #createClass}. Overrides called by the constructor already use the handler.
     * @param constructor a constructor of the superclass
     * @param args arguments of the constructor, with primitives boxed
     */
    Object newInstance(Class<?> adapterClass, Constructor<?> constructor, Object[] args, Handler handler);

    /** Calls the superclass's implementation of an overridden method. */
    Object invokeSuper(Object adapter, Method method, Object[] args);

    interface Handler{
        /**
         * Called for every call of an override.
         * @param method the index of the method in the overrides passed to {@link #createClass}
         * @param args the arguments, with primitives boxed
         * @return the result, boxed as the return type if it's primitive
         */
        Object invoke(Object adapter, int method, Object[] args);
    }
}
