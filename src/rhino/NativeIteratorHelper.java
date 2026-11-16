/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import static rhino.ClassDescriptor.Builder.value;
import static rhino.ClassDescriptor.Destination.CTOR;

/**
 * The iterator objects returned by the lazy Iterator.prototype helpers and Iterator.concat, which
 * inherit from %IteratorHelperPrototype%. Each one runs its steps as a state machine in place of
 * the spec's generator closure.
 */
final class NativeIteratorHelper extends ScriptableObject {
    private static final String CLASS_NAME = "Iterator Helper";
    private static final Object PROTOTYPE_KEY = "IteratorHelperPrototype";

    private static final ClassDescriptor DESCRIPTOR =
            new ClassDescriptor.Builder(CLASS_NAME)
                    .withMethod(CTOR, "next", 0, NativeIteratorHelper::js_next)
                    .withMethod(CTOR, "return", 0, NativeIteratorHelper::js_return)
                    .withProp(CTOR, SymbolKey.TO_STRING_TAG, value(CLASS_NAME, DONTENUM | READONLY))
                    .build();

    private enum State {
        SUSPENDED_START,
        SUSPENDED_YIELD,
        EXECUTING,
        COMPLETED
    }

    /** The steps of one kind of helper, run between its yields. */
    abstract static class Body {
        /** Runs until the next yield, returning its value, or {@link IteratorRecord#DONE}. */
        abstract Object step(Context cx, VarScope scope);

        /** Closes the underlying iterators for return() before the first next(). */
        abstract void closeUnstarted(Context cx, VarScope scope);

        /** Closes the underlying iterators for return() while suspended at a yield. */
        abstract void closeSuspended(Context cx, VarScope scope);
    }

    private final Body body;
    private State state = State.SUSPENDED_START;

    private NativeIteratorHelper() {
        body = null;
    }

    NativeIteratorHelper(VarScope scope, Body body) {
        this.body = body;
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
        return CLASS_NAME;
    }

    private static NativeIteratorHelper realThis(Object thisObj) {
        return LambdaConstructor.convertThisObject(thisObj, NativeIteratorHelper.class);
    }

    private static Object js_next(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeIteratorHelper helper = realThis(thisObj);
        if (helper.state == State.EXECUTING) {
            throw ScriptRuntime.typeErrorById("msg.already.exec.gen");
        }
        if (helper.state == State.COMPLETED) {
            return ES6Iterator.makeIteratorResult(cx, s, Boolean.TRUE);
        }
        helper.state = State.EXECUTING;
        Object value;
        try {
            value = helper.body.step(cx, s);
        } catch (RuntimeException e) {
            helper.state = State.COMPLETED;
            throw e;
        }
        if (value == IteratorRecord.DONE) {
            helper.state = State.COMPLETED;
            return ES6Iterator.makeIteratorResult(cx, s, Boolean.TRUE);
        }
        helper.state = State.SUSPENDED_YIELD;
        return ES6Iterator.makeIteratorResult(cx, s, Boolean.FALSE, value);
    }

    private static Object js_return(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        NativeIteratorHelper helper = realThis(thisObj);
        switch (helper.state) {
            case SUSPENDED_START:
                helper.state = State.COMPLETED;
                helper.body.closeUnstarted(cx, s);
                break;
            case EXECUTING:
                throw ScriptRuntime.typeErrorById("msg.already.exec.gen");
            case SUSPENDED_YIELD:
                helper.state = State.EXECUTING;
                try {
                    helper.body.closeSuspended(cx, s);
                } finally {
                    helper.state = State.COMPLETED;
                }
                break;
            default:
                break;
        }
        return ES6Iterator.makeIteratorResult(cx, s, Boolean.TRUE);
    }

    /** A helper over a single underlying iterator, which return() closes in either state. */
    abstract static class SingleBody extends Body {
        final IteratorRecord iterated;

        SingleBody(IteratorRecord iterated) {
            this.iterated = iterated;
        }

        /** Calls a callback, closing the underlying iterator if it throws. */
        Object call(Context cx, VarScope scope, Callable fn, Object[] args) {
            try {
                return fn.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, args);
            } catch (RuntimeException e) {
                iterated.closeAbruptly(cx, scope, e);
                throw e;
            }
        }

        @Override
        void closeUnstarted(Context cx, VarScope scope) {
            iterated.close(cx, scope);
        }

        @Override
        void closeSuspended(Context cx, VarScope scope) {
            iterated.close(cx, scope);
        }
    }

    static final class MapBody extends SingleBody {
        private final Callable mapper;
        private double counter;

        MapBody(IteratorRecord iterated, Callable mapper) {
            super(iterated);
            this.mapper = mapper;
        }

        @Override
        Object step(Context cx, VarScope scope) {
            Object value = iterated.stepValue(cx, scope);
            if (value == IteratorRecord.DONE) {
                return value;
            }
            return call(
                    cx, scope, mapper, new Object[] {value, ScriptRuntime.wrapNumber(counter++)});
        }
    }

    static final class FilterBody extends SingleBody {
        private final Callable predicate;
        private double counter;

        FilterBody(IteratorRecord iterated, Callable predicate) {
            super(iterated);
            this.predicate = predicate;
        }

        @Override
        Object step(Context cx, VarScope scope) {
            for (; ; ) {
                Object value = iterated.stepValue(cx, scope);
                if (value == IteratorRecord.DONE) {
                    return value;
                }
                Object selected =
                        call(
                                cx,
                                scope,
                                predicate,
                                new Object[] {value, ScriptRuntime.wrapNumber(counter++)});
                if (ScriptRuntime.toBoolean(selected)) {
                    return value;
                }
            }
        }
    }

    static final class TakeBody extends SingleBody {
        private double remaining;

        TakeBody(IteratorRecord iterated, double limit) {
            super(iterated);
            this.remaining = limit;
        }

        @Override
        Object step(Context cx, VarScope scope) {
            if (remaining == 0) {
                iterated.close(cx, scope);
                return IteratorRecord.DONE;
            }
            if (remaining != Double.POSITIVE_INFINITY) {
                remaining--;
            }
            return iterated.stepValue(cx, scope);
        }
    }

    static final class DropBody extends SingleBody {
        private double remaining;

        DropBody(IteratorRecord iterated, double limit) {
            super(iterated);
            this.remaining = limit;
        }

        @Override
        Object step(Context cx, VarScope scope) {
            while (remaining > 0) {
                if (remaining != Double.POSITIVE_INFINITY) {
                    remaining--;
                }
                if (iterated.step(cx, scope) == IteratorRecord.DONE) {
                    return IteratorRecord.DONE;
                }
            }
            return iterated.stepValue(cx, scope);
        }
    }

    static final class FlatMapBody extends SingleBody {
        private final Callable mapper;
        private double counter;
        private IteratorRecord inner;

        FlatMapBody(IteratorRecord iterated, Callable mapper) {
            super(iterated);
            this.mapper = mapper;
        }

        @Override
        Object step(Context cx, VarScope scope) {
            for (; ; ) {
                if (inner == null) {
                    Object value = iterated.stepValue(cx, scope);
                    if (value == IteratorRecord.DONE) {
                        return value;
                    }
                    Object mapped =
                            call(
                                    cx,
                                    scope,
                                    mapper,
                                    new Object[] {value, ScriptRuntime.wrapNumber(counter++)});
                    try {
                        inner = IteratorRecord.flattenable(cx, scope, mapped, false);
                    } catch (RuntimeException e) {
                        iterated.closeAbruptly(cx, scope, e);
                        throw e;
                    }
                }
                Object innerValue;
                try {
                    innerValue = inner.stepValue(cx, scope);
                } catch (RuntimeException e) {
                    iterated.closeAbruptly(cx, scope, e);
                    throw e;
                }
                if (innerValue != IteratorRecord.DONE) {
                    return innerValue;
                }
                inner = null;
            }
        }

        @Override
        void closeSuspended(Context cx, VarScope scope) {
            try {
                inner.close(cx, scope);
            } catch (RuntimeException e) {
                iterated.closeAbruptly(cx, scope, e);
                throw e;
            }
            iterated.close(cx, scope);
        }
    }

    /** Iterator.concat: opens each iterable in turn, with nothing to close before starting. */
    static final class ConcatBody extends Body {
        private final Object[] iterables;
        private final Callable[] openMethods;
        private int index;
        private IteratorRecord current;

        ConcatBody(Object[] iterables, Callable[] openMethods) {
            this.iterables = iterables;
            this.openMethods = openMethods;
        }

        @Override
        Object step(Context cx, VarScope scope) {
            for (; ; ) {
                if (current == null) {
                    if (index == iterables.length) {
                        return IteratorRecord.DONE;
                    }
                    Object iterable = iterables[index];
                    Object iter =
                            openMethods[index].call(cx, scope, iterable, ScriptRuntime.emptyArgs);
                    index++;
                    if (!ScriptRuntime.isObject(iter)) {
                        throw ScriptRuntime.typeErrorById(
                                "msg.iterator.result.primitive", ScriptRuntime.toString(iter));
                    }
                    current = IteratorRecord.direct(cx, scope, (Scriptable) iter);
                }
                Object value = current.stepValue(cx, scope);
                if (value != IteratorRecord.DONE) {
                    return value;
                }
                current = null;
            }
        }

        @Override
        void closeUnstarted(Context cx, VarScope scope) {}

        @Override
        void closeSuspended(Context cx, VarScope scope) {
            current.close(cx, scope);
        }
    }
}
