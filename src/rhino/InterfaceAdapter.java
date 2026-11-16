/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import rhino.lc.type.TypeInfo;

/**
 * Adapter to use JS function as implementation of Java interfaces with single method or multiple
 * methods with the same signature.
 */
public class InterfaceAdapter {
    private final Object proxyHelper;

    /**
     * Make glue object implementing interface cl that will call the supplied JS function when
     * called. Only interfaces were all methods have the same signature is supported.
     *
     * @return The glue object or null if {@code cl} is not interface or has methods with different
     *     signatures.
     */
    static Object create(Context cx, Class<?> cl, ScriptableObject object) {
        if (!cl.isInterface()) throw new IllegalArgumentException();

        VarScope topScope = ScriptRuntime.getTopCallScope(cx);
        ClassCache cache = ClassCache.get(topScope);
        InterfaceAdapter adapter;
        adapter = (InterfaceAdapter) cache.getInterfaceAdapter(cl);
        ContextFactory cf = cx.getFactory();
        if (adapter == null) {
            if (object instanceof Callable) {
                Method[] methods = cl.getMethods();
                // Check if interface can be implemented by a single function.
                // We allow this if the interface has only one method or multiple
                // methods with the same name (in which case they'd result in
                // the same function to be invoked anyway).
                HashSet<String> functionalMethodNames = new HashSet<>();
                HashSet<String> defaultMethodNames = new HashSet<>();
                for (Method method : methods) {
                    // there are multiple methods in the interface we inspect
                    // only abstract ones, they must all have the same name.
                    if (isFunctionalMethodCandidate(method)) {
                        functionalMethodNames.add(method.getName());
                        if (functionalMethodNames.size() > 1) {
                            break;
                        }
                    } else {
                        defaultMethodNames.add(method.getName());
                    }
                }

                boolean canConvert =
                        (functionalMethodNames.size() == 1)
                                || (functionalMethodNames.isEmpty()
                                        && defaultMethodNames.size() == 1);
                // There is no abstract method or there are multiple methods.
                if (!canConvert) {
                    if (functionalMethodNames.isEmpty() && defaultMethodNames.isEmpty()) {
                        throw Context.reportRuntimeErrorById(
                                "msg.no.empty.interface.conversion", cl.getName());
                    } else {
                        throw Context.reportRuntimeErrorById(
                                "msg.no.function.interface.conversion", cl.getName());
                    }
                }
            }
            adapter = new InterfaceAdapter(cf, cl);
            cache.cacheInterfaceAdapter(cl, adapter);
        }
        return newInterfaceProxy(adapter.proxyHelper, cf, adapter, object, topScope);
    }

    private static Object newInterfaceProxy(
            Object proxyHelper,
            final ContextFactory cf,
            final InterfaceAdapter adapter,
            final Object target,
            VarScope topScope) {
        Constructor<?> c = (Constructor<?>) proxyHelper;

        InvocationHandler handler = new Handler(cf, adapter, target, topScope);
        Object proxy;
        try {
            proxy = c.newInstance(handler);
        } catch (InvocationTargetException ex) {
            throw Context.throwAsScriptRuntimeEx(ex);
        } catch (IllegalAccessException | InstantiationException ex) {
            // Should not happen
            throw new IllegalStateException(ex);
        }
        return proxy;
    }

    /**
     * We have to ignore java8 default methods and methods like 'equals', 'hashCode' and 'toString'
     * as it occurs for example in the Comparator interface.
     *
     * @return true, if the function
     */
    static boolean isFunctionalMethodCandidate(Method method) {
        if ("equals".equals(method.getName())
                || "hashCode".equals(method.getName())
                || "toString".equals(method.getName())) {
            // it should be safe to ignore them as there is also a special
            // case for these methods in VMBridge_jdk18.newInterfaceProxy
            return false;
        } else {
            return Modifier.isAbstract(method.getModifiers());
        }
    }

    private InterfaceAdapter(ContextFactory cf, Class<?> cl) {
        this.proxyHelper = getInterfaceProxyHelper(cf, new Class[] {cl});
    }

    @SuppressWarnings("deprecation")
    private static Object getInterfaceProxyHelper(ContextFactory cf, Class<?>[] interfaces) {
        // XXX: How to handle interfaces array withclasses from different
        // class loaders? Using cf.getApplicationClassLoader() ?
        ClassLoader loader = interfaces[0].getClassLoader();
        Class<?> cl = Proxy.getProxyClass(loader, interfaces);
        Constructor<?> c;
        try {
            c = cl.getConstructor(InvocationHandler.class);
        } catch (NoSuchMethodException ex) {
            // Should not happen
            throw new IllegalStateException(ex);
        }
        return c;
    }

    public Object invoke(
            ContextFactory cf,
            final Object target,
            final VarScope topScope,
            final Object thisObject,
            final Method method,
            final Object[] args) {
        return cf.call(cx -> invokeImpl(cx, target, topScope, thisObject, method, args, null));
    }

    private static final class Handler implements InvocationHandler {
        private final ContextFactory cf;
        private final InterfaceAdapter adapter;
        private final Object target;
        private final VarScope topScope;
        private volatile WrappedProxy wrappedProxy;

        Handler(ContextFactory cf, InterfaceAdapter adapter, Object target, VarScope topScope) {
            this.cf = cf;
            this.adapter = adapter;
            this.target = target;
            this.topScope = topScope;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            // In addition to methods declared in the interface, proxies
            // also route some java.lang.Object methods through the
            // invocation handler.
            if (method.getDeclaringClass() == Object.class) {
                String methodName = method.getName();
                if ("equals".equals(methodName)) {
                    Object other = args[0];
                    // Note: we could compare a proxy and its wrapped function
                    // as equal here but that would break symmetry of equal().
                    // The reason == suffices here is that proxies are cached
                    // in ScriptableObject (see NativeJavaObject.coerceType())
                    return Boolean.valueOf(proxy == other);
                }
                if ("hashCode".equals(methodName)) {
                    return Integer.valueOf(target.hashCode());
                }
                if ("toString".equals(methodName)) {
                    return "Proxy[" + target.toString() + "]";
                }
            }
            // entering a context is only needed on threads that have none
            Context cx = Context.getCurrentContext();
            if (cx != null) {
                return adapter.invokeImpl(cx, target, topScope, proxy, method, args, this);
            }
            return cf.call(c -> adapter.invokeImpl(c, target, topScope, proxy, method, args, this));
        }

        /** Returns the proxy wrapped by the current wrap factory, reusing the last wrapper. */
        Scriptable wrapProxy(Context cx, Object proxy) {
            WrapFactory wf = cx.getWrapFactory();
            WrappedProxy wrapped = wrappedProxy;
            if (wrapped == null || wrapped.factory != wf) {
                wrapped =
                        new WrappedProxy(
                                wf, wf.wrapAsJavaObject(cx, topScope, proxy, TypeInfo.NONE));
                wrappedProxy = wrapped;
            }
            return wrapped.wrapper;
        }
    }

    private static final class WrappedProxy {
        final WrapFactory factory;
        final Scriptable wrapper;

        WrappedProxy(WrapFactory factory, Scriptable wrapper) {
            this.factory = factory;
            this.wrapper = wrapper;
        }
    }

    /** @return what a missing method returns: null, or zero/false for primitive types */
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == Void.TYPE) return null;
        if (type == Boolean.TYPE) return Boolean.FALSE;
        if (type == Character.TYPE) return '\0';
        if (type == Byte.TYPE) return (byte) 0;
        if (type == Short.TYPE) return (short) 0;
        if (type == Integer.TYPE) return 0;
        if (type == Long.TYPE) return 0L;
        if (type == Float.TYPE) return 0f;
        return 0d;
    }

    Object invokeImpl(
            Context cx,
            Object target,
            VarScope topScope,
            Object thisObject,
            Method method,
            Object[] args,
            Handler handler) {
        Callable function;
        if (target instanceof Callable) {
            function = (Callable) target;
        } else {
            Scriptable s = (Scriptable) target;
            String methodName = method.getName();
            Object value = ScriptableObject.getProperty(s, methodName);
            if (value == Scriptable.NOT_FOUND) {
                // We really should throw an error here, but for the sake of
                // compatibility with JavaAdapter we silently ignore undefined
                // methods.
                Context.reportWarning(
                        ScriptRuntime.getMessageById(
                                "msg.undefined.function.interface", methodName));
                return defaultValue(method.getReturnType());
            }
            if (!(value instanceof Callable)) {
                throw Context.reportRuntimeErrorById("msg.not.function.interface", methodName);
            }
            function = (Callable) value;
        }
        WrapFactory wf = cx.getWrapFactory();
        if (args == null) {
            args = ScriptRuntime.emptyArgs;
        } else {
            for (int i = 0, N = args.length; i != N; ++i) {
                Object arg = args[i];
                // neutralize wrap factory java primitive wrap feature
                if (!(arg instanceof String || arg instanceof Number || arg instanceof Boolean)) {
                    args[i] = wf.wrap(cx, topScope, arg, TypeInfo.NONE);
                }
            }
        }
        // A method of an object implementing the interface sees that object as this, so that
        // objects such as class instances can use their own state
        Scriptable thisObj;
        if (!(target instanceof Callable)) {
            thisObj = (Scriptable) target;
        } else if (target instanceof JSFunction
                && ((JSFunction) target).getDescriptor().hasLexicalThis()) {
            // arrow functions ignore it, so the proxy needn't be wrapped
            thisObj = Undefined.SCRIPTABLE_UNDEFINED;
        } else if (handler != null) {
            thisObj = handler.wrapProxy(cx, thisObject);
        } else {
            thisObj = wf.wrapAsJavaObject(cx, topScope, thisObject, TypeInfo.NONE);
        }

        Object result = function.call(cx, topScope, thisObj, args);
        Class<?> javaResultType = method.getReturnType();
        if (javaResultType == Void.TYPE) {
            result = null;
        } else {
            result = Context.jsToJava(result, javaResultType);
        }
        return result;
    }
}
