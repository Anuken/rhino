package rhino;

import rhino.ast.FunctionNode;
import rhino.debug.DebuggableScript;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class holds all the meta-data associated with a function in as an immutable form as
 * possible, along with the executable things associated with the function. The idea here is to push
 * all the expensive parts of creating this to an object that can be cached and reused, while
 * function objects represented by {@link JSFunction}s become more lightweight in their creation.
 */
public final class JSDescriptor<T extends ScriptOrFn<T>> implements DebuggableScript {
    private static final int IS_STRICT_FLAG = 1;
    private static final int IS_SCRIPT_FLAG = 1 << 1;
    private static final int IS_TOP_LEVEL_FLAG = 1 << 2;
    private static final int IS_ES6_GENERATOR_FLAG = 1 << 3;
    private static final int IS_SHORTHAND_FLAG = 1 << 4;
    private static final int HAS_PROTOTYPE_FLAG = 1 << 5;
    private static final int HAS_LEXICAL_THIS_FLAG = 1 << 6;
    private static final int IS_EVAL_FUNCTION_FLAG = 1 << 7;
    private static final int HAS_REST_ARG_FLAG = 1 << 8;
    private static final int HAS_DEFAULT_PARAMETERS_FLAG = 1 << 9;
    private static final int REQUIRES_ACTIVATION_FRAME_FLAG = 1 << 10;
    private static final int REQUIRES_ARGUMENT_OBJECT_FLAG = 1 << 11;
    private static final int DECLARED_AS_FUNCTION_EXPRESSION_FLAG = 1 << 12;
    /** Values of the kindFlags constructor argument. */
    public static final int CLASS_CONSTRUCTOR = 1,
            DERIVED = 2,
            DEFAULT = 4,
            ASYNC = 8,
            ASYNC_GENERATOR = 16;

    private static final int DERIVED_CONSTRUCTOR_FLAG = 1 << 13;
    private static final int CLASS_CONSTRUCTOR_FLAG = 1 << 14;
    private static final int DEFAULT_CONSTRUCTOR_FLAG = 1 << 15;
    private static final int ASYNC_FLAG = 1 << 16;
    private static final int ASYNC_GENERATOR_FLAG = 1 << 17;

    private final JSCode<T> code;
    private final JSCode<T> constructor;
    private final JSDescriptor<?> parent;
    public List<JSDescriptor<JSFunction>> nestedFunctions;
    private final String[] paramAndVarNames;
    private final boolean[] paramIsConst;
    private final boolean hasConstParamOrVar;
    private final int flags;
    private final String sourceFile;
    private volatile SourceCodeProvider sourceCodeProvider;
    private final int sourceStart;
    private final int sourceEnd;
    private final String name;
    private final int languageVersion;
    private final int paramAndVarCount;
    private final int paramCount;
    private final int arity; // number of params before first default for reporting as length
    private final int functionType;

    public JSDescriptor(
            JSCode<T> code,
            JSCode<T> constructor,
            JSDescriptor<?> parent,
            String[] paramAndVarNames,
            boolean[] paramIsConst,
            boolean isStrict,
            boolean isScript,
            boolean isTopLevel,
            boolean isES6Generator,
            boolean isShorthand,
            boolean hasPrototype,
            boolean hasLexicalThis,
            boolean isEvalFunction,
            boolean hasRestArg,
            String sourceFile,
            SourceCodeProvider sourceCodeProvider,
            int sourceStart,
            int sourceEnd,
            String name,
            int languageVersion,
            int paramAndVarCount,
            int paramCount,
            int arity,
            boolean hasDefaultParameters,
            boolean requiresActivationFrame,
            boolean requiresArgumentObject,
            boolean declaredAsFunctionExpression,
            int kindFlags,
            int functionType) {
        boolean isClassConstructor = (kindFlags & CLASS_CONSTRUCTOR) != 0;
        // A class constructor throws when called, and runs its body only when constructed.
        // An async function's code is a generator, which a call runs to completion through
        // promises.
        if (isClassConstructor) {
            this.code = (JSCode<T>) JSCode.CLASS_CONSTRUCTOR_CALL;
        } else if ((kindFlags & ASYNC_GENERATOR) != 0) {
            this.code = (JSCode<T>) new AsyncGeneratorCode((JSCode<JSFunction>) code);
        } else if ((kindFlags & ASYNC) != 0) {
            this.code = (JSCode<T>) new AsyncFunctionCode((JSCode<JSFunction>) code);
        } else {
            this.code = code;
        }
        this.constructor = isClassConstructor ? code : constructor;
        this.parent = parent;
        this.paramAndVarNames = paramAndVarNames;
        this.paramIsConst = paramIsConst;
        boolean hasConst = false;
        if (paramIsConst != null) {
            for (boolean isConst : paramIsConst) {
                hasConst |= isConst;
            }
        }
        this.hasConstParamOrVar = hasConst;

        int flags = 0;
        flags = flags | (isStrict ? IS_STRICT_FLAG : 0);
        flags = flags | (isScript ? IS_SCRIPT_FLAG : 0);
        flags = flags | (isTopLevel ? IS_TOP_LEVEL_FLAG : 0);
        flags = flags | (isES6Generator ? IS_ES6_GENERATOR_FLAG : 0);
        flags = flags | (isShorthand ? IS_SHORTHAND_FLAG : 0);
        flags = flags | (hasPrototype ? HAS_PROTOTYPE_FLAG : 0);
        flags = flags | (hasLexicalThis ? HAS_LEXICAL_THIS_FLAG : 0);
        flags = flags | (isEvalFunction ? IS_EVAL_FUNCTION_FLAG : 0);
        flags = flags | (hasRestArg ? HAS_REST_ARG_FLAG : 0);
        flags = flags | (hasDefaultParameters ? HAS_DEFAULT_PARAMETERS_FLAG : 0);
        flags = flags | (requiresActivationFrame ? REQUIRES_ACTIVATION_FRAME_FLAG : 0);
        flags = flags | (requiresArgumentObject ? REQUIRES_ARGUMENT_OBJECT_FLAG : 0);
        flags = flags | (declaredAsFunctionExpression ? DECLARED_AS_FUNCTION_EXPRESSION_FLAG : 0);
        flags = flags | (isClassConstructor ? CLASS_CONSTRUCTOR_FLAG : 0);
        flags = flags | ((kindFlags & DERIVED) != 0 ? DERIVED_CONSTRUCTOR_FLAG : 0);
        flags = flags | ((kindFlags & DEFAULT) != 0 ? DEFAULT_CONSTRUCTOR_FLAG : 0);
        flags = flags | ((kindFlags & ASYNC) != 0 ? ASYNC_FLAG : 0);
        flags = flags | ((kindFlags & ASYNC_GENERATOR) != 0 ? ASYNC_GENERATOR_FLAG : 0);
        this.flags = flags;

        this.sourceFile = sourceFile;
        this.sourceCodeProvider =
                sourceCodeProvider != null
                        ? sourceCodeProvider
                        : NullSourceCodeProvider.NULL_PROVIDER;
        this.sourceStart = sourceStart;
        this.sourceEnd = sourceEnd;
        this.name = name == null ? "" : name;
        this.languageVersion = languageVersion;
        this.paramAndVarCount = paramAndVarCount;
        this.paramCount = paramCount;
        this.arity = arity;
        this.functionType = functionType;
    }

    public JSCode<T> getCode() {
        return code;
    }

    public JSCode<T> getConstructor() {
        return constructor;
    }

    public List<JSDescriptor<JSFunction>> getNestedFunctions() {
        return nestedFunctions;
    }

    public boolean isStrict() {
        return (flags & IS_STRICT_FLAG) != 0;
    }

    public boolean isScript() {
        return (flags & IS_SCRIPT_FLAG) != 0;
    }

    @Override
    public boolean isTopLevel() {
        return (flags & IS_TOP_LEVEL_FLAG) != 0;
    }

    @Override
    public boolean isFunction() {
        return functionType != 0;
    }

    public boolean isES6Generator() {
        return (flags & IS_ES6_GENERATOR_FLAG) != 0;
    }

    public boolean isShorthand() {
        return (flags & IS_SHORTHAND_FLAG) != 0;
    }

    public boolean hasPrototype() {
        return (flags & HAS_PROTOTYPE_FLAG) != 0;
    }

    public boolean hasLexicalThis() {
        return (flags & HAS_LEXICAL_THIS_FLAG) != 0;
    }

    public boolean isEvalFunction() {
        return (flags & IS_EVAL_FUNCTION_FLAG) != 0;
    }

    /** Returns whether this is the constructor of a class. */
    public boolean isClassConstructor() {
        return (flags & CLASS_CONSTRUCTOR_FLAG) != 0;
    }

    /** Returns whether this is the constructor of a class with an extends clause. */
    public boolean isDerivedConstructor() {
        return (flags & DERIVED_CONSTRUCTOR_FLAG) != 0;
    }

    /** Returns whether this is a class constructor synthesized for a class body without one. */
    public boolean isDefaultConstructor() {
        return (flags & DEFAULT_CONSTRUCTOR_FLAG) != 0;
    }

    /** Returns whether this is an async generator function. */
    public boolean isAsyncGenerator() {
        return (flags & ASYNC_GENERATOR_FLAG) != 0;
    }

    /** Returns whether this is an async function, which includes async generators. */
    public boolean isAsync() {
        return (flags & ASYNC_FLAG) != 0;
    }

    public boolean hasRestArg() {
        return (flags & HAS_REST_ARG_FLAG) != 0;
    }

    @Override
    public String getSourceName() {
        return sourceFile;
    }

    public String getSource() {
        return sourceCodeProvider.getSource(this, sourceStart, sourceEnd);
    }

    // For testing purposes.
    SourceCodeProvider getSourceProvider() {
        return sourceCodeProvider;
    }

    void replaceSourceProvider(SourceCodeProvider newProvider) {
        sourceCodeProvider = newProvider;
    }

    public String getName() {
        return name;
    }

    public int getLanguageVersion() {
        return languageVersion;
    }

    @Override
    public int getParamAndVarCount() {
        return paramAndVarCount;
    }

    @Override
    public int getParamCount() {
        return paramCount;
    }

    public int getArity() {
        return arity;
    }

    public boolean getParamOrVarConst(int index) {
        return paramIsConst[index];
    }

    /** Returns true if any parameter or variable is a const. */
    public boolean hasConstParamOrVar() {
        return hasConstParamOrVar;
    }

    @Override
    public String getParamOrVarName(int index) {
        return paramAndVarNames[index];
    }

    public boolean hasDefaultParameters() {
        return (flags & HAS_DEFAULT_PARAMETERS_FLAG) != 0;
    }

    public boolean hasNoFunctionStatementNamed(String name) {
        for (int f = 0; f < getFunctionCount(); f++) {
            var functionData = getFunction(f);
            if (functionData.getFunctionType() == FunctionNode.FUNCTION_STATEMENT
                    && name.equals(functionData.getFunctionName())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getFunctionCount() {
        return (nestedFunctions == null) ? 0 : nestedFunctions.size();
    }

    @Override
    public JSDescriptor<JSFunction> getFunction(int index) {
        return nestedFunctions.get(index);
    }

    @Override
    public String getFunctionName() {
        return name;
    }

    @Override
    public boolean isGeneratedScript() {
        return ScriptRuntime.isGeneratedScript(sourceFile);
    }

    @Override
    public int[] getLineNumbers() {
        return Interpreter.getLineNumbers(this);
    }

    public boolean requiresActivationFrame() {
        return (flags & REQUIRES_ACTIVATION_FRAME_FLAG) != 0;
    }

    public boolean requiresArgumentObject() {
        return (flags & REQUIRES_ARGUMENT_OBJECT_FLAG) != 0;
    }

    public boolean declaredAsFunctionExpression() {
        return (flags & DECLARED_AS_FUNCTION_EXPRESSION_FLAG) != 0;
    }

    public int getFunctionType() {
        return functionType;
    }

    @Override
    public DebuggableScript getParent() {
        return parent;
    }

    /**
     * A mutable object for creating the final immutable {@link JSDescriptor}. This is populated
     * during the compilation process, and then {@link Builder#build(DescriptorConsumer)} is called
     * at the end of the process to gneeate the final objects.
     */
    public static class Builder<T extends ScriptOrFn<T>> {
        public JSCode.Builder<T> code;
        public JSCode.Builder<T> constructor;
        public Builder<?> parent;
        public final ArrayList<Builder<JSFunction>> nestedFunctions = new ArrayList<>();
        public String[] paramAndVarNames;
        public boolean[] paramIsConst;
        public boolean isStrict;
        public boolean isScript;
        public boolean isTopLevel;
        public boolean isES6Generator;
        public boolean isShorthand;
        public boolean hasPrototype;
        public boolean hasLexicalThis;
        public boolean isEvalFunction;
        public boolean hasRestArg;
        public String sourceFile;
        public SourceCodeProvider sourceCodeProvider;
        public int sourceStart;
        public int sourceEnd;
        public String name;
        public int languageVersion;
        public int paramAndVarCount;
        public int paramCount;
        public int arity; // number of params before first default for reporting as length
        public boolean hasDefaultParameters;
        public boolean requiresActivationFrame;
        public boolean requiresArgumentObject;
        public boolean declaredAsFunctionExpression;
        public boolean isClassConstructor;
        public boolean derivedConstructor;
        public boolean defaultConstructor;
        public boolean isAsync;
        public boolean isAsyncGenerator;
        public int functionType;

        public Builder() {}

        private Builder(Builder<?> parent) {
            this.parent = parent;
            this.languageVersion = parent.languageVersion;
            this.sourceCodeProvider = parent.sourceCodeProvider;
            this.sourceFile = parent.sourceFile;
            this.isStrict = parent.isStrict;
        }

        /**
         * Create a builder for the descriptor of a nested function. This will inherit language
         * version and other traits from its parent.
         */
        public Builder<JSFunction> createChildBuilder() {
            Builder<JSFunction> child = new Builder<>(this);
            nestedFunctions.add(child);
            return child;
        }

        /** Receives each {@link JSDescriptor} as it is built. */
        public interface DescriptorConsumer {
            void accept(JSDescriptor<?> descriptor);
        }

        /**
         * Build the final object, calling the consumer as each {@link JSDescriptor} is generated.
         */
        public JSDescriptor<T> build(DescriptorConsumer consumer) {
            if (parent != null) {
                throw new Error();
            }
            return build(null, consumer);
        }

        private JSDescriptor<T> build(JSDescriptor<?> parent, DescriptorConsumer consumer) {
            for (int i = 0; i < paramAndVarNames.length; i++) {
                paramAndVarNames[i] = paramAndVarNames[i].intern();
            }

            var result =
                    new JSDescriptor<T>(
                            code.build(),
                            constructor.build(),
                            parent,
                            paramAndVarNames,
                            paramIsConst,
                            isStrict,
                            isScript,
                            isTopLevel,
                            isES6Generator,
                            isShorthand,
                            hasPrototype,
                            hasLexicalThis,
                            isEvalFunction,
                            hasRestArg,
                            sourceFile,
                            sourceCodeProvider,
                            sourceStart,
                            sourceEnd,
                            name == null ? null : name.intern(),
                            languageVersion,
                            paramAndVarCount,
                            paramCount,
                            arity,
                            hasDefaultParameters,
                            requiresActivationFrame,
                            requiresArgumentObject,
                            declaredAsFunctionExpression,
                            kindFlags(),
                            functionType);
            consumer.accept(result);
            var nested = new ArrayList<JSDescriptor<JSFunction>>(nestedFunctions.size());
            for (var child : nestedFunctions) {
                nested.add(child.build(result, consumer));
            }
            result.nestedFunctions = Collections.unmodifiableList(nested);
            return result;
        }

        /** Returns the kindFlags argument of the descriptor constructor. */
        public int kindFlags() {
            return (isClassConstructor ? CLASS_CONSTRUCTOR : 0)
                    | (derivedConstructor ? DERIVED : 0)
                    | (defaultConstructor ? DEFAULT : 0)
                    | (isAsync ? ASYNC : 0)
                    | (isAsyncGenerator ? ASYNC_GENERATOR : 0);
        }

        public void setCode(JSCode.Builder<T> code) {
            this.code = (JSCode.Builder<T>) code;
        }
    }
}
