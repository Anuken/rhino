/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

/**
 * The iterator a {@code for await} loop steps through. IRFactory lowers the loop onto intrinsics
 * that call these methods, and awaits the promises they return in the async function or generator
 * the loop is in.
 */
final class AsyncIteration {
    // What return() resolves to for an iterator without a return method
    private static final Object NO_RETURN = new Object();

    private final Scriptable iterator;
    private final Callable next;
    // set for a sync iterator, whose values are awaited, as in CreateAsyncFromSyncIterator
    private final boolean fromSync;
    private boolean done;
    private Object value = Undefined.instance;
    private boolean threw;
    private Object exception;

    private AsyncIteration(Scriptable iterator, Callable next, boolean fromSync) {
        this.iterator = iterator;
        this.next = next;
        this.fromSync = fromSync;
    }

    /** GetIterator(value, async). */
    static AsyncIteration create(Context cx, VarScope scope, Object value) {
        Object method = getMethod(cx, scope, value, SymbolKey.ASYNC_ITERATOR);
        boolean fromSync = method == null;
        if (fromSync) {
            method = getMethod(cx, scope, value, SymbolKey.ITERATOR);
        }
        if (!(method instanceof Callable)) {
            throw ScriptRuntime.typeErrorById("msg.not.iterable", ScriptRuntime.toString(value));
        }
        Object iterator =
                ((Callable) method)
                        .call(cx, scope, ScriptRuntime.toObject(cx, scope, value), ScriptRuntime.emptyArgs);
        if (!(iterator instanceof Scriptable) || Undefined.isUndefined(iterator)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.iterator.result.primitive", ScriptRuntime.toString(iterator));
        }
        Object next =
                ScriptRuntime.getObjectPropNoWarn(iterator, ES6Iterator.NEXT_METHOD, cx, scope);
        return new AsyncIteration(
                (Scriptable) iterator, next instanceof Callable ? (Callable) next : null, fromSync);
    }

    private static Object getMethod(Context cx, VarScope scope, Object value, SymbolKey key) {
        Scriptable obj = ScriptRuntime.toObject(cx, scope, value);
        Object method = ScriptableObject.getProperty(obj, key);
        return method == Scriptable.NOT_FOUND || method == null || Undefined.isUndefined(method)
                ? null
                : method;
    }

    /** Calls next(), returning what the loop awaits. */
    Object next(Context cx, VarScope scope) {
        // Should next() throw, the loop does not close the iterator
        done = true;
        if (next == null) {
            throw ScriptRuntime.notFunctionError(iterator, ES6Iterator.NEXT_METHOD);
        }
        Object result = next.call(cx, scope, iterator, ScriptRuntime.emptyArgs);
        return fromSync ? awaitSyncResult(cx, scope, result) : result;
    }

    /** Takes the awaited result of next(), returning whether it holds a value. */
    boolean step(Context cx, VarScope scope, Object result) {
        if (!(result instanceof Scriptable) || Undefined.isUndefined(result)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.iterator.result.primitive", ScriptRuntime.toString(result));
        }
        if (ScriptRuntime.isIteratorDone(cx, result)) {
            done = true;
            return false;
        }
        value = ScriptRuntime.getObjectPropNoWarn(result, ES6Iterator.VALUE_PROPERTY, cx, scope);
        done = false;
        return true;
    }

    Object value() {
        return value;
    }

    /** Returns whether the loop must close the iterator as it exits. */
    boolean isOpen() {
        return !done;
    }

    /** Calls return(), returning what the loop awaits before {@link #checkReturn}. */
    Object callReturn(Context cx, VarScope scope) {
        done = true;
        Object method =
                ScriptRuntime.getObjectPropNoWarn(iterator, ES6Iterator.RETURN_METHOD, cx, scope);
        if (method == null || Undefined.isUndefined(method)) {
            return NO_RETURN;
        }
        if (!(method instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(method, ES6Iterator.RETURN_METHOD);
        }
        Object result = ((Callable) method).call(cx, scope, iterator, ScriptRuntime.emptyArgs);
        return fromSync ? awaitSyncResult(cx, scope, result) : result;
    }

    /** Checks the awaited result of return(), for a loop left by break or return. */
    static void checkReturn(Object result) {
        if (result != NO_RETURN && (!(result instanceof Scriptable) || Undefined.isUndefined(result))) {
            throw ScriptRuntime.typeErrorById(
                    "msg.iterator.result.primitive", ScriptRuntime.toString(result));
        }
    }

    /**
     * Calls return() for a loop left by an exception, which the loop rethrows whatever return()
     * does. The result always fulfills.
     */
    Object callReturnQuietly(Context cx, VarScope frameScope) {
        VarScope scope = ScriptableObject.getTopLevelScope(frameScope);
        NativePromise settled = NativePromise.create(scope);
        try {
            Object result = callReturn(cx, scope);
            NativePromise promise = AsyncFunctionCode.promiseResolve(cx, scope, result);
            promise.onFulfill(cx, r -> settled.resolve(cx, scope, Undefined.instance));
            promise.onReject(cx, r -> settled.resolve(cx, scope, Undefined.instance));
        } catch (RhinoException e) {
            settled.resolve(cx, scope, Undefined.instance);
        }
        return settled;
    }

    /**
     * Calls return() for a loop left by a return statement or a jump out of an enclosing loop,
     * which cannot await it.
     */
    void callReturnWithoutAwaiting(Context cx, VarScope scope) {
        callReturn(cx, scope);
    }

    /** Records the exception that left the loop, which is thrown once the iterator is closed. */
    void caught(Object exception) {
        threw = true;
        this.exception = exception;
    }

    boolean threw() {
        return threw;
    }

    Object exception() {
        return exception;
    }

    /** Awaits the value of a sync iterator result, as in AsyncFromSyncIteratorContinuation. */
    private Object awaitSyncResult(Context cx, VarScope frameScope, Object result) {
        VarScope scope = ScriptableObject.getTopLevelScope(frameScope);
        if (!(result instanceof Scriptable) || Undefined.isUndefined(result)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.iterator.result.primitive", ScriptRuntime.toString(result));
        }
        boolean resultDone = ScriptRuntime.isIteratorDone(cx, result);
        Object resultValue =
                ScriptRuntime.getObjectPropNoWarn(result, ES6Iterator.VALUE_PROPERTY, cx, scope);
        NativePromise awaited = NativePromise.create(scope);
        NativePromise valuePromise = AsyncFunctionCode.promiseResolve(cx, scope, resultValue);
        valuePromise.onFulfill(
                cx,
                v ->
                        awaited.resolve(
                                cx, scope, ES6Iterator.makeIteratorResult(cx, scope, resultDone, v)));
        valuePromise.onReject(cx, r -> awaited.reject(cx, scope, r));
        return awaited;
    }
}
