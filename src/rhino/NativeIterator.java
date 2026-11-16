/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import static rhino.ClassDescriptor.Destination.CTOR;
import static rhino.ClassDescriptor.Destination.PROTO;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * The global Iterator: the ES2025 abstract constructor and %Iterator.prototype% with its helper
 * methods, or for language versions before ES6, the legacy JavaScript 1.7 <a
 * href="http://developer.mozilla.org/en/docs/New_in_JavaScript_1.7#Iterators">Iterator</a>, which
 * this class implements.
 *
 * @author Norris Boyd
 */
public final class NativeIterator extends ScriptableObject {
    @Serial private static final long serialVersionUID = -4136968203581667681L;
    private static final Object ITERATOR_TAG = "Iterator";
    private static final String CLASS_NAME = "Iterator";

    private Object objectIterator;

    private static final String STOP_ITERATION = "StopIteration";
    public static final String ITERATOR_PROPERTY_NAME = "__iterator__";

    private static final ClassDescriptor DESCRIPTOR;

    private static final ClassDescriptor STOP_ITER_DESCRIPTOR;

    private static final Object ITERATOR_PROTOTYPE_KEY = "IteratorPrototype";
    private static final ClassDescriptor ES_DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                2,
                                NativeIterator::jsConstructorCall,
                                NativeIterator::jsConstructor)
                        .withMethod(PROTO, "next", 0, NativeIterator::js_next)
                        .withMethod(
                                PROTO, ITERATOR_PROPERTY_NAME, 1, NativeIterator::js_iteratorMethod)
                        .build();
        STOP_ITER_DESCRIPTOR = new ClassDescriptor.Builder(STOP_ITERATION, 0, null, null).build();
        ES_DESCRIPTOR =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                0,
                                ClassDescriptor.typeError(),
                                NativeIterator::js_esConstructor,
                                DONTENUM,
                                DONTENUM | READONLY | PERMANENT)
                        .withMethod(CTOR, "from", 1, NativeIterator::js_from)
                        .withMethod(CTOR, "concat", 0, NativeIterator::js_concat)
                        .withMethod(PROTO, "map", 1, NativeIterator::js_map)
                        .withMethod(PROTO, "filter", 1, NativeIterator::js_filter)
                        .withMethod(PROTO, "take", 1, NativeIterator::js_take)
                        .withMethod(PROTO, "drop", 1, NativeIterator::js_drop)
                        .withMethod(PROTO, "flatMap", 1, NativeIterator::js_flatMap)
                        .withMethod(PROTO, "reduce", 1, NativeIterator::js_reduce)
                        .withMethod(PROTO, "toArray", 0, NativeIterator::js_toArray)
                        .withMethod(PROTO, "forEach", 1, NativeIterator::js_forEach)
                        .withMethod(PROTO, "some", 1, NativeIterator::js_some)
                        .withMethod(PROTO, "every", 1, NativeIterator::js_every)
                        .withMethod(PROTO, "find", 1, NativeIterator::js_find)
                        .withMethod(PROTO, SymbolKey.ITERATOR, 0, NativeIterator::js_esIterator)
                        .withProp(
                                PROTO,
                                "constructor",
                                (cx, scope, proto) ->
                                        weirdAccessor(
                                                scope,
                                                proto,
                                                "constructor",
                                                proto.get("constructor", proto)))
                        .withProp(
                                PROTO,
                                SymbolKey.TO_STRING_TAG,
                                (cx, scope, proto) ->
                                        weirdAccessor(
                                                scope, proto, SymbolKey.TO_STRING_TAG, CLASS_NAME))
                        .build();
    }

    static void init(Context cx, TopLevel scope, boolean sealed) {
        NativeObject iteratorProto = new NativeObject();
        ES_DESCRIPTOR.buildConstructor(cx, scope, iteratorProto, sealed);
        scope.associateValue(ITERATOR_PROTOTYPE_KEY, iteratorProto);
        NativeIteratorHelper.init(cx, scope, iteratorProto, sealed);
        WrapForValidIterator.init(cx, scope, iteratorProto, sealed);

        if (cx.getLanguageVersion() < Context.VERSION_ES6) {
            DESCRIPTOR.buildConstructor(cx, scope, new NativeObject(), sealed);
        }

        // Generator
        if (cx.getLanguageVersion() >= Context.VERSION_ES6) {
            ES6Generator.init(cx, scope, sealed);
        } else {
            NativeGenerator.init(cx, scope, sealed);
        }

        // StopIteration
        var stopCtor = STOP_ITER_DESCRIPTOR.populateGlobal(cx, scope, new StopIteration(), sealed);
        // Use "associateValue" so that generators can continue to
        // throw StopIteration even if the property of the global
        // scope is replaced or deleted.
        scope.associateValue(ITERATOR_TAG, stopCtor);
    }

    /** Only for constructing the prototype object. */
    private NativeIterator() {}

    private NativeIterator(Object objectIterator) {
        this.objectIterator = objectIterator;
    }

    /**
     * Get the value of the "StopIteration" object. Note that this value is stored in the top-level
     * scope using "associateValue" so the value can still be found even if a script overwrites or
     * deletes the global "StopIteration" property.
     *
     * @param scope a scope whose parent chain reaches a top-level scope
     * @return the StopIteration object
     */
    public static Object getStopIterationObject(VarScope scope) {
        TopLevel top = ScriptableObject.getTopLevelScope(scope);
        return ScriptableObject.getTopScopeValue(top, ITERATOR_TAG);
    }

    public static class StopIteration extends NativeObject {
        @Serial private static final long serialVersionUID = 2485151085722377663L;

        private Object value = Undefined.instance;

        public StopIteration() {}

        public StopIteration(Object val) {
            this.value = val;
        }

        public Object getValue() {
            return value;
        }

        @Override
        public String getClassName() {
            return STOP_ITERATION;
        }

        /* StopIteration has custom instanceof behavior since it
         * doesn't have a constructor.
         */
        @Override
        public boolean hasInstance(Scriptable instance) {
            return instance instanceof StopIteration;
        }
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    private static Object jsConstructorCall(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable target = requireIteratorTarget(cx, s, args);
        boolean keyOnly = isKeyOnly(args);

        Iterator<?> iterator = getJavaIterator(target);
        if (iterator != null) {
            VarScope topScope = ScriptableObject.getTopLevelScope(s);
            return cx.getWrapFactory()
                    .wrap(
                            cx,
                            topScope,
                            new WrappedJavaIterator(iterator, topScope),
                            WrappedJavaIterator.class);
        }

        Scriptable jsIterator = ScriptRuntime.toIterator(cx, target, keyOnly);
        if (jsIterator != null) {
            return jsIterator;
        }

        return createNativeIterator(cx, s, target, keyOnly);
    }

    private static Scriptable jsConstructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable target = requireIteratorTarget(cx, s, args);
        boolean keyOnly = isKeyOnly(args);
        return createNativeIterator(cx, s, target, keyOnly);
    }

    private static Scriptable requireIteratorTarget(Context cx, VarScope scope, Object[] args) {
        if (args.length == 0 || args[0] == null || args[0] == Undefined.instance) {
            Object argument = args.length == 0 ? Undefined.instance : args[0];
            throw ScriptRuntime.typeErrorById(
                    "msg.no.properties", ScriptRuntime.toString(argument));
        }
        return ScriptRuntime.toObject(cx, scope, args[0]);
    }

    private static boolean isKeyOnly(Object[] args) {
        return args.length > 1 && ScriptRuntime.toBoolean(args[1]);
    }

    private static Object js_next(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeIterator iterator = realThis(thisObj);
        return iterator.next(cx, s);
    }

    private static NativeIterator createNativeIterator(
            Context cx, VarScope scope, Scriptable obj, boolean keyOnly) {
        Object objectIterator =
                ScriptRuntime.enumInit(
                        obj,
                        cx,
                        scope,
                        keyOnly
                                ? ScriptRuntime.ENUMERATE_KEYS_NO_ITERATOR
                                : ScriptRuntime.ENUMERATE_ARRAY_NO_ITERATOR);
        ScriptRuntime.setEnumNumbers(objectIterator, true);
        NativeIterator result = new NativeIterator(objectIterator);
        result.setPrototype(ScriptableObject.getClassPrototype(scope, CLASS_NAME));
        result.setParentScope(scope);
        return result;
    }

    private static Object js_iteratorMethod(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return realThis(thisObj);
    }

    private static NativeIterator realThis(Object thisObj) {
        return LambdaConstructor.convertThisObject(thisObj, NativeIterator.class);
    }

    private Object next(Context cx, VarScope scope) {
        Boolean b = ScriptRuntime.enumNext(this.objectIterator, cx);
        if (!b) {
            // Out of values. Throw StopIteration.
            throw new JavaScriptException(NativeIterator.getStopIterationObject(scope), null, 0);
        }
        return ScriptRuntime.enumId(this.objectIterator, cx);
    }

    /**
     * If "obj" is a java.util.Iterator or a java.lang.Iterable, return a wrapping as a JavaScript
     * Iterator. Otherwise, return null.
     */
    private static Iterator<?> getJavaIterator(Object obj) {
        if (obj instanceof Wrapper) {
            Object unwrapped = ((Wrapper) obj).unwrap();
            Iterator<?> iterator = null;
            if (unwrapped instanceof Iterator) iterator = (Iterator<?>) unwrapped;
            if (unwrapped instanceof Iterable) iterator = ((Iterable<?>) unwrapped).iterator();
            return iterator;
        }
        return null;
    }

    public static class WrappedJavaIterator {
        private final Iterator<?> iterator;
        private final VarScope scope;

        WrappedJavaIterator(Iterator<?> iterator, VarScope scope) {
            this.iterator = iterator;
            this.scope = scope;
        }

        public Object next() {
            if (!iterator.hasNext()) {
                // Out of values. Throw StopIteration.
                throw new JavaScriptException(
                        NativeIterator.getStopIterationObject(scope), null, 0);
            }
            return iterator.next();
        }

        public Object __iterator__(boolean b) {
            return this;
        }
    }

    /** Returns %Iterator.prototype% of the realm of a scope. */
    static Scriptable getIteratorPrototype(VarScope scope) {
        return (Scriptable) ScriptableObject.getTopScopeValue(scope, ITERATOR_PROTOTYPE_KEY);
    }

    /**
     * An accessor whose setter acts as SetterThatIgnoresPrototypeProperties, letting objects that
     * inherit from {@code home} shadow the property by assignment.
     */
    private static DescriptorInfo weirdAccessor(
            VarScope scope, ScriptableObject home, Object key, Object value) {
        String name =
                key instanceof SymbolKey ? "[" + ((SymbolKey) key).getName() + "]" : (String) key;
        var getter = new LambdaFunction(scope, "get " + name, 0, (c, s, t, a) -> value, false);
        var setter =
                new LambdaFunction(
                        scope,
                        "set " + name,
                        1,
                        (c, s, thisObj, a) -> {
                            Object v = a.length > 0 ? a[0] : Undefined.instance;
                            if (!ScriptRuntime.isObject(thisObj) || thisObj == home) {
                                throw ScriptRuntime.typeErrorById("msg.modify.readonly", name);
                            }
                            var target = (Scriptable) thisObj;
                            if (target instanceof ScriptableObject
                                    && ((ScriptableObject) target).getOwnPropertyDescriptor(c, key)
                                            == null) {
                                ((ScriptableObject) target)
                                        .defineOwnProperty(
                                                c, key, new DescriptorInfo(true, true, true, v));
                            } else if (key instanceof Symbol) {
                                ScriptableObject.putProperty(target, (Symbol) key, v);
                            } else {
                                ScriptableObject.putProperty(target, (String) key, v);
                            }
                            return Undefined.instance;
                        },
                        false);
        return new DescriptorInfo(
                Boolean.FALSE, NOT_FOUND, Boolean.TRUE, getter, setter, NOT_FOUND);
    }

    private static Scriptable js_esConstructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        if (nt == f || !(nt instanceof Scriptable)) {
            throw ScriptRuntime.typeErrorById("msg.iterator.abstract");
        }
        Object proto = ScriptableObject.getProperty((Scriptable) nt, "prototype");
        NativeObject result = new NativeObject();
        result.setPrototype(
                ScriptRuntime.isObject(proto) ? (Scriptable) proto : getIteratorPrototype(s));
        result.setParentScope(s);
        return result;
    }

    private static Object js_esIterator(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return thisObj;
    }

    private static Object js_from(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Object o = args.length > 0 ? args[0] : Undefined.instance;
        IteratorRecord record = IteratorRecord.flattenable(cx, s, o, true);
        if (ScriptRuntime.jsDelegatesTo(record.iterator, getIteratorPrototype(s))) {
            return record.iterator;
        }
        return new WrapForValidIterator(s, record);
    }

    private static Object js_concat(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Object[] iterables = args.clone();
        Callable[] openMethods = new Callable[args.length];
        for (int i = 0; i < iterables.length; i++) {
            Object item = iterables[i];
            if (!ScriptRuntime.isObject(item)) {
                throw ScriptRuntime.typeErrorById("msg.arg.not.object", ScriptRuntime.typeof(item));
            }
            Object method = IteratorRecord.getMethod(cx, s, item, SymbolKey.ITERATOR);
            if (method == null) {
                throw ScriptRuntime.typeErrorById("msg.not.iterable", ScriptRuntime.toString(item));
            }
            openMethods[i] = (Callable) method;
        }
        return new NativeIteratorHelper(
                s, new NativeIteratorHelper.ConcatBody(iterables, openMethods));
    }

    /** Validates the receiver of a helper, as the first steps of each Iterator.prototype method. */
    private static Scriptable requireObject(Object thisObj, String method) {
        if (!ScriptRuntime.isObject(thisObj)) {
            throw ScriptRuntime.typeErrorById("msg.incompat.call", "Iterator.prototype." + method);
        }
        return (Scriptable) thisObj;
    }

    /**
     * Returns the callable argument of a helper, or closes the receiver and throws if it is not
     * callable.
     */
    private static Callable requireCallable(Context cx, VarScope s, Scriptable o, Object[] args) {
        Object fn = args.length > 0 ? args[0] : Undefined.instance;
        if (fn instanceof Callable) {
            return (Callable) fn;
        }
        var error = ScriptRuntime.notFunctionError(fn);
        new IteratorRecord(o, Undefined.instance).closeAbruptly(cx, s, error);
        throw error;
    }

    /** Converts the limit of take and drop, closing the receiver if it is invalid. */
    private static double toLimit(Context cx, VarScope s, Scriptable o, Object[] args) {
        IteratorRecord unopened = new IteratorRecord(o, Undefined.instance);
        double limit;
        try {
            limit = ScriptRuntime.toNumber(args.length > 0 ? args[0] : Undefined.instance);
        } catch (RuntimeException e) {
            unopened.closeAbruptly(cx, s, e);
            throw e;
        }
        double integer = ScriptRuntime.toIntegerOrInfinity(limit);
        if (Double.isNaN(limit) || integer < 0) {
            var error =
                    ScriptRuntime.rangeErrorById(
                            "msg.iterator.limit", ScriptRuntime.toString(limit));
            unopened.closeAbruptly(cx, s, error);
            throw error;
        }
        return integer;
    }

    private static Object js_map(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable o = requireObject(thisObj, "map");
        Callable mapper = requireCallable(cx, s, o, args);
        return new NativeIteratorHelper(
                s, new NativeIteratorHelper.MapBody(IteratorRecord.direct(cx, s, o), mapper));
    }

    private static Object js_filter(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable o = requireObject(thisObj, "filter");
        Callable predicate = requireCallable(cx, s, o, args);
        return new NativeIteratorHelper(
                s, new NativeIteratorHelper.FilterBody(IteratorRecord.direct(cx, s, o), predicate));
    }

    private static Object js_take(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable o = requireObject(thisObj, "take");
        double limit = toLimit(cx, s, o, args);
        return new NativeIteratorHelper(
                s, new NativeIteratorHelper.TakeBody(IteratorRecord.direct(cx, s, o), limit));
    }

    private static Object js_drop(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable o = requireObject(thisObj, "drop");
        double limit = toLimit(cx, s, o, args);
        return new NativeIteratorHelper(
                s, new NativeIteratorHelper.DropBody(IteratorRecord.direct(cx, s, o), limit));
    }

    private static Object js_flatMap(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable o = requireObject(thisObj, "flatMap");
        Callable mapper = requireCallable(cx, s, o, args);
        return new NativeIteratorHelper(
                s, new NativeIteratorHelper.FlatMapBody(IteratorRecord.direct(cx, s, o), mapper));
    }

    /** Calls the callback of an eager helper, closing the iterator if it throws. */
    private static Object callClosing(
            Context cx, VarScope s, IteratorRecord iterated, Callable fn, Object... args) {
        try {
            return fn.call(cx, s, Undefined.SCRIPTABLE_UNDEFINED, args);
        } catch (RuntimeException e) {
            iterated.closeAbruptly(cx, s, e);
            throw e;
        }
    }

    private static Object js_reduce(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable o = requireObject(thisObj, "reduce");
        Callable reducer = requireCallable(cx, s, o, args);
        IteratorRecord iterated = IteratorRecord.direct(cx, s, o);
        Object accumulator;
        double counter;
        if (args.length < 2) {
            accumulator = iterated.stepValue(cx, s);
            if (accumulator == IteratorRecord.DONE) {
                throw ScriptRuntime.typeErrorById("msg.empty.array.reduce");
            }
            counter = 1;
        } else {
            accumulator = args[1];
            counter = 0;
        }
        for (; ; counter++) {
            Object value = iterated.stepValue(cx, s);
            if (value == IteratorRecord.DONE) {
                return accumulator;
            }
            accumulator =
                    callClosing(
                            cx,
                            s,
                            iterated,
                            reducer,
                            accumulator,
                            value,
                            ScriptRuntime.wrapNumber(counter));
        }
    }

    private static Object js_toArray(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable o = requireObject(thisObj, "toArray");
        IteratorRecord iterated = IteratorRecord.direct(cx, s, o);
        ArrayList<Object> items = new ArrayList<>();
        for (; ; ) {
            Object value = iterated.stepValue(cx, s);
            if (value == IteratorRecord.DONE) {
                return cx.newArray(s, items.toArray());
            }
            items.add(value);
        }
    }

    private static Object js_forEach(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        Scriptable o = requireObject(thisObj, "forEach");
        Callable fn = requireCallable(cx, s, o, args);
        IteratorRecord iterated = IteratorRecord.direct(cx, s, o);
        for (double counter = 0; ; counter++) {
            Object value = iterated.stepValue(cx, s);
            if (value == IteratorRecord.DONE) {
                return Undefined.instance;
            }
            callClosing(cx, s, iterated, fn, value, ScriptRuntime.wrapNumber(counter));
        }
    }

    private static final int SOME = 0, EVERY = 1, FIND = 2;

    /** Runs some, every or find, which stop at the first value the predicate decides. */
    private static Object search(
            Context cx, VarScope s, Object thisObj, Object[] args, String method, int kind) {
        Scriptable o = requireObject(thisObj, method);
        Callable predicate = requireCallable(cx, s, o, args);
        IteratorRecord iterated = IteratorRecord.direct(cx, s, o);
        for (double counter = 0; ; counter++) {
            Object value = iterated.stepValue(cx, s);
            if (value == IteratorRecord.DONE) {
                return kind == FIND ? Undefined.instance : Boolean.valueOf(kind == EVERY);
            }
            boolean selected =
                    ScriptRuntime.toBoolean(
                            callClosing(
                                    cx,
                                    s,
                                    iterated,
                                    predicate,
                                    value,
                                    ScriptRuntime.wrapNumber(counter)));
            if (selected != (kind == EVERY)) {
                iterated.close(cx, s);
                return kind == FIND ? value : Boolean.valueOf(kind == SOME);
            }
        }
    }

    private static Object js_some(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return search(cx, s, thisObj, args, "some", SOME);
    }

    private static Object js_every(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return search(cx, s, thisObj, args, "every", EVERY);
    }

    private static Object js_find(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return search(cx, s, thisObj, args, "find", FIND);
    }

    /**
     * The result of Iterator.from for an iterator that does not inherit from Iterator.prototype.
     */
    static final class WrapForValidIterator extends ScriptableObject {
        @Serial private static final long serialVersionUID = 3270658294016733504L;

        private static final Object PROTOTYPE_KEY = "WrapForValidIteratorPrototype";

        private static final ClassDescriptor DESCRIPTOR =
                new ClassDescriptor.Builder(PROTOTYPE_KEY.toString())
                        .withMethod(CTOR, "next", 0, WrapForValidIterator::js_next)
                        .withMethod(CTOR, "return", 0, WrapForValidIterator::js_return)
                        .build();

        private final IteratorRecord iterated;

        WrapForValidIterator(VarScope scope, IteratorRecord iterated) {
            this.iterated = iterated;
            TopLevel top = ScriptableObject.getTopLevelScope(scope);
            setParentScope(top);
            setPrototype((Scriptable) ScriptableObject.getTopScopeValue(top, PROTOTYPE_KEY));
        }

        static void init(Context cx, TopLevel scope, Scriptable iteratorPrototype, boolean sealed) {
            var proto = new NativeObject();
            proto.setPrototype(iteratorPrototype);
            proto.setParentScope(scope);
            DESCRIPTOR.populateObject(cx, scope, proto, sealed);
            scope.associateValue(PROTOTYPE_KEY, proto);
        }

        @Override
        public String getClassName() {
            return "Object";
        }

        private static WrapForValidIterator realThis(Object thisObj) {
            return LambdaConstructor.convertThisObject(thisObj, WrapForValidIterator.class);
        }

        private static Object js_next(
                Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
            IteratorRecord record = realThis(thisObj).iterated;
            if (!(record.nextMethod instanceof Callable)) {
                throw ScriptRuntime.notFunctionError(
                        record.iterator, record.nextMethod, ES6Iterator.NEXT_METHOD);
            }
            return ((Callable) record.nextMethod)
                    .call(cx, s, record.iterator, ScriptRuntime.emptyArgs);
        }

        private static Object js_return(
                Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
            Scriptable iterator = realThis(thisObj).iterated.iterator;
            Object method = IteratorRecord.getMethod(cx, s, iterator, ES6Iterator.RETURN_METHOD);
            if (method == null) {
                return ES6Iterator.makeIteratorResult(cx, s, Boolean.TRUE);
            }
            return ((Callable) method).call(cx, s, iterator, ScriptRuntime.emptyArgs);
        }
    }
}
