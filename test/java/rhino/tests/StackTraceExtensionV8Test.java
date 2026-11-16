package rhino.tests;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.FileReader;
import java.io.IOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rhino.Context;
import rhino.ContextFactory;
import rhino.RhinoException;
import rhino.StackStyle;
import rhino.VarScope;
import rhino.testutils.TestSource;
import rhino.testutils.Utils;
import rhino.tools.shell.Global;

public class StackTraceExtensionV8Test {
    @BeforeAll
    public static void init() {
        RhinoException.setStackStyle(StackStyle.V8);
    }

    @AfterAll
    public static void terminate() {
        RhinoException.setStackStyle(StackStyle.RHINO);
    }

    private void testTraces(boolean interpretedMode) {
        final ContextFactory factory =
                Utils.contextFactoryWithFeatures(Context.FEATURE_LOCATION_INFORMATION_IN_ERROR);

        try (Context cx = factory.enterContext()) {
            cx.setLanguageVersion(Context.VERSION_1_8);
            cx.setInterpretedMode(interpretedMode);
            cx.setGeneratingDebug(true);

            Global global = new Global(cx);
            global.setFileLoadPrefix(TestSource.getPrefix());
            VarScope root = cx.newVarEnv(global);

            try (FileReader rdr =
                    new FileReader(
                            TestSource.resolve("testsrc/jstests/extensions/stack-traces-v8.js"))) {
                cx.evaluateReader(root, rdr, "stack-traces-v8.js", 1);
            }
        } catch (IOException ioe) {
            assertFalse(true, "I/O Error: " + ioe);
        }
    }

    @Test
    public void stackTraceInterpreted() {
        testTraces(true);
    }

    @Test
    public void stackTraceCompiled() {
        testTraces(false);
    }
}
