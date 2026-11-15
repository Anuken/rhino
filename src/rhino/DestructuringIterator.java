/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

/**
 * The iterator an array destructuring pattern steps through, as in IteratorBindingInitialization.
 * Arrays whose iteration behavior is unmodified are read by index instead, which nothing can
 * observe.
 */
final class DestructuringIterator {
    private final Context cx;
    private final VarScope scope;
    // Set when reading an unmodified array directly
    private final Scriptable array;
    private long index;
    private final Object iterator;
    private final Callable next;
    private boolean done;

    private DestructuringIterator(Context cx, VarScope scope, Object value) {
        this.cx = cx;
        this.scope = scope;
        if (value instanceof NativeArray && hasDefaultIteration((NativeArray) value, scope)) {
            array = (Scriptable) value;
            iterator = null;
            next = null;
        } else {
            array = null;
            if (value == null || Undefined.isUndefined(value)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.not.iterable", ScriptRuntime.toString(value));
            }
            iterator = ScriptRuntime.callIterator(value, cx, scope);
            if (!(iterator instanceof Scriptable) || Undefined.isUndefined(iterator)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.not.iterable", ScriptRuntime.toString(value));
            }
            Object nextFn =
                    ScriptRuntime.getObjectPropNoWarn(
                            iterator, ES6Iterator.NEXT_METHOD, cx, scope);
            next = nextFn instanceof Callable ? (Callable) nextFn : null;
        }
    }

    static DestructuringIterator create(Context cx, VarScope scope, Object value) {
        return new DestructuringIterator(cx, scope, value);
    }

    /** Returns whether iterating an array would call the built-in array iterator. */
    private static boolean hasDefaultIteration(NativeArray array, VarScope scope) {
        Object iteratorFn = ScriptableObject.getProperty(array, SymbolKey.ITERATOR);
        if (!(iteratorFn instanceof JSFunction)
                || ((JSFunction) iteratorFn).getDescriptor() != NativeArray.VALUES_DESCRIPTOR) {
            return false;
        }
        Object proto = NativeArrayIterator.getPrototype(scope);
        if (!(proto instanceof Scriptable)) {
            return false;
        }
        Object nextFn = ScriptableObject.getProperty((Scriptable) proto, ES6Iterator.NEXT_METHOD);
        return nextFn instanceof JSFunction
                && ((JSFunction) nextFn).getDescriptor() == NativeArrayIterator.NEXT_DESCRIPTOR;
    }

    /** Steps the iterator, returning its value, or undefined once it is done. */
    Object next() {
        if (done) {
            return Undefined.instance;
        }
        if (array != null) {
            if (index >= NativeArray.getLengthProperty(cx, array)) {
                done = true;
                return Undefined.instance;
            }
            Object value = getElement(array, index++);
            return value == Scriptable.NOT_FOUND ? Undefined.instance : value;
        }
        Object result;
        try {
            if (next == null) {
                throw ScriptRuntime.notFunctionError(iterator, ES6Iterator.NEXT_METHOD);
            }
            result = next.call(cx, scope, (Scriptable) iterator, ScriptRuntime.emptyArgs);
            if (!(result instanceof Scriptable) || Undefined.isUndefined(result)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.iterator.result.primitive", ScriptRuntime.toString(result));
            }
            if (ScriptRuntime.isIteratorDone(cx, result)) {
                done = true;
                return Undefined.instance;
            }
            return ScriptRuntime.getObjectPropNoWarn(
                    result, ES6Iterator.VALUE_PROPERTY, cx, scope);
        } catch (RuntimeException e) {
            // An iterator that throws is not closed
            done = true;
            throw e;
        }
    }

    private static Object getElement(Scriptable array, long index) {
        if (index <= Integer.MAX_VALUE) {
            return ScriptableObject.getProperty(array, (int) index);
        }
        return ScriptableObject.getProperty(array, Long.toString(index));
    }

    /** Collects the remaining values into an array, for a rest element. */
    Scriptable rest() {
        Scriptable result = cx.newArray(scope, 0);
        int i = 0;
        for (; ; ) {
            Object value = next();
            if (done) {
                return result;
            }
            result.put(i++, result, value);
        }
    }

    /** Closes the iterator unless it is done, once the pattern completes normally. */
    void close() {
        if (done) {
            return;
        }
        done = true;
        if (array != null) {
            return;
        }
        Object returnFn =
                ScriptRuntime.getObjectPropNoWarn(iterator, ES6Iterator.RETURN_METHOD, cx, scope);
        if (returnFn == null || Undefined.isUndefined(returnFn)) {
            return;
        }
        if (!(returnFn instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(returnFn, ES6Iterator.RETURN_METHOD);
        }
        Object result =
                ((Callable) returnFn).call(cx, scope, (Scriptable) iterator, ScriptRuntime.emptyArgs);
        if (!(result instanceof Scriptable) || Undefined.isUndefined(result)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.iterator.result.primitive", ScriptRuntime.toString(result));
        }
    }
}
