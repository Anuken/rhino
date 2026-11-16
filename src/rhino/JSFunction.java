package rhino;

import rhino.debug.DebuggableScript;

import java.util.EnumSet;

/**
 * Represents a JavaScript function built upon a a {@link JSDescriptor}. All the immutable metadata
 * associated with the function is stored on the descriptor, with only the lexically bound this, the
 * home object required for super calls, and mutable properties held on the function object itself.
 */
public class JSFunction extends BaseFunction implements ScriptOrFn<JSFunction> {
    private final JSDescriptor<JSFunction> descriptor;
    private final Object lexicalThis;
    private final Object lexicalNewTarget;
    // Only a class binds its methods' home objects after creating them, see initHomeObject
    private Scriptable homeObject;
    private ClassRuntime.ClassData classData;
    // top level scope of topLevelFor, the declaration scope it was looked up from
    private transient TopLevel topLevel;
    private transient VarScope topLevelFor;

    public JSFunction(
            Context cx,
            VarScope scope,
            JSDescriptor<JSFunction> descriptor,
            Object lexicalThis,
            Object lexicalNewTarget,
            Scriptable homeObject) {
        super(cx, scope, descriptor.hasPrototype());
        this.descriptor = descriptor;
        this.lexicalThis = lexicalThis;
        this.lexicalNewTarget = lexicalNewTarget;
        this.homeObject = homeObject;
        if (descriptor.isES6Generator() && !descriptor.isAsync()) {
            setPrototype(ScriptableObject.getGeneratorFunctionPrototype(scope));
        }
        if (descriptor.hasPrototype()) {
            setupLazyDefaultPrototype();
        }
        if (descriptor.isAsyncGenerator()) {
            TopLevel top = ScriptableObject.getTopLevelScope(scope);
            setPrototype(
                    TopLevel.getBuiltinPrototype(top, TopLevel.Builtins.AsyncGeneratorFunction));
            if (descriptor.hasPrototype()) {
                // The prototype of the async generator objects, without "constructor"
                Scriptable proto = (Scriptable) getPrototypeProperty();
                proto.setPrototype(NativeAsyncGenerator.getAsyncGeneratorPrototype(top));
                proto.delete("constructor");
            }
        } else if (descriptor.isAsync()) {
            setPrototype(
                    TopLevel.getBuiltinPrototype(
                            ScriptableObject.getTopLevelScope(scope),
                            TopLevel.Builtins.AsyncFunction));
        }
    }

    public JSFunction(
            VarScope scope,
            JSDescriptor<JSFunction> descriptor,
            Scriptable lexicalThis,
            Object lexicalNewTarget,
            Scriptable homeObject) {
        super(scope);
        this.descriptor = descriptor;
        this.lexicalThis = lexicalThis;
        this.lexicalNewTarget = lexicalNewTarget;
        this.homeObject = homeObject;
        setParentScope(scope);
        setPrototype(ScriptableObject.getFunctionPrototype(scope));
    }

    @Override
    protected void setPrototypeProperty(Object prototype) {
        super.setPrototypeProperty(prototype);
    }

    @Override
    public VarScope getDeclarationScope() {
        return this.getParentScope();
    }

    @Override
    public JSDescriptor<JSFunction> getDescriptor() {
        return descriptor;
    }

    @Override
    final String decompile(int indent, EnumSet<DecompilerFlag> flags) {
        return descriptor.getSource();
    }

    public boolean isShorthand() {
        return descriptor.isShorthand();
    }

    public boolean isStrict() {
        return descriptor.isStrict();
    }

    @Override
    public int getArity() {
        return descriptor.getArity();
    }

    public DebuggableScript getDebuggableView() {
        return null;
    }

    protected int getLanguageVersion() {
        return descriptor.getLanguageVersion();
    }

    @Override
    protected boolean isGeneratorFunction() {
        return descriptor.isES6Generator() && !descriptor.isAsync();
    }

    @Override
    public int getLength() {
        return descriptor.getArity();
    }

    protected int getParamAndVarCount() {
        return descriptor.getParamAndVarCount();
    }

    protected int getParamCount() {
        int count = descriptor.getParamCount();
        if (descriptor.hasRestArg()) {
            return count - 1;
        }
        return count;
    }

    protected boolean getParamOrVarConst(int index) {
        return descriptor.getParamOrVarConst(index);
    }

    protected String getParamOrVarName(int index) {
        return descriptor.getParamOrVarName(index);
    }

    public String getRawSource() {
        return descriptor.getSource();
    }

    @Override
    protected void createPrototypeProperty() {
        if (descriptor.hasPrototype()) {
            super.createPrototypeProperty();
        }
    }

    JSCode<JSFunction> getCode() {
        return descriptor.getCode();
    }

    JSCode<JSFunction> getConstructor() {
        return descriptor.getConstructor();
    }

    /** Sets the home object of a class constructor or member while the class is defined. */
    void initHomeObject(Scriptable homeObject) {
        this.homeObject = homeObject;
    }

    /** Returns the class data of a class constructor, or null. */
    ClassRuntime.ClassData getClassData() {
        return classData;
    }

    void setClassData(ClassRuntime.ClassData classData) {
        this.classData = classData;
    }

    @Override
    public Object call(Context cx, Object nt, VarScope scope, Object thisObj, Object[] args) {
        if (!ScriptRuntime.hasTopCall(cx)) {
            return ScriptRuntime.doTopCall(this, cx, scope, thisObj, args, isStrict());
        }
        var realThis = getThisObj(thisObj);
        return descriptor.getCode().execute(cx, this, lexicalNewTarget, scope, realThis, args);
    }

    public final Object getThisObj(Object thisObj) {
        if (descriptor.hasLexicalThis()) {
            return lexicalThis;
        } else if (!descriptor.isStrict()) {
            if (thisObj instanceof ScriptableObject) {
                // the common case, as a class check is cheaper than the interface check below
                return thisObj;
            } else if (thisObj == null || Undefined.isUndefined(thisObj)) {
                return getTopLevel().getGlobalThis();
            } else {
                return ScriptRuntime.toObject(getDeclarationScope(), thisObj);
            }
        } else {
            return thisObj;
        }
    }

    private TopLevel getTopLevel() {
        VarScope scope = getDeclarationScope();
        TopLevel top = topLevel;
        if (top == null || topLevelFor != scope) {
            top = ScriptableObject.getTopLevelScope(scope);
            topLevel = top;
            topLevelFor = scope;
        }
        return top;
    }

    @Override
    public Scriptable construct(Context cx, Object nt, VarScope s, Object[] args) {
        if (!ScriptRuntime.hasTopCall(cx)) {
            return (Scriptable)
                    ScriptRuntime.doTopCall(
                            (lcx, lscope, lthisObj, largs) -> construct(lcx, lscope, largs),
                            cx,
                            s,
                            null,
                            args,
                            isStrict());
        }
        if (descriptor.getConstructor() == null) {
            throw ScriptRuntime.typeErrorById("msg.not.ctor", getFunctionName());
        }
        if (nt == null || Undefined.isUndefined(nt)) {
            nt = this;
        }

        if (descriptor.isClassConstructor()) {
            return constructClass(cx, nt, s, args);
        }

        // built-in constructors create their result themselves
        var thisObj =
                homeObject == null && !ClassDescriptor.isBuiltIn(descriptor.getConstructor())
                        ? this.createObject(cx, s, nt)
                        : null;

        // Pass `this` in as new.target for now. This can change when
        // the public `construct` signature changes.
        var res = descriptor.getConstructor().execute(cx, this, nt, s, thisObj, args);
        if (res instanceof Scriptable) {
            thisObj = (Scriptable) res;
        }
        return (Scriptable) thisObj;
    }

    private Scriptable constructClass(Context cx, Object nt, VarScope s, Object[] args) {
        if (descriptor.isDerivedConstructor()) {
            if (descriptor.isDefaultConstructor()) {
                return ClassRuntime.constructDefaultDerived(cx, s, this, nt, args);
            }
            // The body binds this with super() and returns it, see IRFactory
            return ClassRuntime.derivedResult(
                    descriptor.getConstructor().execute(cx, this, nt, s, null, args));
        }
        Scriptable thisObj = createObject(cx, s, nt);
        ClassRuntime.initializeInstance(cx, s, thisObj, this);
        var res = descriptor.getConstructor().execute(cx, this, nt, s, thisObj, args);
        return res instanceof Scriptable && !Undefined.isUndefined(res)
                ? (Scriptable) res
                : thisObj;
    }

    public boolean isScript() {
        return descriptor.isScript();
    }

    @Override
    protected boolean hasDefaultParameters() {
        return descriptor.hasDefaultParameters();
    }

    public boolean hasNoFunctionStatementNamed(String name) {
        return descriptor.hasNoFunctionStatementNamed(name);
    }

    @Override
    public String getFunctionName() {
        return descriptor.getName();
    }

    public Object resumeGenerator(
            Context cx, VarScope scope, int operation, Object state, Object value) {
        return descriptor.getCode().resume(cx, this, state, scope, operation, value);
    }

    @Override
    public Scriptable getHomeObject() {
        return homeObject;
    }

    public Object getLexicalNewTarget() {
        return lexicalNewTarget;
    }

    @Override
    public void setHomeObject(Scriptable homeObject) {
        throw new UnsupportedOperationException("Cannot set home object on JS function.");
    }

    public Object getFunctionThis(Scriptable functionThis) {
        if (descriptor.hasLexicalThis()) {
            return this.lexicalThis;
        } else {
            return functionThis;
        }
    }

    /** Create script from compiled bytecode. */
    public static JSScript createScript(JSDescriptor<JSScript> desc, Scriptable homeObject) {
        assert desc.isScript();
        return new JSScript(desc, homeObject);
    }

    /** Create function compiled from Function(...) constructor. */
    public static JSFunction createFunction(
            Context cx, VarScope scope, JSDescriptor<JSFunction> desc, Scriptable homeObject) {
        JSFunction f = new JSFunction(cx, scope, desc, null, Undefined.instance, homeObject);
        return f;
    }

    /** Create function embedded in script or another function. */
    static JSFunction createFunction(
            Context cx, VarScope scope, JSDescriptor<?> parent, int index, Scriptable homeObject) {
        JSDescriptor<JSFunction> desc = parent.getFunction(index);
        JSFunction f = new JSFunction(cx, scope, desc, null, Undefined.instance, homeObject);
        return f;
    }

    @Override
    public boolean isConstructor() {
        return descriptor.getConstructor() != null;
    }
}
