/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.tests.es6;

import org.junit.jupiter.api.Test;
import rhino.testutils.Utils;

/*
 * Tests for GeneratorFunction
 */
public class GeneratorFunctionTest {

    @Test
    public void arguments() {
        String code =
                "function* foo() { yield 'Hello!'; };\n"
                        + "'' + Object.getOwnPropertyDescriptor(foo, 'arguments');";
        Utils.assertWithAllModes_ES6("undefined", code);
    }

    @Test
    public void argumentsAccess() {
        String code =
                "function* foo() { yield 'Hello!'; };\n"
                        + "let res = '';\n"
                        + "try { foo.arguments; res += 'no ex'; } catch (e) { res += e.message; };\n"
                        + "try { foo.arguments = 7; res += ' no ex'; } catch (e) { res += ' ' + e.message; };";
        // todo Utils.assertWithAllModes_ES6("This operation is not allowed. This operation is not
        // allowed.", code);
        Utils.assertWithAllModes_ES6("no ex no ex", code);
    }

    @Test
    public void throwAfterInnerTryCatchIsCaught() {
        String code =
                "function* g() {\n"
                        + "  try {\n"
                        + "    try { yield 1; } catch (e) {}\n"
                        + "    yield 2;\n"
                        + "    throw 'boom';\n"
                        + "  } catch (e) { yield 'caught ' + e; }\n"
                        + "}\n"
                        + "[...g()].join();";
        Utils.assertWithAllModes_ES6("1,2,caught boom", code);
    }

    @Test
    public void yieldInFinallyDuringReturn() {
        String code =
                "function* g() {\n"
                        + "  try {\n"
                        + "    try { yield 1; } finally { yield 'f'; }\n"
                        + "  } catch (e) { yield 'caught'; }\n"
                        + "}\n"
                        + "var it = g(), r = [];\n"
                        + "it.next();\n"
                        + "for (var x of [it.return('R'), it.next(), it.next()]) r.push(x.value + ':' + x.done);\n"
                        + "r.join();";
        Utils.assertWithAllModes_ES6("f:false,R:true,undefined:true", code);
    }

    @Test
    public void yieldInCatchKeepsCatchScope() {
        String code =
                "function* g() {\n"
                        + "  try {\n"
                        + "    for (const x of [1]) { if (x) throw 'T'; return; }\n"
                        + "  } catch (e) { yield 0; yield e; } finally { this.f = 1; }\n"
                        + "}\n"
                        + "[...g()].join();";
        Utils.assertWithAllModes_ES6("0,T", code);
    }

    @Test
    public void yieldSavesOnlyAssignedLocals() {
        String code =
                "function R() {}\n"
                        + "function* g() {\n"
                        + "  try { yield String(new R() instanceof R); } finally {}\n"
                        + "  try { yield ({ a: 1 }).a; } catch (e) { yield e; }\n"
                        + "}\n"
                        + "[...g()].join();";
        Utils.assertWithAllModes_ES6("true,1", code);
    }

    @Test
    public void yieldInSpreadAndLiteral() {
        String code =
                "function* g() {\n"
                        + "  var a = [0, ...(yield 1)];\n"
                        + "  var o = { ...(yield 2), b: yield, c: 1 };\n"
                        + "  return a.join() + ' ' + o.x + o.b + o.c;\n"
                        + "}\n"
                        + "var it = g();\n"
                        + "it.next(); it.next([5]); it.next({ x: 'X' });\n"
                        + "it.next('B').value;";
        Utils.assertWithAllModes_ES6("0,5 XB1", code);
    }

    @Test
    public void returnInFinallyOverridesReturn() {
        String code =
                "function* g() { try { yield 1; } finally { return 'done'; } }\n"
                        + "var it = g(); it.next();\n"
                        + "var r = it.return('R');\n"
                        + "r.value + ':' + r.done + ':' + it.next().done;";
        Utils.assertWithAllModes_ES6("done:true:true", code);
    }
}
