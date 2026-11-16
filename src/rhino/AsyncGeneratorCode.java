/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

/**
 * The code of an async generator function. Its body is compiled as a generator, and a call wraps
 * that generator in a {@link NativeAsyncGenerator}.
 */
final class AsyncGeneratorCode extends JSCode<JSFunction> {
    final JSCode<JSFunction> body;

    AsyncGeneratorCode(JSCode<JSFunction> body) {
        this.body = body;
    }

    @Override
    public Object execute(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        // Unlike in an async function, an error initializing the parameters is thrown
        Object generator = body.execute(cx, f, nt, s, thisObj, args);
        return new NativeAsyncGenerator(f.getDeclarationScope(), f, (ES6Generator) generator);
    }

    @Override
    public Object resume(Context cx, JSFunction f, Object state, VarScope s, int op, Object v) {
        return body.resume(cx, f, state, s, op, v);
    }
}
