/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import java.util.ArrayList;

/**
 * Runtime semantics of ES2015+ classes. IRFactory lowers a class to calls of these helpers through
 * {@link Intrinsics}, so that the interpreter and the compiler share them.
 */
final class ClassRuntime {

    static final int FLAG_STATIC = 1, FLAG_GETTER = 2, FLAG_SETTER = 4, FLAG_HAS_HERITAGE = 8;

    private ClassRuntime() {}

    /** State of a class, held by its constructor function. */
    static final class ClassData {
        final Scriptable prototype;
        // instance fields: pairs of key (property key or PrivateName) and initializer or null
        private final ArrayList<Object> instanceFields = new ArrayList<>();
        private final ArrayList<PrivateName> instancePrivateMethods = new ArrayList<>();
        // static fields (as for instance fields) and static blocks (null key), in source order,
        // run and cleared once the class is defined
        private ArrayList<Object> staticElements = new ArrayList<>();
        private final ArrayList<PrivateName> staticPrivateMethods = new ArrayList<>();

        ClassData(Scriptable prototype) {
            this.prototype = prototype;
        }
    }

    /**
     * A private name, such as {@code #x}. Every evaluation of a class creates its own. Methods and
     * accessors are held by the name itself, while objects hold the values of private fields and
     * a mark for the private methods they have been branded with.
     */
    static final class PrivateName {
        static final int FIELD = 0, METHOD = 1, ACCESSOR = 2;

        final String description;
        int kind = FIELD;
        Object method;
        Callable getter;
        Callable setter;

        PrivateName(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    // Class definition

    static JSFunction createClass(
            Context cx, VarScope scope, Object heritage, Object ctor, int flags, String name) {
        JSFunction f = (JSFunction) ctor;
        Scriptable protoParent = ScriptableObject.getObjectPrototype(scope);
        Scriptable ctorParent = ScriptableObject.getFunctionPrototype(scope);
        if ((flags & FLAG_HAS_HERITAGE) != 0) {
            if (heritage == null) {
                protoParent = null;
            } else {
                if (heritage instanceof NativeJavaClass
                        || heritage instanceof NativeJavaMethod
                        || heritage instanceof NativeJavaConstructor
                        || heritage instanceof JavaAdapter.AdapterConstructor) {
                    throw ScriptRuntime.typeErrorById("msg.class.extends.java", describe(heritage));
                }
                if (!AbstractEcmaObjectOperations.isConstructor(cx, heritage)) {
                    throw ScriptRuntime.typeErrorById(
                            "msg.class.extends.not.ctor", describe(heritage));
                }
                Scriptable parent = (Scriptable) heritage;
                Object protoValue = ScriptableObject.getProperty(parent, "prototype");
                if (protoValue == null) {
                    protoParent = null;
                } else if (protoValue instanceof Scriptable
                        && !Undefined.isUndefined(protoValue)) {
                    protoParent = (Scriptable) protoValue;
                } else {
                    throw ScriptRuntime.typeErrorById(
                            "msg.class.extends.proto", ScriptRuntime.toString(protoValue));
                }
                ctorParent = parent;
            }
        }

        NativeObject proto = new NativeObject();
        proto.setParentScope(f.getParentScope());
        proto.setPrototype(protoParent);
        proto.defineProperty("constructor", f, ScriptableObject.DONTENUM);

        f.setPrototype(ctorParent);
        f.setPrototypeProperty(proto);
        f.setPrototypePropertyAttributes(
                ScriptableObject.DONTENUM | ScriptableObject.READONLY | ScriptableObject.PERMANENT);
        f.initHomeObject(proto);
        f.setClassData(new ClassData(proto));
        if (name != null) {
            f.setFunctionName(name);
        }
        return f;
    }

    private static ClassData classData(Object f) {
        return ((JSFunction) f).getClassData();
    }

    static void defineMethod(Context cx, Object f, int flags, Object key, Object method) {
        JSFunction fn = (JSFunction) method;
        Scriptable target = (flags & FLAG_STATIC) != 0 ? (Scriptable) f : classData(f).prototype;
        Object propKey = toPropertyKey(key);
        fn.initHomeObject(target);
        boolean isGetter = (flags & FLAG_GETTER) != 0, isSetter = (flags & FLAG_SETTER) != 0;
        fn.setFunctionName(functionName(propKey, isGetter ? "get " : isSetter ? "set " : ""));

        ScriptableObject.DescriptorInfo desc;
        Object notFound = Scriptable.NOT_FOUND;
        if (isGetter) {
            desc = new ScriptableObject.DescriptorInfo(Boolean.FALSE, notFound, Boolean.TRUE, fn, notFound, notFound);
        } else if (isSetter) {
            desc = new ScriptableObject.DescriptorInfo(Boolean.FALSE, notFound, Boolean.TRUE, notFound, fn, notFound);
        } else {
            desc = new ScriptableObject.DescriptorInfo(false, true, true, fn);
        }
        ((ScriptableObject) target).defineOwnProperty(cx, propKey, desc);
    }

    static void definePrivateMethod(Object f, int flags, Object name, Object method) {
        PrivateName pn = (PrivateName) name;
        JSFunction fn = (JSFunction) method;
        boolean isStatic = (flags & FLAG_STATIC) != 0;
        fn.initHomeObject(isStatic ? (Scriptable) f : classData(f).prototype);
        if ((flags & FLAG_GETTER) != 0) {
            pn.kind = PrivateName.ACCESSOR;
            pn.getter = fn;
            fn.setFunctionName("get " + pn.description);
        } else if ((flags & FLAG_SETTER) != 0) {
            pn.kind = PrivateName.ACCESSOR;
            pn.setter = fn;
            fn.setFunctionName("set " + pn.description);
        } else {
            pn.kind = PrivateName.METHOD;
            pn.method = fn;
            fn.setFunctionName(pn.description);
        }
        ArrayList<PrivateName> list =
                isStatic ? classData(f).staticPrivateMethods : classData(f).instancePrivateMethods;
        if (!list.contains(pn)) {
            list.add(pn);
        }
    }

    static void defineField(Object f, int flags, Object key, Object initializer) {
        ClassData data = classData(f);
        Object fieldKey = key instanceof PrivateName ? key : toPropertyKey(key);
        JSFunction init = initializer instanceof JSFunction ? (JSFunction) initializer : null;
        boolean isStatic = (flags & FLAG_STATIC) != 0;
        if (init != null) {
            init.initHomeObject(isStatic ? (Scriptable) f : data.prototype);
        }
        ArrayList<Object> list = isStatic ? data.staticElements : data.instanceFields;
        list.add(fieldKey);
        list.add(init);
    }

    static void defineStaticBlock(Object f, Object block) {
        JSFunction fn = (JSFunction) block;
        fn.initHomeObject((Scriptable) f);
        ArrayList<Object> list = classData(f).staticElements;
        list.add(null);
        list.add(fn);
    }

    /** Runs the static elements of a class whose definition is otherwise complete. */
    static Object finishClass(Context cx, VarScope scope, Object f) {
        JSFunction fn = (JSFunction) f;
        ClassData data = fn.getClassData();
        for (PrivateName pn : data.staticPrivateMethods) {
            privateAdd(fn, pn, pn);
        }
        ArrayList<Object> elements = data.staticElements;
        data.staticElements = null;
        for (int i = 0; i < elements.size(); i += 2) {
            Object key = elements.get(i);
            Callable init = (Callable) elements.get(i + 1);
            if (key == null) {
                init.call(cx, scope, fn, ScriptRuntime.emptyArgs);
            } else {
                defineField(cx, scope, fn, key, init);
            }
        }
        return fn;
    }

    // Instances

    /**
     * Adds the private methods and fields of class {@code f} to a newly constructed instance, as in
     * InitializeInstanceElements.
     */
    static void initializeInstance(Context cx, VarScope scope, Scriptable obj, JSFunction f) {
        ClassData data = f.getClassData();
        if (data == null) {
            return;
        }
        ArrayList<PrivateName> methods = data.instancePrivateMethods;
        for (int i = 0; i < methods.size(); i++) {
            PrivateName pn = methods.get(i);
            privateAdd(obj, pn, pn);
        }
        ArrayList<Object> fields = data.instanceFields;
        for (int i = 0; i < fields.size(); i += 2) {
            defineField(cx, scope, obj, fields.get(i), (Callable) fields.get(i + 1));
        }
    }

    private static void defineField(
            Context cx, VarScope scope, Scriptable obj, Object key, Callable init) {
        Object value =
                init == null
                        ? Undefined.instance
                        : init.call(cx, scope, obj, ScriptRuntime.emptyArgs);
        if (key instanceof PrivateName) {
            privateAdd(obj, (PrivateName) key, value);
            return;
        }
        if (!(obj instanceof ScriptableObject)) {
            throw ScriptRuntime.typeErrorById("msg.class.bad.object", ScriptRuntime.toString(key));
        }
        ScriptableObject so = (ScriptableObject) obj;
        so.defineOwnProperty(cx, key, new ScriptableObject.DescriptorInfo(true, true, true, value));
    }

    /** [[Construct]] of the constructor of a class with an extends clause and no constructor. */
    static Scriptable constructDefaultDerived(
            Context cx, VarScope scope, JSFunction f, Object newTarget, Object[] args) {
        Scriptable result = constructSuper(cx, scope, f.getPrototype(), newTarget, args);
        initializeInstance(cx, scope, result, f);
        return result;
    }

    private static Scriptable constructSuper(
            Context cx, VarScope scope, Object superCtor, Object newTarget, Object[] args) {
        if (!AbstractEcmaObjectOperations.isConstructor(cx, superCtor)) {
            throw ScriptRuntime.typeErrorById("msg.not.ctor", describe(superCtor));
        }
        return ((Constructable) superCtor).construct(cx, newTarget, scope, args);
    }

    // Derived constructors, whose this binding IRFactory keeps in a variable

    /**
     * {@code super(...args)} in the constructor of class {@code f}, returning the new this. The
     * caller binds it and then initializes the instance elements of {@code f}.
     */
    static Object superCall(
            Context cx, VarScope scope, Object f, Object newTarget, Object args, Object thisValue) {
        JSFunction fn = (JSFunction) f;
        Object[] argArray = ScriptRuntime.getArrayElements(cx, (Scriptable) args);
        Scriptable result = constructSuper(cx, scope, fn.getPrototype(), newTarget, argArray);
        if (!Undefined.isUndefined(thisValue)) {
            throw ScriptRuntime.constructError(
                    "ReferenceError", ScriptRuntime.getMessageById("msg.class.super.twice"));
        }
        return result;
    }

    static Object checkThis(Object thisValue) {
        if (Undefined.isUndefined(thisValue)) {
            throw ScriptRuntime.constructError(
                    "ReferenceError", ScriptRuntime.getMessageById("msg.class.this.uninit"));
        }
        return thisValue;
    }

    // What a derived constructor returns for a value that is neither an object nor undefined
    private static final Object INVALID_DERIVED_RESULT = new Object();

    /**
     * The value a derived constructor returns for {@code return value}. It is only checked once the
     * body completes, by {@link #derivedResult}: returning from a try block must not throw there.
     */
    static Object derivedReturn(Object value, Object thisValue) {
        if (value instanceof Scriptable && !Undefined.isUndefined(value)) {
            return value;
        }
        return Undefined.isUndefined(value) ? thisValue : INVALID_DERIVED_RESULT;
    }

    /** The result of [[Construct]] for what the body of a derived constructor returned. */
    static Scriptable derivedResult(Object result) {
        if (result == INVALID_DERIVED_RESULT) {
            throw ScriptRuntime.typeErrorById("msg.class.derived.return");
        }
        return (Scriptable) checkThis(result);
    }

    static Object superGet(Context cx, VarScope scope, Object thisValue, Object home, Object key) {
        return ScriptRuntime.getSuperElem(home, key, cx, scope, thisValue);
    }

    static Object superSet(
            Context cx, VarScope scope, Object thisValue, Object home, Object key, Object value) {
        ScriptRuntime.setSuperElem(home, key, value, cx, scope, thisValue);
        return value;
    }

    static Object superCallMethod(
            Context cx, VarScope scope, Object thisValue, Object home, Object key, Object args) {
        Object fn = superGet(cx, scope, thisValue, home, key);
        if (!(fn instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(fn, key);
        }
        Object[] argArray = ScriptRuntime.getArrayElements(cx, (Scriptable) args);
        return ((Callable) fn).call(cx, scope, thisValue, argArray);
    }

    static Ref superRef(Object thisValue, Object home, Object key) {
        return new SuperRef(thisValue, home, key);
    }

    private static final class SuperRef extends Ref {
        private static final long serialVersionUID = 1L;
        private final Object thisValue;
        private final Object home;
        private final Object key;

        SuperRef(Object thisValue, Object home, Object key) {
            this.thisValue = thisValue;
            this.home = home;
            this.key = key;
        }

        @Override
        public Object get(Context cx) {
            return superGet(cx, ScriptRuntime.getTopCallScope(cx), thisValue, home, key);
        }

        @Override
        @Deprecated
        public Object set(Context cx, Object value) {
            return set(cx, ScriptRuntime.getTopCallScope(cx), value);
        }

        @Override
        public Object set(Context cx, VarScope scope, Object value) {
            return superSet(cx, scope, thisValue, home, key, value);
        }
    }

    // Private names

    private static ScriptableObject privateHolder(Object obj, PrivateName name) {
        if (obj instanceof ScriptableObject) {
            return (ScriptableObject) obj;
        }
        throw ScriptRuntime.typeErrorById("msg.class.private.missing", name.description);
    }

    private static void privateAdd(Object obj, PrivateName name, Object value) {
        if (!(obj instanceof ScriptableObject)) {
            throw ScriptRuntime.typeErrorById("msg.class.bad.object", name.description);
        }
        if (!((ScriptableObject) obj).isExtensible()) {
            throw ScriptRuntime.typeErrorById("msg.class.bad.object", name.description);
        }
        if (!((ScriptableObject) obj).addPrivateElement(name, value)) {
            throw ScriptRuntime.typeErrorById("msg.class.private.twice", name.description);
        }
    }

    static Object privateGet(Context cx, VarScope scope, Object obj, Object name) {
        PrivateName pn = (PrivateName) name;
        Object value = privateHolder(obj, pn).getPrivateElement(pn);
        if (value == Scriptable.NOT_FOUND) {
            throw ScriptRuntime.typeErrorById("msg.class.private.missing", pn.description);
        }
        switch (pn.kind) {
            case PrivateName.METHOD:
                return pn.method;
            case PrivateName.ACCESSOR:
                if (pn.getter == null) {
                    throw ScriptRuntime.typeErrorById(
                            "msg.class.private.no.getter", pn.description);
                }
                return pn.getter.call(cx, scope, (Scriptable) obj, ScriptRuntime.emptyArgs);
            default:
                return value;
        }
    }

    static Object privateSet(Context cx, VarScope scope, Object obj, Object name, Object value) {
        PrivateName pn = (PrivateName) name;
        ScriptableObject holder = privateHolder(obj, pn);
        switch (pn.kind) {
            case PrivateName.METHOD:
                if (holder.getPrivateElement(pn) == Scriptable.NOT_FOUND) {
                    throw ScriptRuntime.typeErrorById("msg.class.private.missing", pn.description);
                }
                throw ScriptRuntime.typeErrorById(
                        "msg.class.private.method.write", pn.description);
            case PrivateName.ACCESSOR:
                if (holder.getPrivateElement(pn) == Scriptable.NOT_FOUND) {
                    throw ScriptRuntime.typeErrorById("msg.class.private.missing", pn.description);
                }
                if (pn.setter == null) {
                    throw ScriptRuntime.typeErrorById(
                            "msg.class.private.no.setter", pn.description);
                }
                pn.setter.call(cx, scope, holder, new Object[] {value});
                return value;
            default:
                if (!holder.setPrivateElement(pn, value)) {
                    throw ScriptRuntime.typeErrorById("msg.class.private.missing", pn.description);
                }
                return value;
        }
    }

    static Object privateCall(Context cx, VarScope scope, Object obj, Object name, Object args) {
        Object fn = privateGet(cx, scope, obj, name);
        if (!(fn instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(fn, name.toString());
        }
        Object[] argArray = ScriptRuntime.getArrayElements(cx, (Scriptable) args);
        return ((Callable) fn).call(cx, scope, obj, argArray);
    }

    static Object privateIn(Object name, Object obj) {
        PrivateName pn = (PrivateName) name;
        if (!(obj instanceof Scriptable) || Undefined.isUndefined(obj)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.class.private.in.primitive",
                    pn.description,
                    ScriptRuntime.toString(obj));
        }
        return obj instanceof ScriptableObject
                && ((ScriptableObject) obj).getPrivateElement(pn) != Scriptable.NOT_FOUND;
    }

    static Ref privateRef(Object obj, Object name) {
        return new PrivateRef(obj, (PrivateName) name);
    }

    private static final class PrivateRef extends Ref {
        private static final long serialVersionUID = 1L;
        private final Object obj;
        private final PrivateName name;

        PrivateRef(Object obj, PrivateName name) {
            this.obj = obj;
            this.name = name;
        }

        @Override
        public Object get(Context cx) {
            return privateGet(cx, ScriptRuntime.getTopCallScope(cx), obj, name);
        }

        @Override
        @Deprecated
        public Object set(Context cx, Object value) {
            return set(cx, ScriptRuntime.getTopCallScope(cx), value);
        }

        @Override
        public Object set(Context cx, VarScope scope, Object value) {
            return privateSet(cx, scope, obj, name, value);
        }
    }

    // Helpers

    /** Describes a value in an error message, without the source text of functions. */
    private static String describe(Object value) {
        if (value instanceof NativeJavaClass) {
            return ((NativeJavaClass) value).getClassObject().getName();
        }
        if (value instanceof BaseFunction) {
            String name = ((BaseFunction) value).getFunctionName();
            return name == null || name.isEmpty() ? "function" : name;
        }
        return value instanceof Scriptable ? "object" : ScriptRuntime.toString(value);
    }

    private static Object toPropertyKey(Object key) {
        if (key instanceof Symbol) {
            return key;
        }
        Object prim = ScriptRuntime.toPrimitive(key, String.class);
        if (prim instanceof Symbol) {
            return prim;
        }
        return ScriptRuntime.toString(prim);
    }

    private static String functionName(Object propKey, String prefix) {
        if (propKey instanceof Symbol) {
            String description = ((Symbol) propKey).getName();
            return description.isEmpty() ? prefix : prefix + "[" + description + "]";
        }
        return prefix + propKey;
    }
}
