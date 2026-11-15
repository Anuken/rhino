package rhino;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import rhino.Context.EvaluationMethod;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Script stacks of errors thrown and created in interpreted code must be the same whether or not
 * the Java stack trace was captured, which is skipped while no compiled scripts exist.
 */
public class InterpreterStackCaptureTest {
    private static final String SOURCE =
            "function inner() { throw new Error('boom'); }\n"
                    + "function outer() { [1].forEach(function each() { inner(); }); }\n"
                    + "var created = (function make() { return new Error('made'); })();\n"
                    + "try { outer(); } catch (e) { e; }";

    @Test
    public void scriptStackWithoutJavaStack() {
        String[] withJavaStack = run(true);
        String[] withoutJavaStack = run(false);
        for (int i = 0; i < withJavaStack.length; i++) {
            assertEquals(withJavaStack[i], withoutJavaStack[i]);
        }
        assertTrue(withoutJavaStack[0].contains("inner"), withoutJavaStack[0]);
        assertTrue(withoutJavaStack[0].contains("each"), withoutJavaStack[0]);
        assertTrue(withoutJavaStack[0].contains("outer"), withoutJavaStack[0]);
        assertTrue(withoutJavaStack[1].contains("make"), withoutJavaStack[1]);
    }

    /** Returns the stack of a caught error, of a created error, and a thrown exception's stack. */
    private static String[] run(boolean javaStack) {
        boolean saved = RhinoException.compiledCodeCreated;
        RhinoException.compiledCodeCreated = javaStack;
        try (Context cx = new ContextFactory().enterContext()) {
            cx.setEvaluationMethod(EvaluationMethod.Interpreter);
            TopLevel scope = cx.initStandardObjects();
            Scriptable caught = (Scriptable) cx.evaluateString(scope, SOURCE, "test.js", 1, null);
            Scriptable created = (Scriptable) scope.get("created", scope);
            String thrown;
            try {
                cx.evaluateString(scope, "outer();", "throw.js", 1, null);
                throw new AssertionError();
            } catch (JavaScriptException e) {
                assertEquals(javaStack, e.getStackTrace().length > 0);
                StringWriter printed = new StringWriter();
                e.printStackTrace(new PrintWriter(printed));
                assertTrue(printed.toString().contains("at script.inner(test.js:1)"));
                thrown = e.getScriptStackTrace();
            }
            return new String[] {
                (String) caught.get("stack", caught), (String) created.get("stack", created), thrown
            };
        } finally {
            RhinoException.compiledCodeCreated = saved;
        }
    }
}
