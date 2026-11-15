package rhino.tests.mindustry;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import rhino.AdapterFactory;
import rhino.classfile.ByteCode;
import rhino.classfile.ClassFileWriter;

/**
 * A desktop stand-in for Mindustry's iOS Subclasser: it generates plain subclasses whose overrides
 * call {@link #dispatch}, which forwards to the {@link AdapterFactory.Handler} of the instance
 * (also during the superclass constructor, like Subclasser), and super$i methods for {@link
 * #invokeSuper}. No Rhino adapter machinery is involved in the generated classes.
 */
public class SubclassingAdapterFactory implements AdapterFactory {
    private static final ThreadLocal<Handler> constructing = new ThreadLocal<>();
    private static final Map<Object, Handler> handlers =
            Collections.synchronizedMap(new IdentityHashMap<>());

    private final Map<Class<?>, Method[]> overridesOf = new HashMap<>();
    int createdClasses, superCalls;

    /** Called by the overrides of generated classes. */
    public static Object dispatch(Object self, int method, Object[] args) {
        Handler handler = handlers.get(self);
        if (handler == null) handler = constructing.get();
        return handler.invoke(self, method, args);
    }

    @Override
    public synchronized Class<?> createClass(
            String name, Class<?> superClass, Class<?>[] interfaces, Method[] overrides) {
        createdClasses++;
        // the JVM doesn't allow defining classes in java.*, unlike Subclasser
        String className = name.startsWith("java.") ? "generated." + name : name;
        byte[] bytes = generate(className, superClass, interfaces, overrides);
        Class<?> c =
                new ClassLoader(SubclassingAdapterFactory.class.getClassLoader()) {
                    Class<?> define() {
                        return defineClass(className, bytes, 0, bytes.length);
                    }
                }.define();
        overridesOf.put(c, overrides);
        return c;
    }

    @Override
    public Object newInstance(
            Class<?> adapterClass, Constructor<?> constructor, Object[] args, Handler handler) {
        constructing.set(handler);
        try {
            Object instance =
                    adapterClass.getConstructor(constructor.getParameterTypes()).newInstance(args);
            handlers.put(instance, handler);
            return instance;
        } catch (InvocationTargetException e) {
            throw rethrow(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        } finally {
            constructing.remove();
        }
    }

    @Override
    public Object invokeSuper(Object adapter, Method method, Object[] args) {
        superCalls++;
        Method[] overrides;
        synchronized (this) {
            overrides = overridesOf.get(adapter.getClass());
        }
        for (int i = 0; i < overrides.length; i++) {
            if (overrides[i].equals(method)) {
                try {
                    return adapter.getClass()
                            .getMethod("super$" + i, method.getParameterTypes())
                            .invoke(adapter, args);
                } catch (InvocationTargetException e) {
                    throw rethrow(e.getCause());
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
        throw new IllegalArgumentException("not an override: " + method);
    }

    private static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException) return (RuntimeException) t;
        if (t instanceof Error) throw (Error) t;
        return new RuntimeException(t);
    }

    private static byte[] generate(
            String name, Class<?> superClass, Class<?>[] interfaces, Method[] overrides) {
        String superName = superClass.getName().replace('.', '/');
        ClassFileWriter cfw = new ClassFileWriter(name, superClass.getName(), "<factory>");
        for (Class<?> i : interfaces) cfw.addInterface(i.getName());

        for (Constructor<?> ctor : superClass.getDeclaredConstructors()) {
            int mods = ctor.getModifiers();
            if (!Modifier.isPublic(mods) && !Modifier.isProtected(mods)) continue;
            String desc = descriptor(ctor.getParameterTypes(), Void.TYPE);
            cfw.startMethod("<init>", desc, ClassFileWriter.ACC_PUBLIC);
            cfw.add(ByteCode.ALOAD_0);
            int local = loadArgs(cfw, ctor.getParameterTypes());
            cfw.addInvoke(ByteCode.INVOKESPECIAL, superName, "<init>", desc);
            cfw.add(ByteCode.RETURN);
            cfw.stopMethod(local);
        }

        for (int i = 0; i < overrides.length; i++) {
            Method m = overrides[i];
            Class<?>[] params = m.getParameterTypes();
            String desc = descriptor(params, m.getReturnType());
            cfw.startMethod(m.getName(), desc, ClassFileWriter.ACC_PUBLIC);
            cfw.add(ByteCode.ALOAD_0);
            cfw.addPush(i);
            cfw.addPush(params.length);
            cfw.add(ByteCode.ANEWARRAY, "java/lang/Object");
            int local = 1;
            for (int a = 0; a < params.length; a++) {
                cfw.add(ByteCode.DUP);
                cfw.addPush(a);
                local += load(cfw, local, params[a]);
                box(cfw, params[a]);
                cfw.add(ByteCode.AASTORE);
            }
            cfw.addInvoke(
                    ByteCode.INVOKESTATIC,
                    SubclassingAdapterFactory.class.getName().replace('.', '/'),
                    "dispatch",
                    "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;");
            unboxAndReturn(cfw, m.getReturnType());
            cfw.stopMethod(local);

            if (!Modifier.isAbstract(m.getModifiers()) && !m.getDeclaringClass().isInterface()) {
                cfw.startMethod("super$" + i, desc, ClassFileWriter.ACC_PUBLIC);
                cfw.add(ByteCode.ALOAD_0);
                local = loadArgs(cfw, params);
                cfw.addInvoke(ByteCode.INVOKESPECIAL, superName, m.getName(), desc);
                returnValue(cfw, m.getReturnType());
                cfw.stopMethod(local);
            }
        }
        return cfw.toByteArray();
    }

    private static int loadArgs(ClassFileWriter cfw, Class<?>[] params) {
        int local = 1;
        for (Class<?> p : params) local += load(cfw, local, p);
        return local;
    }

    private static int load(ClassFileWriter cfw, int local, Class<?> type) {
        if (!type.isPrimitive()) {
            cfw.addALoad(local);
        } else if (type == Long.TYPE) {
            cfw.addLLoad(local);
            return 2;
        } else if (type == Double.TYPE) {
            cfw.addDLoad(local);
            return 2;
        } else if (type == Float.TYPE) {
            cfw.addFLoad(local);
        } else {
            cfw.addILoad(local);
        }
        return 1;
    }

    private static void box(ClassFileWriter cfw, Class<?> type) {
        if (!type.isPrimitive()) return;
        String wrapper = wrapper(type);
        cfw.addInvoke(
                ByteCode.INVOKESTATIC,
                wrapper,
                "valueOf",
                "(" + descriptor(type) + ")L" + wrapper + ";");
    }

    private static void unboxAndReturn(ClassFileWriter cfw, Class<?> type) {
        if (type == Void.TYPE) {
            cfw.add(ByteCode.POP);
        } else if (type.isPrimitive()) {
            String wrapper = wrapper(type);
            cfw.add(ByteCode.CHECKCAST, wrapper);
            cfw.addInvoke(
                    ByteCode.INVOKEVIRTUAL, wrapper, type.getName() + "Value", "()" + descriptor(type));
        } else {
            cfw.add(ByteCode.CHECKCAST, type.getName().replace('.', '/'));
        }
        returnValue(cfw, type);
    }

    private static void returnValue(ClassFileWriter cfw, Class<?> type) {
        if (type == Void.TYPE) cfw.add(ByteCode.RETURN);
        else if (!type.isPrimitive()) cfw.add(ByteCode.ARETURN);
        else if (type == Long.TYPE) cfw.add(ByteCode.LRETURN);
        else if (type == Double.TYPE) cfw.add(ByteCode.DRETURN);
        else if (type == Float.TYPE) cfw.add(ByteCode.FRETURN);
        else cfw.add(ByteCode.IRETURN);
    }

    private static String wrapper(Class<?> type) {
        if (type == Integer.TYPE) return "java/lang/Integer";
        if (type == Character.TYPE) return "java/lang/Character";
        String name = type.getName();
        return "java/lang/" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String descriptor(Class<?>[] params, Class<?> ret) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> p : params) sb.append(descriptor(p));
        return sb.append(')').append(descriptor(ret)).toString();
    }

    private static String descriptor(Class<?> type) {
        if (type.isArray()) return "[" + descriptor(type.getComponentType());
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == Void.TYPE) return "V";
        if (type == Boolean.TYPE) return "Z";
        if (type == Long.TYPE) return "J";
        if (type == Byte.TYPE) return "B";
        if (type == Character.TYPE) return "C";
        if (type == Short.TYPE) return "S";
        if (type == Integer.TYPE) return "I";
        if (type == Float.TYPE) return "F";
        return "D";
    }
}
