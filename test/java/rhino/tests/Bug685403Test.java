/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rhino.Context;
import rhino.Context.EvaluationMethod;
import rhino.ContinuationPending;
import rhino.Function;
import rhino.Script;
import rhino.Scriptable;
import rhino.ScriptableObject;
import rhino.TopLevel;

/**
 * @author André Bargull
 */
public class Bug685403Test {

    private Context cx;
    private TopLevel scope;

    @BeforeEach
    public void setUp() {
        cx = Context.enter();
        cx.setEvaluationMethod(EvaluationMethod.Interpreter);
        scope = cx.initStandardObjects();
    }

    @AfterEach
    public void tearDown() {
        Context.exit();
    }

    public static Object continuation(
            Context cx, Scriptable thisObj, Object[] args, Function funObj) {
        ContinuationPending pending = cx.captureContinuation();
        throw pending;
    }

    @Test
    public void test() {
        String source = "var state = '';";
        source += "function A(){state += 'A'}";
        source += "function B(){state += 'B'}";
        source += "function C(){state += 'C'}";
        source += "try { A(); continuation(); B() } finally { C() }";
        source += "state";

        String[] functions = new String[] {"continuation"};
        scope.defineFunctionProperties(
                scope, functions, Bug685403Test.class, ScriptableObject.DONTENUM);

        Object state = null;
        Script script = cx.compileString(source, "", 1);
        try {
            cx.executeScriptWithContinuations(script, scope);
            fail("expected ContinuationPending exception");
        } catch (ContinuationPending pending) {
            state = cx.resumeContinuation(pending.getContinuation(), scope, "");
        }
        assertEquals("ABC", state);
    }
}
