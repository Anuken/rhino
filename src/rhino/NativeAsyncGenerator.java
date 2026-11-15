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
                    v -> resume(Context.getCurrentContext(), RETURN, v),
                    r -> resume(Context.getCurrentContext(), THROW, r));
            return;
        }
        resume(cx, request.kind, request.value);
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
            // A return awaits its value
            await(
                    cx,
                    resultValue,
                    v -> complete(Context.getCurrentContext(), true, v, true),
                    r -> complete(Context.getCurrentContext(), false, r, true));
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
