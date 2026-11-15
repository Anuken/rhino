package rhino.optimizer.indy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.ref.WeakReference;
import rhino.optimizer.DynamicOp;
import rhino.BaseFunction;
import rhino.ConsString;
import rhino.Context;
import rhino.NativeArray;
import rhino.RhinoException;
import rhino.ScriptRuntime;
import rhino.ScriptableObject;
import rhino.Undefined;
import rhino.VarScope;

/**
 * Links the call sites created by {@link Bootstrapper}: either generically, to the runtime method
 * of a {@link DynamicOp}, or to a fast path specialized for the values the site is called with,
 * which skips the type dispatch of the generic implementation. The fast paths are:
 *
 * <ul>
 *   <li>reads of constants (neither writable nor configurable, like those declared with "const")
 *       from a given object or scope, which just return the value;
 *   <li>arithmetic, comparisons and conversions of Integer, Double and Boolean values;
 *   <li>string concatenation and equality, and the "length" of strings and arrays;
 *   <li>the "prototype" of functions that have their own.
 * </ul>
 */
@SuppressWarnings("AndroidJdkLibsChecker")
final class Linker {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();

    private static final MethodType TEST1 = MethodType.methodType(boolean.class, Object.class);
    private static final MethodType TEST2 =
            MethodType.methodType(boolean.class, Object.class, Object.class);

    private Linker() {}

    /** A fast path, valid for the arguments for which guard returns true. */
    static final class Guarded {
        final MethodHandle guard;
        final MethodHandle target;

        Guarded(MethodHandle guard, MethodHandle target) {
            this.guard = guard;
            this.target = target;
        }
    }

    /** Returns the generic implementation of an operation, adapted to the call site type. */
    static MethodHandle generic(DynamicOp op, String name, MethodType type)
            throws ReflectiveOperationException {
        int count = type.parameterCount();
        if (op.named) {
            MethodType full = type.appendParameterTypes(String.class);
            MethodHandle mh = LOOKUP.findStatic(op.ownerClass(), op.method, full);
            return MethodHandles.insertArguments(mh, count, name);
        }
        if (op.intArg != DynamicOp.NO_INT_ARG) {
            MethodType full = type.appendParameterTypes(int.class);
            MethodHandle mh = LOOKUP.findStatic(op.ownerClass(), op.method, full);
            return MethodHandles.insertArguments(mh, count, op.intArg);
        }
        return LOOKUP.findStatic(op.ownerClass(), op.method, type);
    }

    /** Returns a fast path for the given arguments, or null if there is none. */
    static Guarded link(DynamicOp op, String name, MethodType type, Object[] args)
            throws ReflectiveOperationException {
        Object receiver = args[0];
        Object arg = args.length > 1 ? args[1] : null;

        if (receiver instanceof ScriptableObject) {
            Guarded constant = linkConstant(op, name, type, (ScriptableObject) receiver);
            if (constant != null) {
                return constant;
            }
        }
        if (receiver instanceof Integer) {
            return linkInteger(op, type, arg);
        } else if (receiver instanceof Double) {
            return linkDouble(op, type, arg);
        } else if (receiver instanceof Boolean) {
            return linkBoolean(op, type, arg);
        } else if (receiver instanceof String) {
            return linkString(op, name, type, arg);
        } else if (receiver instanceof ConsString) {
            return linkConsString(op, name, type, arg);
        } else if (receiver instanceof NativeArray) {
            if (isGet(op) && "length".equals(name)) {
                return fast(type, "isNativeArray", TEST1, "arrayLength");
            }
        } else if (receiver instanceof BaseFunction) {
            if (isGet(op) && "prototype".equals(name) && hasOwnPrototype(receiver)) {
                return fast(type, "hasOwnPrototype", TEST1, "functionPrototype");
            }
        }
        return null;
    }

    private static boolean isGet(DynamicOp op) {
        return op == DynamicOp.PROP_GET || op == DynamicOp.PROP_GETNOWARN;
    }

    private static boolean isEq(DynamicOp op) {
        return op == DynamicOp.MATH_EQ || op == DynamicOp.MATH_SHALLOWEQ;
    }

    private static Guarded linkConstant(
            DynamicOp op, String name, MethodType type, ScriptableObject target)
            throws ReflectiveOperationException {
        if (!isGet(op) && op != DynamicOp.NAME_GET) {
            return null;
        }
        Object value = getConstValue(target, name);
        if (value == null) {
            return null;
        }
        // Weak references, so that the call site keeps neither the object nor the value alive.
        WeakReference<Object> targetRef = new WeakReference<>(target);
        WeakReference<Object> valueRef = new WeakReference<>(value);
        MethodHandle guard =
                LOOKUP.findStatic(
                        Linker.class,
                        "isConstant",
                        MethodType.methodType(
                                boolean.class,
                                WeakReference.class,
                                WeakReference.class,
                                Object.class));
        guard = MethodHandles.insertArguments(guard, 0, targetRef, valueRef);
        MethodHandle get =
                LOOKUP.findVirtual(WeakReference.class, "get", MethodType.methodType(Object.class));
        get = MethodHandles.dropArguments(get.bindTo(valueRef), 0, type.parameterList());
        return new Guarded(adaptGuard(guard, type), get.asType(type));
    }

    /**
     * Returns the value of the named property if it is an own constant of the object, otherwise
     * null (so null constants are not handled).
     */
    private static Object getConstValue(ScriptableObject target, String name) {
        try {
            if (target.has(name, target)) {
                int attributes = target.getAttributes(name);
                if ((attributes & ScriptableObject.READONLY) != 0
                        && (attributes & ScriptableObject.PERMANENT) != 0
                        && (attributes & ScriptableObject.UNINITIALIZED_CONST) == 0
                        // A getter can return a different value each time.
                        && target.getGetterOrSetter(name, 0, target, false) == Undefined.instance) {
                    return target.get(name, target);
                }
            }
        } catch (RhinoException re) {
            // Some objects fail on these operations; treat that as not constant.
        }
        return null;
    }

    private static Guarded linkInteger(DynamicOp op, MethodType type, Object arg)
            throws ReflectiveOperationException {
        boolean intArg = arg instanceof Integer;
        boolean doubleArg = arg instanceof Double;
        String test = intArg ? "isIntInt" : "isIntDouble";
        switch (op) {
            case MATH_ADD:
                if (intArg || doubleArg) {
                    return fast(type, test, TEST2, intArg ? "addIntInt" : "addIntDouble");
                }
                return null;
            case MATH_EQ:
            case MATH_SHALLOWEQ:
                if (intArg || doubleArg) {
                    return fast(type, test, TEST2, intArg ? "eqIntInt" : "eqIntDouble");
                }
                return null;
            case MATH_COMPARELT:
            case MATH_COMPAREGT:
            case MATH_COMPARELE:
            case MATH_COMPAREGE:
                if (intArg || doubleArg) {
                    return fast(
                            type, test, TEST2, compareName(op, intArg ? "IntInt" : "IntDouble"));
                }
                return null;
            case MATH_TOBOOLEAN:
                return fast(type, "isInt", TEST1, "intToBoolean");
            case MATH_TONUMBER:
                return fast(type, "isInt", TEST1, "intToNumber");
            case MATH_TONUMERIC:
                return fast(type, "isInt", TEST1, "intToNumeric");
            case MATH_TOINT32:
                return fast(type, "isInt", TEST1, "intToInt32");
            case MATH_TOUINT32:
                return fast(type, "isInt", TEST1, "intToUint32");
            default:
                return null;
        }
    }

    private static Guarded linkDouble(DynamicOp op, MethodType type, Object arg)
            throws ReflectiveOperationException {
        boolean doubleArg = arg instanceof Double;
        boolean intArg = arg instanceof Integer;
        String test = doubleArg ? "isDoubleDouble" : "isDoubleInt";
        switch (op) {
            case MATH_ADD:
                if (intArg || doubleArg) {
                    return fast(type, test, TEST2, doubleArg ? "addDoubleDouble" : "addDoubleInt");
                }
                return null;
            case MATH_EQ:
            case MATH_SHALLOWEQ:
                if (intArg || doubleArg) {
                    return fast(type, test, TEST2, doubleArg ? "eqDoubleDouble" : "eqDoubleInt");
                }
                return null;
            case MATH_COMPARELT:
            case MATH_COMPAREGT:
            case MATH_COMPARELE:
            case MATH_COMPAREGE:
                if (intArg || doubleArg) {
                    return fast(
                            type,
                            test,
                            TEST2,
                            compareName(op, doubleArg ? "DoubleDouble" : "DoubleInt"));
                }
                return null;
            case MATH_TOBOOLEAN:
                return fast(type, "isDouble", TEST1, "doubleToBoolean");
            case MATH_TONUMBER:
                return fast(type, "isDouble", TEST1, "doubleToNumber");
            case MATH_TONUMERIC:
                return fast(type, "isDouble", TEST1, "doubleToNumeric");
            case MATH_TOINT32:
                return fast(type, "isDouble", TEST1, "doubleToInt32");
            case MATH_TOUINT32:
                return fast(type, "isDouble", TEST1, "doubleToUint32");
            default:
                return null;
        }
    }

    private static Guarded linkBoolean(DynamicOp op, MethodType type, Object arg)
            throws ReflectiveOperationException {
        if (isEq(op) && arg instanceof Boolean) {
            return fast(type, "isBooleanBoolean", TEST2, "eqObjects");
        } else if (op == DynamicOp.MATH_TOBOOLEAN) {
            return fast(type, "isBoolean", TEST1, "booleanToBoolean");
        }
        return null;
    }

    private static Guarded linkString(DynamicOp op, String name, MethodType type, Object arg)
            throws ReflectiveOperationException {
        if (op == DynamicOp.MATH_ADD && arg instanceof CharSequence) {
            return fast(type, "isStringCharSequence", TEST2, "addStrings");
        } else if (isEq(op) && arg instanceof String) {
            return fast(type, "isStringString", TEST2, "eqObjects");
        } else if (isGet(op) && "length".equals(name)) {
            return fast(type, "isString", TEST1, "stringLength");
        }
        return null;
    }

    private static Guarded linkConsString(DynamicOp op, String name, MethodType type, Object arg)
            throws ReflectiveOperationException {
        if (op == DynamicOp.MATH_ADD && arg instanceof CharSequence) {
            return fast(type, "isConsStringCharSequence", TEST2, "addStrings");
        } else if (isGet(op) && "length".equals(name)) {
            return fast(type, "isConsString", TEST1, "stringLength");
        }
        return null;
    }

    private static String compareName(DynamicOp op, String types) {
        switch (op) {
            case MATH_COMPARELT:
                return "lt" + types;
            case MATH_COMPAREGT:
                return "gt" + types;
            case MATH_COMPARELE:
                return "le" + types;
            default:
                return "ge" + types;
        }
    }

    /** A fast path implemented by static methods of this class with the call site's type. */
    private static Guarded fast(MethodType type, String guard, MethodType guardType, String target)
            throws ReflectiveOperationException {
        return new Guarded(
                adaptGuard(LOOKUP.findStatic(Linker.class, guard, guardType), type),
                LOOKUP.findStatic(Linker.class, target, type));
    }

    /** Adapts a guard on leading arguments to test a call with the given type. */
    private static MethodHandle adaptGuard(MethodHandle guard, MethodType type) {
        int count = guard.type().parameterCount();
        guard =
                MethodHandles.dropArguments(
                        guard, count, type.parameterList().subList(count, type.parameterCount()));
        return guard.asType(type.changeReturnType(boolean.class));
    }

    // Guards

    @SuppressWarnings("unused")
    private static boolean isConstant(
            WeakReference<Object> targetRef, WeakReference<Object> valueRef, Object receiver) {
        return receiver == targetRef.get() && valueRef.get() != null;
    }

    @SuppressWarnings("unused")
    private static boolean isInt(Object o) {
        return o instanceof Integer;
    }

    @SuppressWarnings("unused")
    private static boolean isDouble(Object o) {
        return o instanceof Double;
    }

    @SuppressWarnings("unused")
    private static boolean isBoolean(Object o) {
        return o instanceof Boolean;
    }

    @SuppressWarnings("unused")
    private static boolean isString(Object o) {
        return o instanceof String;
    }

    @SuppressWarnings("unused")
    private static boolean isConsString(Object o) {
        return o instanceof ConsString;
    }

    @SuppressWarnings("unused")
    private static boolean isNativeArray(Object o) {
        return o instanceof NativeArray;
    }

    private static boolean hasOwnPrototype(Object o) {
        return o instanceof BaseFunction && ((BaseFunction) o).hasPrototypeProperty();
    }

    @SuppressWarnings("unused")
    private static boolean isIntInt(Object a, Object b) {
        return a instanceof Integer && b instanceof Integer;
    }

    @SuppressWarnings("unused")
    private static boolean isIntDouble(Object a, Object b) {
        return a instanceof Integer && b instanceof Double;
    }

    @SuppressWarnings("unused")
    private static boolean isDoubleDouble(Object a, Object b) {
        return a instanceof Double && b instanceof Double;
    }

    @SuppressWarnings("unused")
    private static boolean isDoubleInt(Object a, Object b) {
        return a instanceof Double && b instanceof Integer;
    }

    @SuppressWarnings("unused")
    private static boolean isBooleanBoolean(Object a, Object b) {
        return a instanceof Boolean && b instanceof Boolean;
    }

    @SuppressWarnings("unused")
    private static boolean isStringString(Object a, Object b) {
        return a instanceof String && b instanceof String;
    }

    @SuppressWarnings("unused")
    private static boolean isStringCharSequence(Object a, Object b) {
        return a instanceof String && b instanceof CharSequence;
    }

    @SuppressWarnings("unused")
    private static boolean isConsStringCharSequence(Object a, Object b) {
        return a instanceof ConsString && b instanceof CharSequence;
    }

    // Fast paths, with the signatures of their call sites (see Signatures)

    @SuppressWarnings("unused")
    private static Object addIntInt(Object a, Object b, Context cx) {
        return ScriptRuntime.add((Integer) a, (Integer) b);
    }

    @SuppressWarnings("unused")
    private static Object addIntDouble(Object a, Object b, Context cx) {
        return ((Integer) a).doubleValue() + (Double) b;
    }

    @SuppressWarnings("unused")
    private static Object addDoubleDouble(Object a, Object b, Context cx) {
        return (Double) a + (Double) b;
    }

    @SuppressWarnings("unused")
    private static Object addDoubleInt(Object a, Object b, Context cx) {
        return (Double) a + (Integer) b;
    }

    @SuppressWarnings("unused")
    private static Object addStrings(Object a, Object b, Context cx) {
        return new ConsString((CharSequence) a, (CharSequence) b);
    }

    @SuppressWarnings("unused")
    private static boolean eqObjects(Object a, Object b) {
        return a.equals(b);
    }

    @SuppressWarnings("unused")
    private static boolean eqIntInt(Object a, Object b) {
        return ((Integer) a).intValue() == ((Integer) b).intValue();
    }

    @SuppressWarnings("unused")
    private static boolean eqIntDouble(Object a, Object b) {
        return ((Integer) a).doubleValue() == (Double) b;
    }

    @SuppressWarnings("unused")
    private static boolean eqDoubleDouble(Object a, Object b) {
        return ((Double) a).doubleValue() == ((Double) b).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean eqDoubleInt(Object a, Object b) {
        return (Double) a == ((Integer) b).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean ltIntInt(Object a, Object b) {
        return (Integer) a < (Integer) b;
    }

    @SuppressWarnings("unused")
    private static boolean gtIntInt(Object a, Object b) {
        return (Integer) a > (Integer) b;
    }

    @SuppressWarnings("unused")
    private static boolean leIntInt(Object a, Object b) {
        return (Integer) a <= (Integer) b;
    }

    @SuppressWarnings("unused")
    private static boolean geIntInt(Object a, Object b) {
        return (Integer) a >= (Integer) b;
    }

    @SuppressWarnings("unused")
    private static boolean ltIntDouble(Object a, Object b) {
        return ((Integer) a).doubleValue() < (Double) b;
    }

    @SuppressWarnings("unused")
    private static boolean gtIntDouble(Object a, Object b) {
        return ((Integer) a).doubleValue() > (Double) b;
    }

    @SuppressWarnings("unused")
    private static boolean leIntDouble(Object a, Object b) {
        return ((Integer) a).doubleValue() <= (Double) b;
    }

    @SuppressWarnings("unused")
    private static boolean geIntDouble(Object a, Object b) {
        return ((Integer) a).doubleValue() >= (Double) b;
    }

    @SuppressWarnings("unused")
    private static boolean ltDoubleDouble(Object a, Object b) {
        return (Double) a < (Double) b;
    }

    @SuppressWarnings("unused")
    private static boolean gtDoubleDouble(Object a, Object b) {
        return (Double) a > (Double) b;
    }

    @SuppressWarnings("unused")
    private static boolean leDoubleDouble(Object a, Object b) {
        return (Double) a <= (Double) b;
    }

    @SuppressWarnings("unused")
    private static boolean geDoubleDouble(Object a, Object b) {
        return (Double) a >= (Double) b;
    }

    @SuppressWarnings("unused")
    private static boolean ltDoubleInt(Object a, Object b) {
        return (Double) a < ((Integer) b).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean gtDoubleInt(Object a, Object b) {
        return (Double) a > ((Integer) b).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean leDoubleInt(Object a, Object b) {
        return (Double) a <= ((Integer) b).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean geDoubleInt(Object a, Object b) {
        return (Double) a >= ((Integer) b).doubleValue();
    }

    @SuppressWarnings("unused")
    private static boolean intToBoolean(Object o) {
        return (Integer) o != 0;
    }

    @SuppressWarnings("unused")
    private static double intToNumber(Object o) {
        return (Integer) o;
    }

    @SuppressWarnings("unused")
    private static Number intToNumeric(Object o) {
        return (Integer) o;
    }

    @SuppressWarnings("unused")
    private static int intToInt32(Object o) {
        return (Integer) o;
    }

    @SuppressWarnings("unused")
    private static long intToUint32(Object o) {
        return (Integer) o & 0xffffffffL;
    }

    @SuppressWarnings("unused")
    private static boolean doubleToBoolean(Object o) {
        double d = (Double) o;
        return d == d && d != 0.0;
    }

    @SuppressWarnings("unused")
    private static double doubleToNumber(Object o) {
        return (Double) o;
    }

    @SuppressWarnings("unused")
    private static Number doubleToNumeric(Object o) {
        return (Double) o;
    }

    @SuppressWarnings("unused")
    private static int doubleToInt32(Object o) {
        return ScriptRuntime.toInt32(((Double) o).doubleValue());
    }

    @SuppressWarnings("unused")
    private static long doubleToUint32(Object o) {
        return ScriptRuntime.toUint32(((Double) o).doubleValue());
    }

    @SuppressWarnings("unused")
    private static boolean booleanToBoolean(Object o) {
        return (Boolean) o;
    }

    @SuppressWarnings("unused")
    private static Object stringLength(Object o, Context cx, VarScope scope) {
        return ((CharSequence) o).length();
    }

    @SuppressWarnings("unused")
    private static Object arrayLength(Object o, Context cx, VarScope scope) {
        long length = ((NativeArray) o).getLength();
        if (length < Integer.MAX_VALUE) {
            return (int) length;
        }
        return (double) length;
    }

    @SuppressWarnings("unused")
    private static Object functionPrototype(Object o, Context cx, VarScope scope) {
        return ((BaseFunction) o).getPrototypeProperty();
    }
}
