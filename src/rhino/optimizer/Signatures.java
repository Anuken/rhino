package rhino.optimizer;

/**
 * This class defines the method signatures for the properties used by the dynamic operations in the
 * bytecode (see {@link DynamicOp}). This helps us identify what each bytecode operation means, and
 * what the method signature should be. The method signatures here don't necessarily map 1:1 with
 * ScriptRuntime operations -- the "name" part of the operation name is passed as an extra last
 * argument to the method that implements it. Also, many of the "name" operations have a different signature than
 * the ScriptRuntime equivalents because in these signatures we are trying to make the "target" of
 * each operation the first argument to make future optimizations easier.
 */
interface Signatures {
    /**
     * PROP:GET:{name}: Looks up the object property named "name". Falls back to
     * ScriptRuntime.getObjectPropNoWarn.
     */
    String PROP_GET =
            "(Ljava/lang/Object;"
                    + "Lrhino/Context;"
                    + "Lrhino/VarScope;"
                    + ")Ljava/lang/Object;";

    /**
     * PROP:GETNOWARN:{name}: Looks up the object property named "name" and does not warn if it does
     * not exist. Falls back to ScriptRuntime.getObjectProp.
     */
    String PROP_GET_NOWARN =
            "(Ljava/lang/Object;Lrhino/Context;"
                    + "Lrhino/VarScope;)Ljava/lang/Object;";

    /**
     * PROP:GET_SUPER:{name}: Looks up the super property named "name". Falls back to
     * ScriptRuntime.getSuperProp.
     */
    String PROP_GET_SUPER =
            "(Ljava/lang/Object;" // superObj
                    + "Lrhino/Context;" // cx
                    + "Lrhino/VarScope;" // scope
                    + "Ljava/lang/Object;" // thisObj
                    + "Z" // noWarn
                    + ")Ljava/lang/Object;";

    /**
     * PROP:GETWITHTHIS:{name}: Looks up the target of a method call, see
     * ScriptRuntime.getPropCallTarget.
     */
    String PROP_GET_THIS =
            "(Ljava/lang/Object;"
                    + "Lrhino/Context;"
                    + "Lrhino/VarScope;"
                    + ")Ljava/lang/Object;";

    /**
     * PROP:GETINDEX: Get a property from an object based on a numeric index. Falls back to
     * ScriptRuntime.getObjectIndex.
     */
    String PROP_GET_INDEX =
            "(Ljava/lang/Object;"
                    + "D"
                    + "Lrhino/Context;"
                    + "Lrhino/VarScope;"
                    + ")Ljava/lang/Object;";

    /**
     * PROP:GETELEMENT: Get a property from an object based on an element ID, which could be a
     * string, number, or symbol. Falls back to ScriptRuntime.getObjectElem.
     */
    String PROP_GET_ELEMENT =
            "(Ljava/lang/Object;"
                    + "Ljava/lang/Object;"
                    + "Lrhino/Context;"
                    + "Lrhino/VarScope;"
                    + ")Ljava/lang/Object;";

    /**
     * PROP:GETELEMENTSUPER: Get a property from super based on an element ID, which could be a
     * string, number, or symbol. Falls back to ScriptRuntime.getSuperElem.
     */
    String PROP_GET_ELEMENT_SUPER =
            "(Ljava/lang/Object;" // super
                    + "Ljava/lang/Object;" // elem
                    + "Lrhino/Context;" // cx
                    + "Lrhino/VarScope;" // scope
                    + "Ljava/lang/Object;" // this
                    + ")Ljava/lang/Object;";

    /**
     * PROP:SET:{name}: Sets the named property on an object. Falls back to
     * ScriptRuntime.setObjectProp.
     */
    String PROP_SET =
            "(Ljava/lang/Object;Ljava/lang/Object;"
                    + "Lrhino/Context;Lrhino/VarScope;)Ljava/lang/Object;";

    /**
     * PROP:SETSUPER:{name}: Sets the named property on super. Falls back to
     * ScriptRuntime.setSuperProp.
     */
    String PROP_SET_SUPER =
            "("
                    + "Ljava/lang/Object;" // superObj
                    + "Ljava/lang/Object;" // value
                    + "Lrhino/Context;" // cx
                    + "Lrhino/VarScope;" // scope
                    + "Ljava/lang/Object;" // thisObj
                    + ")Ljava/lang/Object;";

    /**
     * PROP:SETINDEX: Set a property on an object based on a numeric index. Falls back to
     * ScriptRuntime.setObjectIndex.
     */
    String PROP_SET_INDEX =
            "(Ljava/lang/Object;"
                    + "D"
                    + "Ljava/lang/Object;"
                    + "Lrhino/Context;"
                    + "Lrhino/VarScope;"
                    + ")Ljava/lang/Object;";

    /**
     * PROP:SETELEMENT: Set a property on an object based on an identifier. Falls back to
     * ScriptRuntime.setObjectElem.
     */
    String PROP_SET_ELEMENT =
            "(Ljava/lang/Object;"
                    + "Ljava/lang/Object;"
                    + "Ljava/lang/Object;"
                    + "Lrhino/Context;"
                    + "Lrhino/VarScope;"
                    + ")Ljava/lang/Object;";

    /**
     * PROP:SETELEMENTSUPER: Set a property on super based on an identifier. Falls back to
     * ScriptRuntime.setSuperElem.
     */
    String PROP_SET_ELEMENT_SUPER =
            "(Ljava/lang/Object;" // super
                    + "Ljava/lang/Object;" // elem
                    + "Ljava/lang/Object;" // value
                    + "Lrhino/Context;" // cx
                    + "Lrhino/VarScope;" // scope
                    + "Ljava/lang/Object;" // this
                    + ")Ljava/lang/Object;";

    /**
     * NAME:GET:{name}: Looks up a the named value from the scope. Falls back to ScriptRuntime.name.
     * Compared to that function, this version of the signature puts the "scope" first in the
     * argument list rather than second. This makes it easier for future linkers to work because
     * they can always assume that the "receiver" of an operation is the first argument.
     */
    String NAME_GET =
            "(Lrhino/VarScope;"
                    + "Lrhino/Context;"
                    + ")Ljava/lang/Object;";

    /**
     * NAME:GETWITHTHIS:{name}: Looks up the target of a call by name, see
     * ScriptRuntime.getNameCallTarget. Also, this version of the signature makes the scope the
     * first argument, as described above. Also, NAME:GETWITHTHISOPTIONAL:{name} has different
     * semantics for an optional function call, but it uses this same signature.
     */
    String NAME_GET_THIS =
            "(Lrhino/VarScope;"
                    + "Lrhino/Context;"
                    + ")Ljava/lang/Object;";

    /** NAME:SET:{name}: Sets the named value in the scope. Falls back to ScriptRuntime.setName. */
    String NAME_SET =
            "(Lrhino/VarScope;"
                    + "Ljava/lang/Object;"
                    + "Lrhino/Context;"
                    + "Lrhino/VarScope;"
                    + ")Ljava/lang/Object;";

    /**
     * NAME:BIND:{name}: Bind the named value into the current scope. Falls back to
     * ScriptRuntime.bind. Like some methods above, this version of the signature puts the scope
     * first in the argument list so that future linkers can have a consistent place to find the
     * "receiver".
     */
    String NAME_BIND =
            "(Lrhino/VarScope;"
                    + "Lrhino/Context;"
                    + ")Lrhino/VarScope;";

    /**
     * NAME:SETSTRICT:{name}: Sets the named value in the scope, and enforces strict mode. Falls
     * back to ScriptRuntime.strictSetName.
     */
    String NAME_SET_STRICT =
            "(Lrhino/VarScope;"
                    + "Ljava/lang/Object;"
                    + "Lrhino/Context;"
                    + "Lrhino/VarScope;"
                    + ")Ljava/lang/Object;";

    /**
     * NAME:SETCONST:{name}: Sets the named constant in the scope. Falls back to
     * ScriptRuntime.setConst.
     */
    String NAME_SET_CONST =
            "(Lrhino/VarScope;"
                    + "Ljava/lang/Object;"
                    + "Lrhino/Context;"
                    + ")Ljava/lang/Object;";

    /**
     * MATH:ADD: Add the first two arguments on the stack, which could be numbers, strings, or
     * really just about anything.
     */
    String MATH_ADD =
            "(Ljava/lang/Object;"
                    + "Ljava/lang/Object;"
                    + "Lrhino/Context;"
                    + ")Ljava/lang/Object;";

    /** MATH:TOBOOLEAN: Make the object into a primitive boolean. */
    String MATH_TO_BOOLEAN = "(Ljava/lang/Object;)Z";

    /** MATH:EQ: Are the two arguments equal? */
    String MATH_EQ = "(Ljava/lang/Object;Ljava/lang/Object;)Z";

    /** MATH:SHALLOWEQ: Like EQ but not. */
    String MATH_SHALLOW_EQ = MATH_EQ;

    /**
     * A list of four possible compare operations, that all share the same signature:
     *
     * <ul>
     *   <li>MATH:COMPAREGT
     *   <li>MATH:COMPARELT
     *   <li>MATH:COMPAREGE
     *   <li>MATH:COMPARELE
     * </ul>
     */
    String MATH_COMPARE = "(Ljava/lang/Object;Ljava/lang/Object;)Z";

    /** MATH:TONUMBER: Convert the object to a Java "double". */
    String MATH_TO_NUMBER = "(Ljava/lang/Object;)D";

    /** MATH:TONUMERIC: Convert the object to a Java "Number". */
    String MATH_TO_NUMERIC = "(Ljava/lang/Object;)Ljava/lang/Number;";

    /** MATH:TOINT32: Convert the object to a Java "int". */
    String MATH_TO_INT32 = "(Ljava/lang/Object;)I";

    /** MATH:TOUINT32: Convert the object to a Java "long" that represents an unsigned integer. */
    String MATH_TO_UINT32 = "(Ljava/lang/Object;)J";
}
