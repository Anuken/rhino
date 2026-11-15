/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rhino.ConsString;
import rhino.Context;
import rhino.Context.EvaluationMethod;
import rhino.ContextFactory;
import rhino.ContinuationPending;
import rhino.Function;
import rhino.Script;
import rhino.TopLevel;
import rhino.WrappedException;

/**
 * Test of new API functions for running and resuming scripts containing continuations, and for
 * suspending a continuation from a Java method called from JavaScript.
 *
 * @author Norris Boyd
 */
public class ContinuationsApiTest {
    private static final class SerializationEnabledFactory extends ContextFactory {
        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            if (featureIndex == Context.FEATURE_JAVA_SERIALIZATION) {
                return true;
            }
            return super.hasFeature(cx, featureIndex);
        }
    }

    private static final ContextFactory factory = new SerializationEnabledFactory();

    TopLevel globalScope;

    public static class MyClass implements Serializable {

        private static final long serialVersionUID = 4189002778806232070L;

        public int f(int a) {
            try (Context cx = factory.enterContext()) {
                ContinuationPending pending = cx.captureContinuation();
                pending.setApplicationState(a);
                throw pending;
            }
        }

        public int g(int a) {
            try (Context cx = factory.enterContext()) {
                ContinuationPending pending = cx.captureContinuation();
                pending.setApplicationState(2 * a);
                throw pending;
            }
        }

        public String h() {
            try (Context cx = factory.enterContext()) {
                ContinuationPending pending = cx.captureContinuation();
                pending.setApplicationState("2*3");
                throw pending;
            }
        }

        public void directThrow() {
            try (Context cx = factory.enterContext()) {
                throw cx.captureContinuation();
            }
        }
    }

    @BeforeEach
    public void setUp() {
        try (Context cx = factory.enterContext()) {
            globalScope = cx.initStandardObjects();
            cx.setEvaluationMethod(EvaluationMethod.Interpreter); // must use interpreter mode
            globalScope.put("myObject", globalScope, Context.javaToJS(new MyClass(), globalScope));
        }
    }

    @Test
    public void scriptWithContinuations() {
        try (Context cx = factory.enterContext()) {
            try {
                cx.setEvaluationMethod(EvaluationMethod.Interpreter); // must use interpreter mode
                Script script = cx.compileString("myObject.f(3) + 1;", "test source", 1, null);
                cx.executeScriptWithContinuations(script, globalScope);
                fail("Should throw ContinuationPending");
            } catch (ContinuationPending pending) {
                Object applicationState = pending.getApplicationState();
                assertEquals(Integer.valueOf(3), applicationState);
                int saved = (Integer) applicationState;
                Object result =
                        cx.resumeContinuation(pending.getContinuation(), globalScope, saved + 1);
                assertEquals(5, ((Number) result).intValue());
            }
        }
    }

    @Test
    public void scriptWithMultipleContinuations() {
        try (Context cx = factory.enterContext()) {
            try {
                cx.setEvaluationMethod(EvaluationMethod.Interpreter); // must use interpreter mode
                Script script =
                        cx.compileString(
                                "myObject.f(3) + myObject.g(3) + 2;", "test source", 1, null);
                cx.executeScriptWithContinuations(script, globalScope);
                fail("Should throw ContinuationPending");
            } catch (ContinuationPending pending) {
                try {
                    Object applicationState = pending.getApplicationState();
                    assertEquals(Integer.valueOf(3), applicationState);
                    int saved = (Integer) applicationState;
                    cx.resumeContinuation(pending.getContinuation(), globalScope, saved + 1);
                    fail("Should throw another ContinuationPending");
                } catch (ContinuationPending pending2) {
                    Object applicationState2 = pending2.getApplicationState();
                    assertEquals(Integer.valueOf(6), applicationState2);
                    int saved2 = (Integer) applicationState2;
                    Object result2 =
                            cx.resumeContinuation(
                                    pending2.getContinuation(), globalScope, saved2 + 1);
                    assertEquals(13, ((Number) result2).intValue());
                }
            }
        }
    }

    @Test
    public void scriptWithNestedContinuations() {
        try (Context cx = factory.enterContext()) {
            try {
                cx.setEvaluationMethod(EvaluationMethod.Interpreter);
                Script script =
                        cx.compileString(
                                "myObject.g( myObject.f(1) ) + 2;", "test source", 1, null);
                cx.executeScriptWithContinuations(script, globalScope);
                fail("Should throw ContinuationPending");
            } catch (ContinuationPending pending) {
                try {
                    Object applicationState = pending.getApplicationState();
                    assertEquals(Integer.valueOf(1), applicationState);
                    int saved = (Integer) applicationState;
                    cx.resumeContinuation(pending.getContinuation(), globalScope, saved + 1);
                    fail("Should throw another ContinuationPending");
                } catch (ContinuationPending pending2) {
                    Object applicationState2 = pending2.getApplicationState();
                    assertEquals(Integer.valueOf(4), applicationState2);
                    int saved2 = (Integer) applicationState2;
                    Object result2 =
                            cx.resumeContinuation(
                                    pending2.getContinuation(), globalScope, saved2 + 2);
                    assertEquals(8, ((Number) result2).intValue());
                }
            }
        }
    }

    @Test
    public void functionWithContinuations() {
        try (Context cx = factory.enterContext()) {
            try {
                cx.setEvaluationMethod(EvaluationMethod.Interpreter);
                cx.evaluateString(
                        globalScope,
                        "function f(a) { return myObject.f(a); }",
                        "function test source",
                        1,
                        null);
                Function f = (Function) globalScope.get("f", globalScope);
                Object[] args = {7};
                cx.callFunctionWithContinuations(f, globalScope, args);
                fail("Should throw ContinuationPending");
            } catch (ContinuationPending pending) {
                Object applicationState = pending.getApplicationState();
                assertEquals(7, ((Number) applicationState).intValue());
                int saved = (Integer) applicationState;
                Object result =
                        cx.resumeContinuation(pending.getContinuation(), globalScope, saved + 1);
                assertEquals(8, ((Number) result).intValue());
            }
        }
    }

    /**
     * Since a continuation can only capture JavaScript frames and not Java frames, ensure that
     * Rhino throws an exception when the JavaScript frames don't reach all the way to the code
     * called by executeScriptWithContinuations or callFunctionWithContinuations.
     */
    @Test
    public void errorOnEvalCall() {
        try (Context cx = factory.enterContext()) {
            cx.setEvaluationMethod(EvaluationMethod.Interpreter); // must use interpreter mode
            Script script = cx.compileString("eval('myObject.f(3);');", "test source", 1, null);
            cx.executeScriptWithContinuations(script, globalScope);
            fail("Should throw IllegalStateException");
        } catch (WrappedException we) {
            Throwable t = we.getWrappedException();
            assertTrue(t instanceof IllegalStateException);
            assertTrue(t.getMessage().startsWith("Cannot capture continuation"));
        }
    }


    /**
     * Method ensureStackLength() has to take care of stack, sDbl, and stackAttributes. see PR#1815
     */
    @Test
    public void ensureStackLength() {
        String jsSource =
                "let array1 = [];\n"
                        + "let array2 = [];\n"
                        // add enough items to inc stack size
                        + "for (let i = 0; i < 42; i++) {\n"
                        + "    array1.push(i);\n"
                        + "    array2.push(i);\n"
                        + "}\n"

                        // this will cause ensureStackLength to be called
                        + "Array.prototype.push.apply(array1, array1)\n"
                        // this will cause ArrayIndexOutOfBoundsException
                        + "let select = myObject.directThrow()\n";

        try (Context cx = factory.enterContext()) {
            cx.setEvaluationMethod(EvaluationMethod.Interpreter);
            Script script = cx.compileString(jsSource, "test source", 1, null);
            cx.executeScriptWithContinuations(script, globalScope);
        } catch (ContinuationPending e) {
            e.getContinuation();
        }
    }
}
