/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

/**
 * The code of an async function. Its body is compiled as a generator whose yields are the
 * function's awaits: a call creates the generator and a promise, and then resumes the generator
 * each time an awaited value settles, until the body completes and settles the promise.
 */
final class AsyncFunctionCode extends JSCode<JSFunction> {
    final JSCode<JSFunction> body;

    AsyncFunctionCode(JSCode<JSFunction> body) {
        this.body = body;
    }

    @Override
    public Object execute(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        VarScope scope = ScriptableObject.getTopLevelScope(f.getDeclarationScope());
        NativePromise promise = NativePromise.create(scope);
        Object generator;
        try {
            // Runs the parameter initialization, which rejects rather than throws
            generator = body.execute(cx, f, nt, s, thisObj, args);
        } catch (RhinoException e) {
            promise.reject(cx, scope, NativePromise.getErrorObject(cx, scope, e));
            return promise;
        }
        new Run(scope, promise, (ES6Generator) generator)
                .step(cx, NativeGenerator.GENERATOR_SEND, Undefined.instance);
        return promise;
    }

    @Override
    public Object resume(Context cx, JSFunction f, Object state, VarScope s, int op, Object v) {
        return body.resume(cx, f, state, s, op, v);
    }

    /** One call of an async function. */
    private static final class Run {
        private final VarScope scope;
        private final NativePromise promise;
        private final ES6Generator generator;

        Run(VarScope scope, NativePromise promise, ES6Generator generator) {
            this.scope = scope;
            this.promise = promise;
            this.generator = generator;
        }

        /** Resumes the body with the value an await settled with, sent or thrown. */
        void step(Context cx, int op, Object value) {
            if (!ScriptRuntime.hasTopCall(cx)) {
                // Resumed from the microtask queue, outside of any script
                ScriptRuntime.doTopCall(
                        (c, s, t, a) -> {
                            step(c, op, value);
                            return Undefined.instance;
                        },
                        cx,
                        scope,
                        null,
                        ScriptRuntime.emptyArgs,
                        true);
                return;
            }
            Scriptable result;
            try {
                result = generator.resumeAsync(cx, scope, op, value);
            } catch (RhinoException e) {
                promise.reject(cx, scope, NativePromise.getErrorObject(cx, scope, e));
                return;
            }
            Object resultValue = ScriptableObject.getProperty(result, ES6Iterator.VALUE_PROPERTY);
            if (resultValue == Scriptable.NOT_FOUND) {
                resultValue = Undefined.instance;
            }
            if (ScriptRuntime.isIteratorDone(cx, result)) {
                promise.resolve(cx, scope, resultValue);
                return;
            }
            NativePromise awaited = promiseResolve(cx, scope, resultValue);
            awaited.onFulfill(
                    cx, r -> step(Context.getCurrentContext(), NativeGenerator.GENERATOR_SEND, r));
            awaited.onReject(
                    cx, r -> step(Context.getCurrentContext(), NativeGenerator.GENERATOR_THROW, r));
        }
    }

    /** PromiseResolve(%Promise%, value). */
    static NativePromise promiseResolve(Context cx, VarScope scope, Object value) {
        scope = ScriptableObject.getTopLevelScope(scope);
        if (value instanceof NativePromise) {
            Object ctor = ScriptableObject.getProperty((Scriptable) value, "constructor");
            if (ctor == TopLevel.getBuiltinCtor(cx, scope, TopLevel.Builtins.Promise)) {
                return (NativePromise) value;
            }
        }
        NativePromise promise = NativePromise.create(scope);
        promise.resolve(cx, scope, value);
        return promise;
    }
}
