/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

/**
 * An async iterator record, as stepped by a {@code for await} loop or the {@code yield*} of an
 * async generator. IRFactory lowers the loop onto intrinsics that call these methods, and awaits
 * what they return in the async function or generator the loop is in. A sync iterator is wrapped as
 * in CreateAsyncFromSyncIterator: its methods then return promises and never throw.
 */
final class AsyncIteration {
    // What return() resolves to for an iterator without a return method
    static final Object NO_RETURN = new Object();
    // What throw() returns for an iterator without a throw method
    static final Object NO_THROW = new Object();

    private final Scriptable iterator;
    private final Object next;
    private final boolean fromSync;
    private boolean done;
    private Object value = Undefined.instance;

    private AsyncIteration(Scriptable iterator, Object next, boolean fromSync) {
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
                        .call(
                                cx,
                                scope,
                                ScriptRuntime.toObject(cx, scope, value),
                                ScriptRuntime.emptyArgs);
        if (!(iterator instanceof Scriptable) || Undefined.isUndefined(iterator)) {
            throw ScriptRuntime.typeErrorById(
                    "msg.iterator.result.primitive", ScriptRuntime.toString(iterator));
        }
        Object next =
                ScriptRuntime.getObjectPropNoWarn(iterator, ES6Iterator.NEXT_METHOD, cx, scope);
        return new AsyncIteration((Scriptable) iterator, next, fromSync);
    }

    private static Object getMethod(Context cx, VarScope scope, Object value, Object key) {
        Scriptable obj = ScriptRuntime.toObject(cx, scope, value);
        Object method =
                key instanceof Symbol
                        ? ScriptableObject.getProperty(obj, (Symbol) key)
                        : ScriptableObject.getProperty(obj, (String) key);
        if (method == Scriptable.NOT_FOUND || method == null || Undefined.isUndefined(method)) {
            return null;
        }
        if (!(method instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(method, key.toString());
        }
        return method;
    }

    private static boolean isObject(Object value) {
        return value instanceof Scriptable && !Undefined.isUndefined(value);
    }

    private static RhinoException notObject(Object value) {
        return ScriptRuntime.typeErrorById(
                "msg.iterator.result.primitive", ScriptRuntime.toString(value));
    }

    // for await

    /** Calls next(), returning what the loop awaits. */
    Object next(Context cx, VarScope scope) {
        // Should next() throw, the loop does not close the iterator
        done = true;
        return callNext(cx, scope, ScriptRuntime.emptyArgs);
    }

    /** Takes the awaited result of next(), returning whether it holds a value. */
    boolean step(Context cx, VarScope scope, Object result) {
        if (!isObject(result)) {
            throw notObject(result);
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
    static boolean isOpen(Object iteration) {
        return iteration instanceof AsyncIteration && !((AsyncIteration) iteration).done;
    }

    /** Calls return() for a loop left early, returning what the loop awaits before checkReturn. */
    Object callReturn(Context cx, VarScope scope) {
        done = true;
        return callReturn(cx, scope, null);
    }

    /** Checks the awaited result of return(), for a loop left by break, continue or return. */
    static void checkReturn(Object result) {
        if (result != NO_RETURN && !isObject(result)) {
            throw notObject(result);
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

    // The async iterator methods, as yield* calls them

    /** Calls next, returning the result to await. */
    Object callNext(Context cx, VarScope scope, Object[] args) {
        if (fromSync) {
            return syncNext(cx, ScriptableObject.getTopLevelScope(scope), args);
        }
        if (!(next instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(next, ES6Iterator.NEXT_METHOD);
        }
        return ((Callable) next).call(cx, scope, iterator, args);
    }

    /** Calls throw, returning the result to await, or {@link #NO_THROW} if there is none. */
    Object callThrow(Context cx, VarScope scope, Object value) {
        if (fromSync) {
            // The wrapper of a sync iterator always has a throw method
            return syncThrow(cx, ScriptableObject.getTopLevelScope(scope), value);
        }
        Object method = getMethod(cx, scope, iterator, "throw");
        if (method == null) {
            return NO_THROW;
        }
        return ((Callable) method).call(cx, scope, iterator, new Object[] {value});
    }

    /**
     * Calls return, returning the result to await, or {@link #NO_RETURN} if there is none.
     *
     * @param args the arguments, or null to pass none
     */
    Object callReturn(Context cx, VarScope scope, Object[] args) {
        if (fromSync) {
            return syncReturn(cx, ScriptableObject.getTopLevelScope(scope), args);
        }
        Object method = getMethod(cx, scope, iterator, ES6Iterator.RETURN_METHOD);
        if (method == null) {
            return NO_RETURN;
        }
        return ((Callable) method)
                .call(cx, scope, iterator, args == null ? ScriptRuntime.emptyArgs : args);
    }

    // %AsyncFromSyncIteratorPrototype%

    private NativePromise syncNext(Context cx, VarScope scope, Object[] args) {
        NativePromise promise = NativePromise.create(scope);
        Object result;
        try {
            if (!(next instanceof Callable)) {
                throw ScriptRuntime.notFunctionError(next, ES6Iterator.NEXT_METHOD);
            }
            result = ((Callable) next).call(cx, scope, iterator, args);
            if (!isObject(result)) {
                throw notObject(result);
            }
        } catch (RhinoException e) {
            promise.reject(cx, scope, NativePromise.getErrorObject(cx, scope, e));
            return promise;
        }
        continuation(cx, scope, result, promise, true);
        return promise;
    }

    private Object syncReturn(Context cx, VarScope scope, Object[] args) {
        NativePromise promise = NativePromise.create(scope);
        Object result;
        try {
            Object method = getMethod(cx, scope, iterator, ES6Iterator.RETURN_METHOD);
            if (method == null) {
                Object value = args == null || args.length == 0 ? Undefined.instance : args[0];
                promise.resolve(cx, scope, ES6Iterator.makeIteratorResult(cx, scope, true, value));
                return promise;
            }
            result =
                    ((Callable) method)
                            .call(
                                    cx,
                                    scope,
                                    iterator,
                                    args == null ? ScriptRuntime.emptyArgs : args);
            if (!isObject(result)) {
                throw notObject(result);
            }
        } catch (RhinoException e) {
            promise.reject(cx, scope, NativePromise.getErrorObject(cx, scope, e));
            return promise;
        }
        continuation(cx, scope, result, promise, false);
        return promise;
    }

    private NativePromise syncThrow(Context cx, VarScope scope, Object value) {
        NativePromise promise = NativePromise.create(scope);
        Object result;
        try {
            Object method = getMethod(cx, scope, iterator, "throw");
            if (method == null) {
                // Close the iterator, as the delegating yield* expects a throw method
                Object close = getMethod(cx, scope, iterator, ES6Iterator.RETURN_METHOD);
                if (close != null) {
                    Object closed =
                            ((Callable) close).call(cx, scope, iterator, ScriptRuntime.emptyArgs);
                    if (!isObject(closed)) {
                        throw notObject(closed);
                    }
                }
                throw ScriptRuntime.typeErrorById("msg.yield.star.no.throw");
            }
            result = ((Callable) method).call(cx, scope, iterator, new Object[] {value});
            if (!isObject(result)) {
                throw notObject(result);
            }
        } catch (RhinoException e) {
            promise.reject(cx, scope, NativePromise.getErrorObject(cx, scope, e));
            return promise;
        }
        continuation(cx, scope, result, promise, true);
        return promise;
    }

    /** AsyncFromSyncIteratorContinuation: settles the promise with the awaited result. */
    private void continuation(
            Context cx,
            VarScope scope,
            Object result,
            NativePromise promise,
            boolean closeOnRejection) {
        boolean resultDone;
        NativePromise valueWrapper;
        try {
            resultDone = ScriptRuntime.isIteratorDone(cx, result);
            Object resultValue =
                    ScriptRuntime.getObjectPropNoWarn(
                            result, ES6Iterator.VALUE_PROPERTY, cx, scope);
            try {
                valueWrapper = AsyncFunctionCode.promiseResolve(cx, scope, resultValue);
            } catch (RhinoException e) {
                if (!resultDone && closeOnRejection) {
                    closeQuietly(cx, scope);
                }
                throw e;
            }
        } catch (RhinoException e) {
            promise.reject(cx, scope, NativePromise.getErrorObject(cx, scope, e));
            return;
        }
        boolean close = !resultDone && closeOnRejection;
        valueWrapper.onFulfill(
                cx,
                v ->
                        promise.resolve(
                                cx,
                                scope,
                                ES6Iterator.makeIteratorResult(cx, scope, resultDone, v)));
        valueWrapper.onReject(
                cx,
                r -> {
                    if (close) {
                        closeQuietly(Context.getCurrentContext(), scope);
                    }
                    promise.reject(cx, scope, r);
                });
    }

    /** IteratorClose for a throw completion: calls return, ignoring what it does. */
    private void closeQuietly(Context cx, VarScope scope) {
        try {
            Object method = getMethod(cx, scope, iterator, ES6Iterator.RETURN_METHOD);
            if (method != null) {
                ((Callable) method).call(cx, scope, iterator, ScriptRuntime.emptyArgs);
            }
        } catch (RhinoException e) {
            // the original rejection wins
        }
    }
}
