package rhino;

import java.lang.reflect.*;
import java.util.*;

/**
 * An adapter class made by {@link JavaAdapter#adapterFactory}, for one superclass, set of interfaces and set of function names.
 * It behaves like a generated adapter: overrides call the JavaScript functions, and "this" has a super$ method for each override.
 */
final class FactoryAdapter{
    private static final Map<JavaAdapter.JavaAdapterSignature, FactoryAdapter> adapters = new HashMap<>();
    private static int lastId;

    final AdapterFactory factory;
    final Class<?> type;
    final Method[] overrides;
    final Class<?>[][] parameterTypes;
    /** Bits of the arguments of each override that are wrapped for JavaScript, like JavaAdapter.callMethod() takes. */
    final long[] wrappedArgs;
    final NativeJavaMethod constructors;
    final Map<String, SuperMethod> supers = new HashMap<>();

    private FactoryAdapter(AdapterFactory factory, Scriptable scope, Class<?> superClass, Class<?>[] interfaces, ObjToIntMap functionNames){
        this.factory = factory;
        ArrayList<JavaAdapter.AdapterMethod> methods = JavaAdapter.getOverrides(functionNames, superClass, interfaces);
        overrides = new Method[methods.size()];
        parameterTypes = new Class<?>[methods.size()][];
        wrappedArgs = new long[methods.size()];
        Map<String, ArrayList<MemberBox>> superMethods = new HashMap<>();

        for(int i = 0; i < overrides.length; i++){
            Method method = methods.get(i).method;
            overrides[i] = method;
            parameterTypes[i] = method.getParameterTypes();
            if(parameterTypes[i].length > 64){
                throw Context.reportRuntimeError0("JavaAdapter can not subclass methods with more then 64 arguments.");
            }
            for(int a = 0; a < parameterTypes[i].length; a++){
                if(!parameterTypes[i][a].isPrimitive()) wrappedArgs[i] |= 1L << a;
            }
            if(methods.get(i).hasSuper){
                String name = "super$" + method.getName();
                ArrayList<MemberBox> overloads = superMethods.get(name);
                if(overloads == null) superMethods.put(name, overloads = new ArrayList<>());
                overloads.add(new MemberBox(method));
            }
        }
        for(Map.Entry<String, ArrayList<MemberBox>> entry : superMethods.entrySet()){
            SuperMethod method = new SuperMethod(entry.getValue().toArray(new MemberBox[0]), entry.getKey());
            ScriptRuntime.setFunctionProtoAndParent(method, scope);
            supers.put(entry.getKey(), method);
        }

        ArrayList<MemberBox> ctors = new ArrayList<>();
        for(Constructor<?> ctor : superClass.getDeclaredConstructors()){
            int mods = ctor.getModifiers();
            if(Modifier.isPublic(mods) || Modifier.isProtected(mods)){
                ctors.add(new MemberBox(ctor));
            }
        }
        constructors = new NativeJavaMethod(ctors.toArray(new MemberBox[0]), superClass.getSimpleName());

        type = factory.createClass(superClass.getName() + "$JavaAdapter" + (++lastId), superClass, interfaces, overrides);
    }

    static synchronized FactoryAdapter get(AdapterFactory factory, Scriptable scope, Class<?> superClass, Class<?>[] interfaces, Scriptable obj){
        ObjToIntMap names = JavaAdapter.getObjectFunctionNames(obj);
        JavaAdapter.JavaAdapterSignature signature = new JavaAdapter.JavaAdapterSignature(superClass, interfaces, names);
        FactoryAdapter adapter = adapters.get(signature);
        if(adapter == null){
            adapters.put(signature, adapter = new FactoryAdapter(factory, scope, superClass, interfaces, names));
        }
        return adapter;
    }

    /** @return the JavaScript "this" of a new instance */
    Scriptable create(Context cx, Scriptable delegee, Object[] ctorArgs){
        int index = constructors.findCachedFunction(cx, ctorArgs);
        if(index < 0){
            throw Context.reportRuntimeError2("msg.no.java.ctor", type.getSuperclass().getName(), NativeJavaMethod.scriptSignature(ctorArgs));
        }
        MemberBox ctor = constructors.methods[index];
        Instance instance = new Instance(delegee, cx.getFactory());
        return instance.self(factory.newInstance(type, ctor.ctor(), NativeJavaClass.convertArgs(ctorArgs, ctor), instance));
    }

    /** Converts arguments as generated adapters do: numbers to Double and chars to String. */
    static Object wrapArg(Object arg, Class<?> type){
        if(!type.isPrimitive() || type == Boolean.TYPE) return arg;
        if(type == Character.TYPE) return String.valueOf((char)(Character)arg);
        return ((Number)arg).doubleValue();
    }

    /** Converts the result of a JavaScript function as generated adapters do. */
    static Object convertResult(Object result, Class<?> type){
        if(type == Void.TYPE) return null;
        if(type == Boolean.TYPE) return Context.toBoolean(result);
        if(type == Character.TYPE) return Context.toString(result).charAt(0);
        if(!type.isPrimitive()) return JavaAdapter.convertResult(result, type);

        double value = Context.toNumber(result);
        if(type == Integer.TYPE) return (int)value;
        if(type == Long.TYPE) return (long)value;
        if(type == Float.TYPE) return (float)value;
        if(type == Short.TYPE) return (short)(int)value;
        if(type == Byte.TYPE) return (byte)(int)value;
        return value;
    }

    final class Instance implements AdapterFactory.Handler{
        final Scriptable delegee;
        final ContextFactory contextFactory;
        Scriptable self;

        Instance(Scriptable delegee, ContextFactory contextFactory){
            this.delegee = delegee;
            this.contextFactory = contextFactory;
        }

        //created on first use, since the superclass constructor may call overrides
        Scriptable self(Object adapter){
            if(self == null){
                Wrapper wrapper = new Wrapper(ScriptableObject.getTopLevelScope(delegee), adapter);
                wrapper.setPrototype(delegee);
                self = wrapper;
            }
            return self;
        }

        @Override
        public Object invoke(Object adapter, int index, Object[] args){
            Method method = overrides[index];
            Class<?>[] types = parameterTypes[index];
            for(int i = 0; i < args.length; i++){
                args[i] = wrapArg(args[i], types[i]);
            }
            Object result = JavaAdapter.callMethod(contextFactory, self(adapter), JavaAdapter.getFunction(delegee, method.getName()), args, wrappedArgs[index]);
            return convertResult(result, method.getReturnType());
        }
    }

    /** The JavaScript "this" of an instance, which adds the super$ methods. */
    final class Wrapper extends NativeJavaObject{
        Wrapper(Scriptable scope, Object adapter){
            super(scope, adapter, null, true);
        }

        @Override
        public boolean has(String name, Scriptable start){
            return supers.containsKey(name) || super.has(name, start);
        }

        @Override
        public Object get(String name, Scriptable start){
            SuperMethod method = supers.get(name);
            return method != null ? method : super.get(name, start);
        }
    }

    final class SuperMethod extends NativeJavaMethod{
        SuperMethod(MemberBox[] methods, String name){
            super(methods, name);
        }

        @Override
        Object invoke(MemberBox method, Object javaObject, Object[] args){
            return factory.invokeSuper(javaObject, method.method(), args);
        }
    }
}
