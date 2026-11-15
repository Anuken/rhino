package rhino.optimizer;

import java.util.HashMap;
import rhino.ScriptRuntime;
import rhino.Token;

/**
 * The operations that compiled code performs through dynamic call sites (see {@link Signatures}),
 * each paired with the static runtime method that implements it generically. That method takes the
 * call site's arguments, followed by the operation's name for named operations like
 * "PROP:GET:{name}" and by {@link #intArg} where one is set.
 *
 * <p>Without invokedynamic, compiled code calls these methods directly. With it, the method is the
 * fallback that {@link rhino.optimizer.indy.Bootstrapper} links when no faster specialization
 * applies.
 */
public enum DynamicOp {
    PROP_GET("PROP:GET", true, true, "propGet"),
    PROP_GETNOWARN("PROP:GETNOWARN", true, true, "propGetNoWarn"),
    PROP_GETSUPER("PROP:GETSUPER", true, true, "propGetSuper"),
    PROP_GETWITHTHIS("PROP:GETWITHTHIS", true, true, "propGetWithThis"),
    PROP_GETWITHTHISOPTIONAL("PROP:GETWITHTHISOPTIONAL", true, true, "propGetWithThisOptional"),
    PROP_SET("PROP:SET", true, true, "propSet"),
    PROP_SETSUPER("PROP:SETSUPER", true, true, "propSetSuper"),
    PROP_GETELEMENT("PROP:GETELEMENT", false, false, "getObjectElem"),
    PROP_GETELEMENTSUPER("PROP:GETELEMENTSUPER", false, false, "getSuperElem"),
    PROP_GETINDEX("PROP:GETINDEX", false, false, "getObjectIndex"),
    PROP_SETELEMENT("PROP:SETELEMENT", false, false, "setObjectElem"),
    PROP_SETELEMENTSUPER("PROP:SETELEMENTSUPER", false, false, "setSuperElem"),
    PROP_SETINDEX("PROP:SETINDEX", false, false, "setObjectIndex"),
    NAME_BIND("NAME:BIND", true, true, "nameBind"),
    NAME_GET("NAME:GET", true, true, "nameGet"),
    NAME_GETWITHTHIS("NAME:GETWITHTHIS", true, true, "nameGetWithThis"),
    NAME_GETWITHTHISOPTIONAL("NAME:GETWITHTHISOPTIONAL", true, true, "nameGetWithThisOptional"),
    NAME_SET("NAME:SET", true, false, "setName"),
    NAME_SETSTRICT("NAME:SETSTRICT", true, false, "strictSetName"),
    NAME_SETCONST("NAME:SETCONST", true, false, "setConst"),
    MATH_ADD("MATH:ADD", false, false, "add"),
    MATH_TOBOOLEAN("MATH:TOBOOLEAN", false, false, "toBoolean"),
    MATH_TOINT32("MATH:TOINT32", false, false, "toInt32"),
    MATH_TOUINT32("MATH:TOUINT32", false, false, "toUint32"),
    MATH_TONUMBER("MATH:TONUMBER", false, false, "toNumber"),
    MATH_TONUMERIC("MATH:TONUMERIC", false, false, "toNumeric"),
    MATH_EQ("MATH:EQ", false, false, "eq"),
    MATH_SHALLOWEQ("MATH:SHALLOWEQ", false, false, "shallowEq"),
    MATH_COMPAREGT("MATH:COMPAREGT", Token.GT),
    MATH_COMPARELT("MATH:COMPARELT", Token.LT),
    MATH_COMPAREGE("MATH:COMPAREGE", Token.GE),
    MATH_COMPARELE("MATH:COMPARELE", Token.LE);

    public static final int NO_INT_ARG = -1;

    private static final HashMap<String, DynamicOp> BY_ID = new HashMap<>();

    static {
        for (DynamicOp op : values()) {
            BY_ID.put(op.id, op);
        }
    }

    /** The "NAMESPACE:OPERATION" prefix of the call site name. */
    public final String id;

    public final boolean named;

    /**
     * True if the implementing method is in {@link OptRuntime}, otherwise {@link ScriptRuntime}.
     */
    public final boolean inOptRuntime;

    public final String method;

    public final int intArg;

    DynamicOp(String id, boolean named, boolean inOptRuntime, String method) {
        this.id = id;
        this.named = named;
        this.inOptRuntime = inOptRuntime;
        this.method = method;
        this.intArg = NO_INT_ARG;
    }

    DynamicOp(String id, int compareToken) {
        this.id = id;
        this.named = false;
        this.inOptRuntime = false;
        this.method = "compare";
        this.intArg = compareToken;
    }

    public Class<?> ownerClass() {
        return inOptRuntime ? OptRuntime.class : ScriptRuntime.class;
    }

    public String ownerName() {
        return inOptRuntime ? "rhino/optimizer/OptRuntime" : "rhino/ScriptRuntime";
    }

    /** Signature of the implementing method, given the call site's signature. */
    public String methodSignature(String siteSignature) {
        int end = siteSignature.indexOf(')');
        String extra = named ? "Ljava/lang/String;" : intArg != NO_INT_ARG ? "I" : "";
        return siteSignature.substring(0, end) + extra + siteSignature.substring(end);
    }

    /** Returns the operation for a call site name like "PROP:GET:foo". */
    public static DynamicOp forName(String siteName) {
        int first = siteName.indexOf(':');
        int second = siteName.indexOf(':', first + 1);
        String id = second < 0 ? siteName : siteName.substring(0, second);
        DynamicOp op = BY_ID.get(id);
        if (op == null || op.named != (second >= 0)) {
            throw new IllegalArgumentException("Unknown dynamic operation: " + siteName);
        }
        return op;
    }

    /** Returns the name operand of a call site name like "PROP:GET:foo", or null if unnamed. */
    public static String operandOf(String siteName) {
        int second = siteName.indexOf(':', siteName.indexOf(':') + 1);
        return second < 0 ? null : siteName.substring(second + 1);
    }
}
