// API class

package rhino;

import java.lang.reflect.*;

public abstract class VMBridge{
    private static final ThreadLocal<Object[]> contextLocal = new ThreadLocal<>();

    /**
     * Return a helper object to optimize {@link Context} access.
     * <p>
     * The runtime will pass the resulting helper object to the subsequent
     * calls to {@link #getContext(Object contextHelper)} and
     * {@link #setContext(Object contextHelper, Context cx)} methods.
     * In this way the implementation can use the helper to cache
     * information about current thread to make {@link Context} access faster.
     */
    public static Object getThreadContextHelper(){
        // To make subsequent batch calls to getContext/setContext faster
        // associate permanently one element array with contextLocal
        // so getContext/setContext would need just to read/write the first
        // array element.
        // Note that it is necessary to use Object[], not Context[] to allow
        // garbage collection of Rhino classes. For details see comments
        // by Attila Szegedi in
        // https://bugzilla.mozilla.org/show_bug.cgi?id=281067#c5

        Object[] storage = contextLocal.get();
        if(storage == null){
            storage = new Object[1];
            contextLocal.set(storage);
        }
        return storage;
    }

    /**
     * Get {@link Context} instance associated with the current thread
     * or null if none.
     * @param contextHelper The result of {@link #getThreadContextHelper()}
     * called from the current thread.
     */
    public static Context getContext(Object contextHelper){
        Object[] storage = (Object[])contextHelper;
        return (Context)storage[0];
    }

    /**
     * Associate {@link Context} instance with the current thread or remove
     * the current association if <tt>cx</tt> is null.
     * @param contextHelper The result of {@link #getThreadContextHelper()}
     * called from the current thread.
     */
    public static void setContext(Object contextHelper, Context cx){
        Object[] storage = (Object[])contextHelper;
        storage[0] = cx;
    }

    /**
     * In many JVMSs, public methods in private
     * classes are not accessible by default (Sun Bug #4071593).
     * VMBridge instance should try to workaround that via, for example,
     * calling method.setAccessible(true) when it is available.
     * @return true if it was possible to make method accessible
     * or false otherwise.
     */
    public static boolean tryToMakeAccessible(AccessibleObject accessible){
        if(accessible.isAccessible()){
            return true;
        }
        try{
            accessible.setAccessible(true);
        }catch(Exception ex){
        }

        return accessible.isAccessible();
    }

    /**
     * Create helper object to create later proxies implementing the specified
     * interfaces later. Under JDK 1.3 the implementation can look like:
     * <pre>
     * return java.lang.reflect.Proxy.getProxyClass(..., interfaces).
     *     getConstructor(new Class[] {
     *         java.lang.reflect.InvocationHandler.class });
     * </pre>
     * @param interfaces Array with one or more interface class objects.
     */
    public static Object getInterfaceProxyHelper(ContextFactory cf,
                                             Class<?>[] interfaces){
        // XXX: How to handle interfaces array withclasses from different
        // class loaders? Using cf.getApplicationClassLoader() ?
        ClassLoader loader = interfaces[0].getClassLoader();
        Class<?> cl = Proxy.getProxyClass(loader, interfaces);
        Constructor<?> c;
        try{
            c = cl.getConstructor(InvocationHandler.class);
        }catch(NoSuchMethodException ex){
            // Should not happen
            throw new IllegalStateException(ex);
        }
        return c;
    }

    /**
     * Create proxy object for {@link InterfaceAdapter}. The proxy should call
     * {@link InterfaceAdapter#invoke(ContextFactory, Object, Scriptable,
     * Object, Method, Object[])}
     * as implementation of interface methods associated with
     * <tt>proxyHelper</tt>. {@link Method}
     * @param proxyHelper The result of the previous call to
     * {@link #getInterfaceProxyHelper(ContextFactory, Class[])}.
     */
    public static Object newInterfaceProxy(Object proxyHelper,
                                       final ContextFactory cf,
                                       final InterfaceAdapter adapter,
                                       final Object target,
                                       final Scriptable topScope){
        Constructor<?> c = (Constructor<?>)proxyHelper;

        InvocationHandler handler = (proxy, method, args) -> {
            // In addition to methods declared in the interface, proxies
            // also route some java.lang.Object methods through the
            // invocation handler.
            if(method.getDeclaringClass() == Object.class){
                String methodName = method.getName();
                if(methodName.equals("equals")){
                    Object other = args[0];
                    // Note: we could compare a proxy and its wrapped function
                    // as equal here but that would break symmetry of equal().
                    // The reason == suffices here is that proxies are cached
                    // in ScriptableObject (see NativeJavaObject.coerceType())
                    return proxy == other;
                }
                if(methodName.equals("hashCode")){
                    return target.hashCode();
                }
                if(methodName.equals("toString")){
                    return "Proxy[" + target.toString() + "]";
                }
            }
            return adapter.invoke(cf, target, topScope, proxy, method, args);
        };
        Object proxy;
        try{
            proxy = c.newInstance(handler);
        }catch(InvocationTargetException ex){
            throw Context.throwAsScriptRuntimeEx(ex);
        }catch(IllegalAccessException | InstantiationException ex){
            // Should not happen
            throw new IllegalStateException(ex);
        }
        return proxy;
    }
}
