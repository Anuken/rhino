package rhino.tests.mindustry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import rhino.Context;
import rhino.ContextFactory;
import rhino.ImporterTopLevel;
import rhino.NativeJavaClass;
import rhino.Undefined;
import rhino.Wrapper;
import rhino.testutils.Utils;

/**
 * Runs scripts the way Mindustry does: an {@link ImporterTopLevel} with the default language
 * version, in both interpreted and compiled mode. {@link Fixtures} is available as "F", and
 * Mindustry's {@code extend()} helper from core/assets/scripts/global.js is defined.
 */
final class MindustryScripts {
    /** Mindustry's extend(): js 'extend(Base, ..., {})' = java 'new Base(...) {}'. */
    static final String EXTEND =
            "function extend(){\n"
                    + "  const Base = arguments[0];\n"
                    + "  const def = arguments[arguments.length - 1];\n"
                    + "  const args = [Base, def].concat(Array.from(arguments).splice(1, arguments.length - 2));\n"
                    + "  const instance = JavaAdapter.apply(null, args);\n"
                    + "  for(var i in def){ if(typeof(def[i]) != 'function'){ instance[i] = def[i]; } }\n"
                    + "  return instance;\n"
                    + "}\n";

    private MindustryScripts() {}

    /** Evaluates the parts in order in one scope and returns the last result as a string. */
    static String eval(ContextFactory factory, Context.EvaluationMethod mode, String... parts) {
        String[] result = new String[1];
        Utils.runWithMode(
                factory,
                cx -> {
                    ImporterTopLevel scope = new ImporterTopLevel(cx);
                    scope.put("F", scope, new NativeJavaClass(scope, Fixtures.class));
                    cx.evaluateString(scope, EXTEND, "extend.js", 1, null);
                    Object r = null;
                    for (String part : parts) {
                        r = cx.evaluateString(scope, part, "test.js", 1, null);
                    }
                    if (r instanceof Wrapper) r = ((Wrapper) r).unwrap();
                    result[0] =
                            r instanceof Undefined
                                    ? "undefined"
                                    : r instanceof String || r == null
                                            ? String.valueOf(r)
                                            : r instanceof Number || r instanceof Boolean
                                                    ? Context.toString(r)
                                                    : r.toString();
                    return null;
                },
                mode);
        return result[0];
    }

    /** Asserts the result of the parts in interpreted and compiled mode. */
    static void assertBoth(String expected, String... parts) {
        assertBoth(new ContextFactory(), expected, parts);
    }

    static void assertBoth(ContextFactory factory, String expected, String... parts) {
        for (Context.EvaluationMethod mode : Context.EvaluationMethod.values()) {
            assertEquals(expected, eval(factory, mode, parts), mode.name());
        }
    }

    /** Asserts that the parts throw in interpreted and compiled mode. */
    static void assertThrowsBoth(Class<? extends Throwable> type, String... parts) {
        for (Context.EvaluationMethod mode : Context.EvaluationMethod.values()) {
            assertThrows(type, () -> eval(new ContextFactory(), mode, parts), mode.name());
        }
    }
}
