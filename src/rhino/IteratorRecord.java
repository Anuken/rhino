/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

/** An Iterator Record, with the abstract operations that step and close it. */
final class IteratorRecord {
    /** Returned by {@link #stepValue} and {@link #step} once the iterator is done. */
    static final Object DONE = new Object();

    final Scriptable iterator;
    final Object nextMethod;
    boolean done;

    IteratorRecord(Scriptable iterator, Object nextMethod) {
        this.iterator = iterator;
        this.nextMethod = nextMethod;
    }

    /** GetIteratorDirect */
    static IteratorRecord direct(Context cx, VarScope scope, Scriptable obj) {
        return new IteratorRecord(
                obj, ScriptRuntime.getObjectPropNoWarn(obj, ES6Iterator.NEXT_METHOD, cx, scope));
    }

    /** GetIteratorFlattenable, iterating strings only if {@code allowStrings} is set. */
    static IteratorRecord flattenable(
            Context cx, VarScope scope, Object obj, boolean allowStrings) {
        if (!ScriptRuntime.isObject(obj)) {
            if (!allowStrings || !(obj instanceof CharSequence)) {
                throw ScriptRuntime.typeErrorById("msg.arg.not.object", ScriptRuntime.typeof(obj));
            }
        }
        Object method = getMethod(cx, scope, obj, SymbolKey.ITERATOR);
        Object iterator =
                method == null
                        ? obj
                        : ((Callable) method).call(cx, scope, obj, ScriptRuntime.emptyArgs);
        if (!ScriptRuntime.isObject(iterator)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.iterator.result.primitive", ScriptRuntime.toString(iterator));
        }
        return direct(cx, scope, (Scriptable) iterator);
    }

    /** GetMethod: returns null if the property is undefined or null. */
    static Object getMethod(Context cx, VarScope scope, Object obj, Object key) {
        Object method =
                key instanceof SymbolKey
                        ? ScriptRuntime.getObjectElem(obj, key, cx, scope)
                        : ScriptRuntime.getObjectPropNoWarn(obj, (String) key, cx, scope);
        if (method == null || Undefined.isUndefined(method)) {
            return null;
        }
        if (!(method instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(obj, method, key.toString());
        }
        return method;
    }

    /** IteratorStep: returns the result object, or {@link #DONE}. */
    Object step(Context cx, VarScope scope) {
        try {
            if (!(nextMethod instanceof Callable)) {
                throw ScriptRuntime.notFunctionError(iterator, nextMethod, ES6Iterator.NEXT_METHOD);
            }
            Object result =
                    ((Callable) nextMethod).call(cx, scope, iterator, ScriptRuntime.emptyArgs);
            if (!ScriptRuntime.isObject(result)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.iterator.result.primitive", ScriptRuntime.toString(result));
            }
            Object doneValue =
                    ScriptRuntime.getObjectPropNoWarn(result, ES6Iterator.DONE_PROPERTY, cx, scope);
            if (ScriptRuntime.toBoolean(doneValue)) {
                done = true;
                return DONE;
            }
            return result;
        } catch (RuntimeException e) {
            done = true;
            throw e;
        }
    }

    /** IteratorStepValue: returns the next value, or {@link #DONE}. */
    Object stepValue(Context cx, VarScope scope) {
        Object result = step(cx, scope);
        if (result == DONE) {
            return DONE;
        }
        try {
            return ScriptRuntime.getObjectPropNoWarn(result, ES6Iterator.VALUE_PROPERTY, cx, scope);
        } catch (RuntimeException e) {
            done = true;
            throw e;
        }
    }

    /** IteratorClose for a normal or return completion: errors from closing propagate. */
    void close(Context cx, VarScope scope) {
        Object method = getMethod(cx, scope, iterator, ES6Iterator.RETURN_METHOD);
        if (method == null) {
            return;
        }
        Object result = ((Callable) method).call(cx, scope, iterator, ScriptRuntime.emptyArgs);
        if (!ScriptRuntime.isObject(result)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.iterator.result.primitive", ScriptRuntime.toString(result));
        }
    }

    /**
     * IteratorClose for a throw completion, which the caller rethrows: errors from closing are
     * discarded.
     */
    void closeAbruptly(Context cx, VarScope scope, RuntimeException cause) {
        if (cause instanceof ContinuationPending) {
            return;
        }
        try {
            Object method = getMethod(cx, scope, iterator, ES6Iterator.RETURN_METHOD);
            if (method != null) {
                ((Callable) method).call(cx, scope, iterator, ScriptRuntime.emptyArgs);
            }
        } catch (RuntimeException ignored) {
            // The original exception wins
        }
    }
}
