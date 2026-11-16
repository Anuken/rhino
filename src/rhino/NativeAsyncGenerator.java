/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import java.util.ArrayDeque;

/**
 * An async generator object. Its body runs as a generator, see {@link AsyncGeneratorCode}, which
 * yields both the values it yields and, wrapped in {@link AwaitRequest}, the values it awaits. This
 * object queues the next, throw and return requests and settles their promises as the body runs.
 */
final class NativeAsyncGenerator extends ScriptableObject {
    private static final long serialVersionUID = 1L;

    private static final String ASYNC_ITERATOR_PROTOTYPE = "AsyncIteratorPrototype";
    private static final String ASYNC_GENERATOR_PROTOTYPE = "AsyncGeneratorPrototype";

    private static final int NEXT = NativeGenerator.GENERATOR_SEND,
            THROW = NativeGenerator.GENERATOR_THROW,
            RETURN = NativeGenerator.GENERATOR_CLOSE;

    private enum State {
        SUSPENDED_START,
        SUSPENDED_YIELD,
        EXECUTING,
        AWAITING_RETURN,
        COMPLETED
    }

    /** What an async generator body yields for {@code await value}. */
    static final class AwaitRequest {
        final Object value;

        AwaitRequest(Object value) {
            this.value = value;
        }
    }

    /** What an async generator body yields for {@code yield* value}. */
    static final class YieldStarRequest {
        final Object value;

        YieldStarRequest(Object value) {
            this.value = value;
        }
    }

    private static final class Request {
        final int kind;
        final Object value;
        final NativePromise promise;

        Request(int kind, Object value, NativePromise promise) {
            this.kind = kind;
            this.value = value;
            this.promise = promise;
        }
    }

    private final transient VarScope scope;
    private final transient ES6Generator generator;
    private final transient ArrayDeque<Request> queue = new ArrayDeque<>();
    private State state = State.SUSPENDED_START;
    // The iterator of the yield* being evaluated
    private transient AsyncIteration delegate;

    NativeAsyncGenerator(VarScope scope, JSFunction function, ES6Generator generator) {
        this.scope = ScriptableObject.getTopLevelScope(scope);
        this.generator = generator;
        setParentScope(this.scope);
        Object proto = ScriptableObject.getProperty(function, "prototype");
        setPrototype(
                proto instanceof Scriptable && !Undefined.isUndefined(proto)
                        ? (Scriptable) proto
                        : getAsyncGeneratorPrototype(scope));
    }

    @Override
    public String getClassName() {
        return "AsyncGenerator";
    }

    // Intrinsic objects

    /** Returns %AsyncIteratorPrototype%. */
    static Scriptable getAsyncIteratorPrototype(VarScope scope) {
        TopLevel top = ScriptableObject.getTopLevelScope(scope);
        Object existing = top.getAssociatedValue(ASYNC_ITERATOR_PROTOTYPE);
        if (existing instanceof Scriptable) {
            return (Scriptable) existing;
        }
        NativeObject proto = new NativeObject();
        proto.setParentScope(top);
        proto.setPrototype(ScriptableObject.getObjectPrototype(top));
        proto.defineProperty(
                SymbolKey.ASYNC_ITERATOR,
                new LambdaFunction(top, "[Symbol.asyncIterator]", 0, (c, s, t, a) -> t, false),
                DONTENUM);
        return (Scriptable) top.associateValue(ASYNC_ITERATOR_PROTOTYPE, proto);
    }

    /** Returns %AsyncGeneratorPrototype%. */
    static Scriptable getAsyncGeneratorPrototype(VarScope scope) {
        TopLevel top = ScriptableObject.getTopLevelScope(scope);
        Object existing = top.getAssociatedValue(ASYNC_GENERATOR_PROTOTYPE);
        if (existing instanceof Scriptable) {
            return (Scriptable) existing;
        }
        NativeObject proto = new NativeObject();
        proto.setParentScope(top);
        proto.setPrototype(getAsyncIteratorPrototype(top));
        defineRequestMethod(proto, top, "next", NEXT);
        defineRequestMethod(proto, top, "return", RETURN);
        defineRequestMethod(proto, top, "throw", THROW);
        proto.defineProperty(SymbolKey.TO_STRING_TAG, "AsyncGenerator", DONTENUM | READONLY);
        return (Scriptable) top.associateValue(ASYNC_GENERATOR_PROTOTYPE, proto);
    }

    private static void defineRequestMethod(
            NativeObject proto, TopLevel top, String name, int kind) {
        proto.defineProperty(
                name,
                new LambdaFunction(top, name, 1, (c, s, t, a) -> request(c, top, t, kind, a), false),
                DONTENUM);
    }

    // The requests

    private static Object request(
            Context cx, TopLevel top, Object thisObj, int kind, Object[] args) {
        Object value = args.length > 0 ? args[0] : Undefined.instance;
        if (!(thisObj instanceof NativeAsyncGenerator)) {
            NativePromise promise = NativePromise.create(top);
            promise.reject(
                    cx,
                    top,
                    ScriptRuntime.newNativeError(
                            cx,
                            top,
                            TopLevel.NativeErrors.TypeError,
                            new Object[] {"not an async generator"}));
            return promise;
        }
        return ((NativeAsyncGenerator) thisObj).enqueue(cx, kind, value);
    }

    private NativePromise enqueue(Context cx, int kind, Object value) {
        NativePromise promise = NativePromise.create(scope);
        if (kind == THROW && state == State.SUSPENDED_START) {
            state = State.COMPLETED;
        }
        if (state == State.COMPLETED && queue.isEmpty()) {
            if (kind == NEXT) {
                promise.resolve(cx, scope, iteratorResult(cx, Undefined.instance, true));
                return promise;
            }
            if (kind == THROW) {
                promise.reject(cx, scope, value);
                return promise;
            }
        }
        queue.add(new Request(kind, value, promise));
        if (state == State.SUSPENDED_START || state == State.SUSPENDED_YIELD) {
            resumeNext(cx);
        } else if (state == State.COMPLETED) {
            drain(cx);
        }
        return promise;
    }

    /** Resumes the body with the request at the head of the queue. */
    private void resumeNext(Context cx) {
        Request request = queue.peek();
        if (request == null) {
            return;
        }
        if (request.kind == RETURN) {
            if (state == State.SUSPENDED_START) {
                state = State.COMPLETED;
                drain(cx);
                return;
            }
            // A return at a yield awaits its value first
            state = State.EXECUTING;
            await(
                    cx,
                    request.value,
                    v -> resumeOrDelegate(Context.getCurrentContext(), RETURN, v),
                    r -> resumeOrDelegate(Context.getCurrentContext(), THROW, r));
            return;
        }
        resumeOrDelegate(cx, request.kind, request.value);
    }

    private void resumeOrDelegate(Context cx, int op, Object value) {
        if (delegate != null) {
            delegate(cx, op, value);
        } else {
            resume(cx, op, value);
        }
    }

    private void resume(Context cx, int op, Object value) {
        if (!ScriptRuntime.hasTopCall(cx)) {
            ScriptRuntime.doTopCall(
                    (c, s, t, a) -> {
                        resume(c, op, value);
                        return Undefined.instance;
                    },
                    cx,
                    scope,
                    null,
                    ScriptRuntime.emptyArgs,
                    true);
            return;
        }
        state = State.EXECUTING;
        Scriptable result;
        try {
            result = generator.resumeAsync(cx, scope, op, value);
        } catch (RhinoException e) {
            complete(cx, false, NativePromise.getErrorObject(cx, scope, e), true);
            return;
        }
        Object resultValue = ScriptableObject.getProperty(result, ES6Iterator.VALUE_PROPERTY);
        if (resultValue == Scriptable.NOT_FOUND) {
            resultValue = Undefined.instance;
        }
        if (ScriptRuntime.isIteratorDone(cx, result)) {
            // The body awaited what it returned
            complete(cx, true, resultValue, true);
        } else if (resultValue instanceof YieldStarRequest) {
            try {
                delegate =
                        AsyncIteration.create(cx, scope, ((YieldStarRequest) resultValue).value);
            } catch (RhinoException e) {
                resume(cx, THROW, NativePromise.getErrorObject(cx, scope, e));
                return;
            }
            delegate(cx, NEXT, Undefined.instance);
        } else if (resultValue instanceof AwaitRequest) {
            await(
                    cx,
                    ((AwaitRequest) resultValue).value,
                    v -> resume(Context.getCurrentContext(), NEXT, v),
                    r -> resume(Context.getCurrentContext(), THROW, r));
        } else {
            // A yield awaits its value too, and rejection is thrown back at the yield
            await(
                    cx,
                    resultValue,
                    v -> yielded(Context.getCurrentContext(), v),
                    r -> resume(Context.getCurrentContext(), THROW, r));
        }
    }

    /** Passes a request on to the yield* delegate, as in the async case of yield* evaluation. */
    private void delegate(Context cx, int op, Object value) {
        state = State.EXECUTING;
        AsyncIteration iteration = delegate;
        Object result;
        try {
            if (op == NEXT) {
                result = iteration.callNext(cx, scope, new Object[] {value});
            } else if (op == THROW) {
                result = iteration.callThrow(cx, scope, value);
                if (result == AsyncIteration.NO_THROW) {
                    // Close the delegate, then throw for the missing method
                    delegate = null;
                    Object closed = iteration.callReturn(cx, scope, null);
                    if (closed == AsyncIteration.NO_RETURN) {
                        resume(cx, THROW, delegateError("msg.yield.star.no.throw", null));
                        return;
                    }
                    await(
                            cx,
                            closed,
                            r ->
                                    resume(
                                            Context.getCurrentContext(),
                                            THROW,
                                            isObject(r)
                                                    ? delegateError("msg.yield.star.no.throw", null)
                                                    : delegateError(
                                                            "msg.iterator.result.primitive", r)),
                            r -> resume(Context.getCurrentContext(), THROW, r));
                    return;
                }
            } else {
                result = iteration.callReturn(cx, scope, new Object[] {value});
                if (result == AsyncIteration.NO_RETURN) {
                    delegate = null;
                    await(
                            cx,
                            value,
                            v -> resume(Context.getCurrentContext(), RETURN, v),
                            r -> resume(Context.getCurrentContext(), THROW, r));
                    return;
                }
            }
        } catch (RhinoException e) {
            delegate = null;
            resume(cx, THROW, NativePromise.getErrorObject(cx, scope, e));
            return;
        }
        await(
                cx,
                result,
                r -> delegated(Context.getCurrentContext(), op, r),
                r -> {
                    delegate = null;
                    resume(Context.getCurrentContext(), THROW, r);
                });
    }

    /** Takes the awaited result of the delegate, yielding its value unless it is done. */
    private void delegated(Context cx, int op, Object result) {
        boolean done;
        Object value;
        try {
            if (!isObject(result)) {
                throw ScriptRuntime.typeErrorById(
                        "msg.iterator.result.primitive", ScriptRuntime.toString(result));
            }
            done = ScriptRuntime.isIteratorDone(cx, result);
            if (!done) {
                value =
                        ScriptRuntime.getObjectPropNoWarn(
                                result, ES6Iterator.VALUE_PROPERTY, cx, scope);
                // Unlike yield, yield* does not await the values it yields
                yielded(cx, value);
                return;
            }
            value =
                    ScriptRuntime.getObjectPropNoWarn(
                            result, ES6Iterator.VALUE_PROPERTY, cx, scope);
        } catch (RhinoException e) {
            delegate = null;
            resume(cx, THROW, NativePromise.getErrorObject(cx, scope, e));
            return;
        }
        delegate = null;
        if (op == RETURN) {
            await(
                    cx,
                    value,
                    v -> resume(Context.getCurrentContext(), RETURN, v),
                    r -> resume(Context.getCurrentContext(), THROW, r));
        } else {
            resume(cx, NEXT, value);
        }
    }

    /** Creates a TypeError, with the value that caused it or null. */
    private Object delegateError(String messageId, Object value) {
        String message =
                value == null
                        ? ScriptRuntime.getMessageById(messageId)
                        : ScriptRuntime.getMessageById(messageId, ScriptRuntime.toString(value));
        return ScriptRuntime.newNativeError(
                Context.getCurrentContext(),
                scope,
                TopLevel.NativeErrors.TypeError,
                new Object[] {message});
    }

    private static boolean isObject(Object value) {
        return value instanceof Scriptable && !Undefined.isUndefined(value);
    }

    private void yielded(Context cx, Object value) {
        Request request = queue.poll();
        state = State.SUSPENDED_YIELD;
        request.promise.resolve(cx, scope, iteratorResult(cx, value, false));
        if (!queue.isEmpty()) {
            resumeNext(cx);
        }
    }

    /** Settles the head request once the body completed, and then the rest of the queue. */
    private void complete(Context cx, boolean fulfilled, Object value, boolean done) {
        state = State.COMPLETED;
        Request request = queue.poll();
        if (request != null) {
            if (fulfilled) {
                request.promise.resolve(cx, scope, iteratorResult(cx, value, done));
            } else {
                request.promise.reject(cx, scope, value);
            }
        }
        drain(cx);
    }

    /** Settles the requests queued after the body completed, as in AsyncGeneratorDrainQueue. */
    private void drain(Context cx) {
        while (!queue.isEmpty()) {
            Request request = queue.peek();
            if (request.kind == RETURN) {
                state = State.AWAITING_RETURN;
                await(
                        cx,
                        request.value,
                        v -> complete(Context.getCurrentContext(), true, v, true),
                        r -> complete(Context.getCurrentContext(), false, r, true));
                return;
            }
            queue.poll();
            if (request.kind == THROW) {
                request.promise.reject(cx, scope, request.value);
            } else {
                request.promise.resolve(cx, scope, iteratorResult(cx, Undefined.instance, true));
            }
        }
    }

    private void await(
            Context cx,
            Object value,
            NativePromise.ResultCallback onFulfilled,
            NativePromise.ResultCallback onRejected) {
        NativePromise promise;
        try {
            promise = AsyncFunctionCode.promiseResolve(cx, scope, value);
        } catch (RhinoException e) {
            onRejected.accept(NativePromise.getErrorObject(cx, scope, e));
            return;
        }
        promise.onFulfill(cx, onFulfilled);
        promise.onReject(cx, onRejected);
    }

    private Scriptable iteratorResult(Context cx, Object value, boolean done) {
        return ES6Iterator.makeIteratorResult(cx, scope, done, value);
    }
}
