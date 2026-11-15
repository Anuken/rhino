/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.tests.es6;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import rhino.Context;
import rhino.NativePromise;
import rhino.ScriptableObject;
import rhino.TopLevel;
import rhino.testutils.Utils;

/** Async functions as mod code uses them; test262 covers the language semantics. */
public class AsyncFunctionTest {

    /** Runs a script, whose microtasks run once it returns, then returns the value of "log". */
    private static void assertLog(String expected, String script) {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ES6);
                    TopLevel scope = cx.initStandardObjects();
                    cx.evaluateString(scope, "var log = [];\n" + script, "test.js", 1, null);
                    Object log = cx.evaluateString(scope, "log.join('|')", "log.js", 1, null);
                    assertEquals(expected, Context.toString(log));
                    return null;
                });
    }

    @Test
    public void awaitsInClassesAndCalls() {
        assertLog(
                "start|sync|loaded 3|caught boom|sum 6",
                Utils.lines(
                        "class Loader {",
                        "  #cache = new Map();",
                        "  async load(name) {",
                        "    if (!this.#cache.has(name)) this.#cache.set(name, await Promise.resolve(name.length));",
                        "    return this.#cache.get(name);",
                        "  }",
                        "}",
                        "class Strict extends Loader {",
                        "  async load(name) {",
                        "    if (!name) throw new Error('boom');",
                        "    return super.load(name);",
                        "  }",
                        "}",
                        "async function main() {",
                        "  log.push('start');",
                        "  var l = new Strict();",
                        "  log.push('loaded ' + await l.load('abc'));",
                        "  try { await l.load(''); } catch (e) { log.push('caught ' + e.message); }",
                        "  var sum = 0;",
                        "  for (const x of [1, 2, 3]) sum += Math.max(await x, 0);",
                        "  log.push('sum ' + sum);",
                        "}",
                        "main();",
                        "log.push('sync');"));
    }

    @Test
    public void promiseResolvedFromJava() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ES6);
                    TopLevel scope = cx.initStandardObjects();
                    NativePromise promise = NativePromise.create(scope);
                    ScriptableObject.putProperty(scope, "javaPromise", promise);
                    cx.evaluateString(
                            scope,
                            "var log = []; (async () => log.push(await javaPromise + 1))();",
                            "test.js",
                            1,
                            null);
                    // Resolved later, as an asset loaded on another frame would be
                    promise.resolve(cx, scope, 41);
                    cx.processMicrotasks();
                    Object log = cx.evaluateString(scope, "log.join()", "log.js", 1, null);
                    assertEquals("42", Context.toString(log));
                    return null;
                });
    }
}
