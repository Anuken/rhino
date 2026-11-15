package rhino.lc.member;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import rhino.Context;
import rhino.ContinuationPending;
import rhino.Delegator;
import rhino.NativeArray;
import rhino.NativeJavaArray;
import rhino.lc.ReflectUtils;
import rhino.lc.type.TypeInfo;
import rhino.lc.type.TypeInfoFactory;
import rhino.lc.type.VariableTypeInfo;

/**
 * @author ZZZank
 */
public final class ExecutableBox {

    /**
     * Must be either {@link Method} or {@link Constructor}.
     *
     * <p>Not using {@link java.lang.reflect.Executable} for Android compatibility
     */
    private final Member member;

    private final List<TypeInfo> argTypes;
    private final TypeInfo[] argTypeArray;
    private final TypeInfo returnType;
    private final boolean varArgs;
    private final boolean isStatic;

    private static boolean loggedGenericFailure;

    static void genericFailed(Member member, Throwable e) {
        if (!loggedGenericFailure) {
            loggedGenericFailure = true;
            System.err.println("Rhino: generic types of " + member + " could not be resolved; using erased types.");
            e.printStackTrace();
        }
    }

    public ExecutableBox(Method method, TypeInfoFactory factory, Class<?> parent) {
        this.member = method;
        this.varArgs = method.isVarArgs();

        List<TypeInfo> argTypes;
        TypeInfo returnType;
        try {
            argTypes = factory.createList(method.getGenericParameterTypes());
            returnType = factory.create(method.getGenericReturnType());
            var mapping = factory.getConsolidationMapping(parent);
            if (!mapping.isEmpty()) {
                argTypes = TypeInfoFactory.consolidateAll(argTypes, mapping);
                returnType = returnType.consolidate(mapping);
            }
        } catch (RuntimeException | LinkageError | AssertionError e) {
            // generic reflection is unreliable on some runtimes (RoboVM); erased types still work
            genericFailed(method, e);
            argTypes = factory.createList(method.getParameterTypes());
            returnType = factory.create(method.getReturnType());
        }
        this.argTypes = argTypes;
        this.returnType = returnType;
        this.argTypeArray = this.argTypes.toArray(TypeInfoFactory.EMPTY_ARRAY);
        this.isStatic = Modifier.isStatic(method.getModifiers());
        ReflectUtils.skipAccessCheck(method, method);
    }

    public ExecutableBox(Constructor<?> constructor, TypeInfoFactory factory) {
        this.member = constructor;
        this.varArgs = constructor.isVarArgs();
        List<TypeInfo> argTypes;
        try {
            argTypes = factory.createList(constructor.getGenericParameterTypes());
        } catch (RuntimeException | LinkageError | AssertionError e) {
            genericFailed(constructor, e);
            argTypes = factory.createList(constructor.getParameterTypes());
        }
        this.argTypes = argTypes;
        this.argTypeArray = this.argTypes.toArray(TypeInfoFactory.EMPTY_ARRAY);
        this.returnType = TypeInfo.NONE;
        this.isStatic = false;
        ReflectUtils.skipAccessCheck(constructor, constructor);

        // Type consolidation not required for constructor.
        //
        // consider this example:
        // class A<T> {
        //     A(T value) { ... }
        // }
        // class B extends A<String> {
        //     B(String value) { super(value); }
        // }
        // for class B, the constructor must have "String" instead of "T" as parameter type,
        // otherwise it won't compile. So param types are already concrete types.
    }

    public Method asMethod() {
        return (Method) member;
    }

    public Constructor<?> asConstructor() {
        return (Constructor<?>) member;
    }

    public Member member() {
        return member;
    }

    public boolean isMethod() {
        return member instanceof Method;
    }

    public boolean isConstructor() {
        return member instanceof Constructor;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isPublic() {
        return Modifier.isPublic(member.getModifiers());
    }

    public boolean isVarArgs() {
        return varArgs;
    }

    public String getName() {
        return member.getName();
    }

    public Class<?> getDeclaringClass() {
        return member.getDeclaringClass();
    }

    public List<TypeInfo> getArgTypes() {
        return argTypes;
    }

    /** The same as {@link #getArgTypes()}, as an array that must not be modified. */
    public TypeInfo[] getArgTypeArray() {
        return argTypeArray;
    }

    public TypeInfo getReturnType() {
        return returnType;
    }

    public String toJavaDeclaration() {
        StringBuilder sb = new StringBuilder();
        if (isMethod()) {
            Method method = asMethod();
            sb.append(method.getReturnType());
            sb.append(' ');
            sb.append(method.getName());
        } else {
            Constructor<?> ctor = asConstructor();
            String name = ctor.getDeclaringClass().getName();
            int lastDot = name.lastIndexOf('.');
            if (lastDot >= 0) {
                name = name.substring(lastDot + 1);
            }
            sb.append(name);
        }
        sb.append(ReflectUtils.liveConnectSignature(getArgTypes()));
        return sb.toString();
    }

    @Override
    public String toString() {
        return member.toString();
    }

    public Object invoke(Object target, Object[] args) {
        Method method = asMethod();

        // handle delegators
        if (target instanceof Delegator) {
            target = ((Delegator) target).getDelegee();
        }
        if (args != null) {
            for (int i = 0; i < args.length; ++i) {
                if (args[i] instanceof Delegator) {
                    args[i] = ((Delegator) args[i]).getDelegee();
                }
            }
        }

        try {
            try {
                return method.invoke(target, args);
            } catch (IllegalAccessException ex) {
                // Unlike MemberBox, ExecutableBox will NOT try to search an accessible method after
                // failure. Instead, JavaMembers should be responsible for ensuring methods visible
                // to JS are accessible.
                if (!tryToMakeAccessible(method)) {
                    throw Context.throwAsScriptRuntimeEx(ex);
                }
                // Retry after recovery
                return method.invoke(target, args);
            }
        } catch (InvocationTargetException ite) {
            // Must allow ContinuationPending exceptions to propagate unhindered
            Throwable e = ite;
            do {
                e = ((InvocationTargetException) e).getTargetException();
            } while ((e instanceof InvocationTargetException));
            if (e instanceof ContinuationPending) throw (ContinuationPending) e;
            throw Context.throwAsScriptRuntimeEx(e);
        } catch (Exception ex) {
            throw Context.throwAsScriptRuntimeEx(ex);
        }
    }

    public Object newInstance(Object[] args) {
        Constructor<?> ctor = asConstructor();
        try {
            try {
                return ctor.newInstance(args);
            } catch (IllegalAccessException ex) {
                if (!tryToMakeAccessible(ctor)) {
                    throw Context.throwAsScriptRuntimeEx(ex);
                }
                return ctor.newInstance(args);
            }
        } catch (Exception ex) {
            throw Context.throwAsScriptRuntimeEx(ex);
        }
    }

    public Object[] wrapArgsInternal(Object[] args, Map<VariableTypeInfo, TypeInfo> mapping) {
        var argTypes = argTypeArray;
        var argTypesLen = argTypes.length;
        var argLen = args.length;
        final var shouldConsolidate = !mapping.isEmpty();

        if (!this.varArgs) {
            // fast path for getter
            if (argLen == 0) {
                return args;
            }

            var wrappedArgs = args;
            for (int i = 0; i < argLen; i++) {
                var arg = args[i];
                var argType = argTypes[i];
                if (shouldConsolidate) {
                    argType = argType.consolidate(mapping);
                }

                var coerced = Context.jsToJava(arg, argType);
                if (coerced != arg) {
                    if (wrappedArgs == args) {
                        wrappedArgs = args.clone();
                    }
                    wrappedArgs[i] = coerced;
                }
            }
            return wrappedArgs;
        }

        // marshall the explicit parameters
        var wrappedArgs = new Object[argTypesLen];
        for (int i = 0; i < argTypesLen - 1; i++) {
            var argType = argTypes[i];
            if (shouldConsolidate) {
                argType = argType.consolidate(mapping);
            }
            wrappedArgs[i] = Context.jsToJava(args[i], argType);
        }

        // Handle special situation where a single variable parameter
        // is given, and it is a Java or ECMA array or is null.
        if (argLen == argTypesLen) {
            var lastArg = args[argLen - 1];
            var lastArgType = argTypes[argTypesLen - 1];
            if (shouldConsolidate) {
                lastArgType = lastArgType.consolidate(mapping);
            }
            if (lastArg == null
                    || lastArg instanceof NativeArray
                    || lastArg instanceof NativeJavaArray) {
                // convert the ECMA array into a native array
                wrappedArgs[argLen - 1] = Context.jsToJava(lastArg, lastArgType);
                return wrappedArgs;
            }
        }

        // marshall the variable parameters
        var lastArgType = argTypes[argTypesLen - 1].getComponentType();
        if (shouldConsolidate) {
            lastArgType = lastArgType.consolidate(mapping);
        }
        var varArgs = lastArgType.newArray(argLen - argTypesLen + 1);
        for (int i = 0, arrayLen = Array.getLength(varArgs); i < arrayLen; i++) {
            Array.set(varArgs, i, Context.jsToJava(args[argTypesLen - 1 + i], lastArgType));
        }
        wrappedArgs[argTypesLen - 1] = varArgs;

        return wrappedArgs;
    }

    @SuppressWarnings("deprecation")
    private static boolean tryToMakeAccessible(AccessibleObject accessible) {
        if (!accessible.isAccessible()) {
            accessible.setAccessible(true);
        }
        return true;
    }
}
