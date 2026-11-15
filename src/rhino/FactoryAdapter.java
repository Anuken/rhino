/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rhino.lc.member.ExecutableBox;
import rhino.lc.type.TypeInfo;
import rhino.lc.type.TypeInfoFactory;

/**
 * An adapter class made by {@link JavaAdapter#adapterFactory} for one superclass, set of
 * interfaces and set of function names. It behaves like a generated adapter: overrides call the
 * JavaScript functions, and "this" has a super$ method for each override of a superclass method.
 */
final class FactoryAdapter {
    private static final Map<JavaAdapter.JavaAdapterSignature, FactoryAdapter> adapters =
            new HashMap<>();
    private static int lastId;

    final AdapterFactory factory;
    final Class<?> type;
    final Method[] overrides;
    final Class<?>[][] parameterTypes;

    /** Bits of the arguments of each override that are wrapped, as JavaAdapter.callMethod takes. */
    final long[] wrappedArgs;

    final ExecutableBox[] constructors;

    /** The super$ methods by name, as the overloads they choose from. */
    final Map<String, ExecutableBox[]> supers = new HashMap<>();

    private FactoryAdapter(
            AdapterFactory factory, VarScope scope, JavaAdapter.JavaAdapterSignature sig) {
        this.factory = factory;
        TypeInfoFactory typeFactory = TypeInfoFactory.get(scope);
        List<JavaAdapter.AdapterMethod> methods =
                JavaAdapter.getOverrides(sig.names, sig.superClass, sig.interfaces);
        overrides = new Method[methods.size()];
        parameterTypes = new Class<?>[methods.size()][];
        wrappedArgs = new long[methods.size()];
        Map<String, List<ExecutableBox>> superMethods = new HashMap<>();

        for (int i = 0; i < overrides.length; i++) {
            Method method = methods.get(i).method;
            overrides[i] = method;
            parameterTypes[i] = method.getParameterTypes();
            if (parameterTypes[i].length > 64) {
                throw Context.reportRuntimeError(
                        "JavaAdapter can not subclass methods with more than 64 arguments.");
            }
            for (int a = 0; a < parameterTypes[i].length; a++) {
                if (!parameterTypes[i][a].isPrimitive()) wrappedArgs[i] |= 1L << a;
            }
            if (methods.get(i).hasSuper) {
                String name = "super$" + method.getName();
                List<ExecutableBox> overloads = superMethods.get(name);
                if (overloads == null) {
                    overloads = new ArrayList<>();
                    superMethods.put(name, overloads);
                }
                overloads.add(new ExecutableBox(method, typeFactory, sig.superClass));
            }
        }
        for (Map.Entry<String, List<ExecutableBox>> entry : superMethods.entrySet()) {
            supers.put(entry.getKey(), entry.getValue().toArray(new ExecutableBox[0]));
        }

        List<ExecutableBox> ctors = new ArrayList<>();
        for (Constructor<?> ctor : sig.superClass.getDeclaredConstructors()) {
            int mods = ctor.getModifiers();
            if (Modifier.isPublic(mods) || Modifier.isProtected(mods)) {
                ctors.add(new ExecutableBox(ctor, typeFactory));
            }
        }
        constructors = ctors.toArray(new ExecutableBox[0]);

        type =
                factory.createClass(
                        sig.superClass.getName() + "$JavaAdapter" + (++lastId),
                        sig.superClass,
                        sig.interfaces,
                        overrides);
    }

    static synchronized FactoryAdapter get(
            AdapterFactory factory, VarScope scope, JavaAdapter.JavaAdapterSignature sig) {
        FactoryAdapter adapter = adapters.get(sig);
        if (adapter == null || adapter.factory != factory) {
            adapter = new FactoryAdapter(factory, scope, sig);
            adapters.put(sig, adapter);
        }
        return adapter;
    }

    /** @return the JavaScript "this" of a new instance */
    Scriptable create(Context cx, VarScope scope, Scriptable delegee, Object[] ctorArgs) {
        NativeJavaMethod ctors =
                new NativeJavaMethod(scope, constructors, type.getSuperclass().getSimpleName());
        int index = ctors.findCachedFunction(cx, ctorArgs);
        if (index < 0) {
            throw Context.reportRuntimeErrorById(
                    "msg.no.java.ctor",
                    type.getSuperclass().getName(),
                    NativeJavaMethod.scriptSignature(ctorArgs));
        }
        ExecutableBox ctor = constructors[index];
        Instance instance = new Instance(delegee, cx.getFactory());
        Object[] args = ctor.wrapArgsInternal(ctorArgs, Collections.emptyMap());
        return instance.self(factory.newInstance(type, ctor.asConstructor(), args, instance));
    }

    /** Converts arguments as generated adapters do: numbers to Double and chars to String. */
    static Object wrapArg(Object arg, Class<?> type) {
        if (!type.isPrimitive() || type == Boolean.TYPE) return arg;
        if (type == Character.TYPE) return String.valueOf(((Character) arg).charValue());
        return ((Number) arg).doubleValue();
    }

    /** Converts the result of a JavaScript function as generated adapters do. */
    static Object convertResult(Object result, Class<?> type) {
        if (type == Void.TYPE) return null;
        if (type == Boolean.TYPE) return Context.toBoolean(result);
        if (type == Character.TYPE) return Context.toString(result).charAt(0);
        if (!type.isPrimitive()) return JavaAdapter.convertResult(result, type);

        double value = Context.toNumber(result);
        if (type == Integer.TYPE) return (int) value;
        if (type == Long.TYPE) return (long) value;
        if (type == Float.TYPE) return (float) value;
        if (type == Short.TYPE) return (short) (int) value;
        if (type == Byte.TYPE) return (byte) (int) value;
        return value;
    }

    final class Instance implements AdapterFactory.Handler {
        final Scriptable delegee;
        final ContextFactory contextFactory;
        Scriptable self;

        Instance(Scriptable delegee, ContextFactory contextFactory) {
            this.delegee = delegee;
            this.contextFactory = contextFactory;
        }

        // created on first use, since the superclass constructor may call overrides
        Scriptable self(Object adapter) {
            if (self == null) {
                VarScope scope = ScriptableObject.getTopLevelScope(delegee.getParentScope());
                Wrapper wrapper = new Wrapper(scope, adapter);
                wrapper.setPrototype(delegee);
                self = wrapper;
            }
            return self;
        }

        @Override
        public Object invoke(Object adapter, int index, Object[] args) {
            Method method = overrides[index];
            Class<?>[] types = parameterTypes[index];
            for (int i = 0; i < args.length; i++) {
                args[i] = wrapArg(args[i], types[i]);
            }
            Object result =
                    JavaAdapter.callMethod(
                            contextFactory,
                            self(adapter),
                            JavaAdapter.getFunction(delegee, method.getName()),
                            args,
                            wrappedArgs[index]);
            return convertResult(result, method.getReturnType());
        }
    }

    /** The JavaScript "this" of an instance, which adds the super$ methods. */
    final class Wrapper extends NativeJavaObject {
        private Map<String, SuperMethod> superMethods;

        Wrapper(VarScope scope, Object adapter) {
            super(scope, adapter, TypeInfo.NONE, true);
        }

        @Override
        public boolean has(String name, Scriptable start) {
            return supers.containsKey(name) || super.has(name, start);
        }

        @Override
        public Object get(String name, Scriptable start) {
            ExecutableBox[] overloads = supers.get(name);
            if (overloads == null) {
                return super.get(name, start);
            }
            if (superMethods == null) {
                superMethods = new HashMap<>();
            }
            SuperMethod method = superMethods.get(name);
            if (method == null) {
                method = new SuperMethod(parent, overloads, name);
                ScriptRuntime.setFunctionProtoAndParent(
                        method, Context.getCurrentContext(), parent, false);
                superMethods.put(name, method);
            }
            return method;
        }
    }

    final class SuperMethod extends NativeJavaMethod {
        SuperMethod(VarScope scope, ExecutableBox[] methods, String name) {
            super(scope, methods, name);
        }

        @Override
        Object invoke(ExecutableBox method, Object javaObject, Object[] args) {
            return factory.invokeSuper(javaObject, method.asMethod(), args);
        }
    }
}
