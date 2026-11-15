package rhino;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import rhino.ast.AstNode;
import rhino.ast.AstRoot;
import rhino.ast.Block;
import rhino.ast.FunctionNode;
import rhino.ast.Scope;
import rhino.ast.ScriptNode;

/**
 * Common utilities usable by all the compilers for populating {@link JSDescriptor.Builder} entries
 * from IR nodes.
 */
public class CodeGenUtils {

    /** Populates builder data for a nested function. */
    public static void fillInForNestedFunction(
            JSDescriptor.Builder<JSFunction> builder,
            JSDescriptor.Builder<?> parent,
            FunctionNode fn) {
        final AstNode fnParent = fn.getParent();
        if (!(fnParent instanceof AstRoot
                || fnParent instanceof Scope
                || fnParent instanceof Block)) {
            builder.declaredAsFunctionExpression = true;
            boolean isArrow = fn.getFunctionType() == FunctionNode.ARROW_FUNCTION;
            builder.hasLexicalThis = isArrow;
            // Arrow functions and methods have no prototype property, generator methods do
            builder.hasPrototype =
                    !isArrow
                            && ((!fn.isShorthand() && !fn.isClassMember())
                                    || fn.isES6Generator());
            if (!isArrow) {
                builder.constructor = builder.code;
            } else {
                builder.constructor = new JSCode.NullBuilder<JSFunction>();
            }
        } else {
            builder.hasLexicalThis = false;
            builder.hasPrototype = true;
            builder.constructor = builder.code;
        }

        if (fn.isClassMember()) {
            // Class methods and accessors have no prototype, generator methods do
            builder.hasPrototype = fn.isES6Generator();
        }
        if (fn.isAsync()) {
            // An async generator's prototype is that of its async generator objects
            builder.hasPrototype = fn.isAsyncGenerator();
        }
        fillInForFunction(builder, fn);
    }

    private static void fillInForFunction(JSDescriptor.Builder builder, FunctionNode fn) {
        builder.functionType = fn.getFunctionType();
        builder.requiresActivationFrame = fn.requiresActivation();
        builder.requiresArgumentObject = fn.requiresArgumentObject();
        if (fn.getFunctionName() != null) {
            builder.name = fn.getName();
        }
        if (fn.isInStrictMode()) {
            builder.isStrict = true;
        }
        if (fn.isES6Generator()) {
            builder.isES6Generator = true;
        }
        if (fn.isShorthand()) {
            builder.isShorthand = true;
        }
        if (fn.isAsync()) {
            builder.isAsync = true;
            builder.isAsyncGenerator = fn.isAsyncGenerator();
        }
        if (fn.isClassConstructor()) {
            builder.isClassConstructor = true;
            builder.derivedConstructor = fn.isDerivedClassConstructor();
            builder.defaultConstructor = fn.isDefaultClassConstructor();
        }
        fillInCommon(builder, fn);
    }

    /** Populate builder data for a top level function. */
    public static void fillInForTopLevelFunction(
            JSDescriptor.Builder builder,
            FunctionNode fn,
            String rawSource,
            CompilerEnvirons compilerEnv) {

        builder.hasPrototype = !fn.isAsync() || fn.isAsyncGenerator();

        fillInTopLevelCommon(builder, fn, rawSource, compilerEnv);
        fillInForFunction(builder, fn);
    }

    /** Populate builder data for a top level script. */
    public static void fillInForScript(
            JSDescriptor.Builder builder,
            ScriptNode scriptOrFn,
            String rawSource,
            CompilerEnvirons compilerEnv) {
        builder.hasPrototype = false;

        fillInTopLevelCommon(builder, scriptOrFn, rawSource, compilerEnv);
        fillInCommon(builder, scriptOrFn);
    }

    private static void fillInTopLevelCommon(
            JSDescriptor.Builder builder,
            ScriptNode scriptOrFn,
            String rawSource,
            CompilerEnvirons compilerEnv) {
        builder.sourceFile = scriptOrFn.getSourceName();
        builder.sourceCodeProvider = compilerEnv.getSourceCodeProvider();
        builder.isTopLevel = true;
        builder.isScript = true;
        builder.isEvalFunction = compilerEnv.isInEval();
        builder.isStrict = scriptOrFn.isInStrictMode();
        builder.hasLexicalThis = false;
        builder.securityController = compilerEnv.securityController();
        builder.securityDomain = compilerEnv.securityDomain();
    }

    private static void fillInCommon(JSDescriptor.Builder builder, ScriptNode scriptOrFn) {
        builder.paramAndVarNames =
                disambiguateNames(scriptOrFn.getParamAndVarNames(), scriptOrFn.getParamCount());
        builder.paramCount = scriptOrFn.getParamCount();
        builder.paramIsConst = scriptOrFn.getParamAndVarConst();
        builder.paramAndVarCount = scriptOrFn.getParamAndVarCount();
        builder.hasRestArg = scriptOrFn.hasRestParameter();
        builder.hasDefaultParameters = scriptOrFn.getDefaultParams() != null;

        // Calculate arity (function.length) - count params before first default
        builder.arity = FunctionNode.calculateFunctionArity(scriptOrFn);

        builder.sourceStart = scriptOrFn.getRawSourceStart();
        builder.sourceEnd = scriptOrFn.getRawSourceEnd();
    }

    /** Configure the constructor appropriately based on a function's type. */
    public static <T extends ScriptOrFn<T>> void setConstructor(
            JSDescriptor.Builder<T> builder, ScriptNode scriptOrFn) {
        if (scriptOrFn instanceof FunctionNode) {
            FunctionNode f = (FunctionNode) scriptOrFn;
            boolean isArrow = f.getFunctionType() == FunctionNode.ARROW_FUNCTION;
            if (isArrow || f.isMethodDefinition() || f.isClassMember() || f.isGenerator()) {
                builder.constructor = new JSCode.NullBuilder<T>();
            } else {
                builder.constructor = builder.code;
            }
        } else {
            builder.constructor = new JSCode.NullBuilder<T>();
        }
    }

    /**
     * Disambiguate all variable names after the parameters. We avoid disambiguating the parameters
     * themselves because those names can be repeated and are not unique even if an activation frame
     * is used.
     *
     * <p>This is only required when an interpreted function might have duplicate local variable
     * names and might be run under the debugger. This can only occur if the function has nested
     * blocks, uses `let` with a variable name in that nested block which is also used outside that
     * block, and does not require the use of an activation frame for any other reason. When running
     * under the debugger variables are accessed by name rather than by index, and hence those names
     * must not include duplicates.
     */
    private static String[] disambiguateNames(String[] names, int paramCount) {
        String[] result = new String[names.length];
        var checkMap = new HashMap<String, AtomicInteger>();
        for (int i = 0; i < names.length; i++) {
            var counter = checkMap.get(names[i]);
            if (counter == null) {
                counter = new AtomicInteger();
                checkMap.put(names[i], counter);
            }
            var count = counter.getAndIncrement();
            if (i >= paramCount && count > 0) {
                result[i] = String.format("%s(%d)", names[i], count);
            } else {
                result[i] = names[i];
            }
        }
        return result;
    }
}
