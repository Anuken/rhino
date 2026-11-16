package rhino.tests.mindustry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import rhino.Context;
import rhino.ImporterTopLevel;
import rhino.NativeArray;
import rhino.NativeJavaClass;
import rhino.NativeJavaObject;
import rhino.Scriptable;
import rhino.VarScope;
import rhino.WrapFactory;
import rhino.lc.type.TypeInfo;
import rhino.testutils.Utils;

/** The proxy a function is converted to is wrapped as this by the current WrapFactory. */
class InterfaceProxyWrapTest {
    static class Marked extends NativeJavaObject {
        Marked(VarScope scope, Object javaObject, TypeInfo staticType) {
            super(scope, javaObject, staticType);
        }
    }

    @Test
    void thisFollowsWrapFactory() {
        for (Context.EvaluationMethod mode : Context.EvaluationMethod.values()) {
            Utils.runWithMode(
                    cx -> {
                        ImporterTopLevel scope = new ImporterTopLevel(cx);
                        scope.put("F", scope, new NativeJavaClass(scope, Fixtures.class));
                        String run = "F.run(fn); F.run(fn);";
                        cx.evaluateString(
                                scope,
                                "var seen = []; var fn = function(){ seen.push(this); };" + run,
                                "test.js",
                                1);
                        cx.setWrapFactory(
                                new WrapFactory() {
                                    @Override
                                    public Scriptable wrapAsJavaObject(
                                            Context cx,
                                            VarScope scope,
                                            Object javaObject,
                                            TypeInfo staticType) {
                                        return new Marked(scope, javaObject, staticType);
                                    }
                                });
                        cx.evaluateString(scope, run, "test.js", 1);

                        NativeArray seen = (NativeArray) scope.get("seen", scope);
                        assertEquals(4, seen.size());
                        Object proxy = ((NativeJavaObject) seen.get(0, seen)).unwrap();
                        for (int i = 0; i < 4; i++) {
                            NativeJavaObject wrapper = (NativeJavaObject) seen.get(i, seen);
                            assertSame(proxy, wrapper.unwrap());
                            assertEquals(i >= 2, wrapper instanceof Marked, "call " + i);
                        }
                        return null;
                    },
                    mode);
        }
    }
}
