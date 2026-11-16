/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.tests.es6;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import rhino.Context;
import rhino.ScriptableObject;
import rhino.TopLevel;
import rhino.testutils.Utils;

/** Class behavior that test262 does not cover: interplay with ES5 code and with Java. */
public class ClassTest {

    /** Implemented by script classes in {@link #instanceImplementsJavaInterface}. */
    public interface Named {
        String name();

        int size();
    }

    public static String describe(Named named) {
        return named.name() + ":" + named.size();
    }

    @Test
    public void extendsEs5Constructor() {
        Utils.assertWithAllModes_ES6(
                "hi 7!|true|true|a,b|Old",
                Utils.lines(
                        "function Old(a) { this.a = a; }",
                        "Old.prototype.hi = function() { return 'hi ' + this.a; };",
                        "class New extends Old {",
                        "  constructor() { super(7); this.b = 1; }",
                        "  hi() { return super.hi() + '!'; }",
                        "}",
                        "class Newer extends New {}",
                        "var n = new Newer();",
                        "[n.hi(), n instanceof Old, Object.getPrototypeOf(Newer) === New,",
                        " Object.keys(n), Object.getPrototypeOf(New.prototype).constructor.name]",
                        "  .join('|')"));
    }

    @Test
    public void thisInDerivedConstructors() {
        Utils.assertWithAllModes_ES6(
                "ReferenceError|true|true|2|ReferenceError|5",
                Utils.lines(
                        "var out = [];",
                        "class A { constructor() { this.x = 1; } }",
                        "class B extends A {",
                        "  f = () => this.x + 1;",
                        "  constructor() {",
                        "    var early = () => this;",
                        "    try { this.y = 0; } catch (e) { out.push(e.name); }",
                        "    super();",
                        "    out.push(early() === this);",
                        "    try { super(); } catch (e) { out.push(e.name === 'ReferenceError'); }",
                        "  }",
                        "}",
                        "out.push(new B().f());",
                        "class C extends A { constructor() { } }",
                        "try { new C(); } catch (e) { out.push(e.name); }",
                        "class D extends A { constructor() { return {v: 5}; } }",
                        "out.push(new D().v);",
                        "out.join('|')"));
    }

    @Test
    public void privateMembers() {
        Utils.assertWithAllModes_ES6(
                "4,40,m4,true,false|TypeError|1",
                Utils.lines(
                        "class P {",
                        "  #x = 1; static #n = 0;",
                        "  #m() { return 'm' + this.#x; }",
                        "  get #g() { return this.#x * 10; }",
                        "  set #g(v) { this.#x = v; }",
                        "  test() {",
                        "    this.#x++; this.#x += 1; [this.#x] = [this.#x + 1];",
                        "    P.#n++;",
                        "    return [this.#x, this.#g, this.#m(), #x in this, #x in {}].join();",
                        "  }",
                        "  static count() { return P.#n; }",
                        "}",
                        "var out = [new P().test()];",
                        "try { P.prototype.test.call({}); } catch (e) { out.push(e.name); }",
                        "out.push(P.count());",
                        "out.join('|')"));
    }

    @Test
    public void extendsBuiltIns() {
        Utils.assertWithAllModes_ES6(
                "6|true|E: boom|true|2",
                Utils.lines(
                        "class Arr extends Array { sum() { return this.reduce((a, b) => a + b, 0); } }",
                        "var a = new Arr(); a.push(1, 2, 3);",
                        "class E extends Error { constructor(m) { super(m); this.name = 'E'; } }",
                        "class M extends Map { constructor() { super([[1, 2]]); } }",
                        "[a.sum(), Array.isArray(a), String(new E('boom')), new E() instanceof Error,",
                        " new M().get(1)].join('|')"));
    }

    @Test
    public void errors() {
        Utils.assertWithAllModes_ES6(
                "TypeError: Class constructor A cannot be invoked without \"new\""
                        + "|TypeError|TypeError",
                Utils.lines(
                        "var out = [];",
                        "class A {}",
                        "try { A(); } catch (e) { out.push(String(e)); }",
                        "try { class B extends java.lang.Object {} } catch (e) { out.push(e.name); }",
                        "try { class C extends Math.max {} } catch (e) { out.push(e.name); }",
                        "out.join('|')"));
    }

    @Test
    public void staticMembersAndToString() {
        Utils.assertWithAllModes_ES6(
                "S|2|11|class S { static x = 1; static { this.y = S.x + 10; } }|m() { return 1; }",
                Utils.lines(
                        "class S { static x = 1; static { this.y = S.x + 10; } }",
                        "class T extends S { static two() { return super.x + 1; } m() { return 1; } }",
                        "[S.name, T.two(), T.y, S.toString(), T.prototype.m.toString()].join('|')"));
    }

    @Test
    public void instanceImplementsJavaInterface() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ES6);
                    TopLevel scope = cx.initStandardObjects();
                    Object result =
                            cx.evaluateString(
                                    scope,
                                    Utils.lines(
                                            "var Test = Packages.rhino.tests.es6.ClassTest;",
                                            "class Base { size() { return this.items.length; } }",
                                            "class Box extends Base {",
                                            "  #label;",
                                            "  items = [1, 2];",
                                            "  constructor(label) { super(); this.#label = label; }",
                                            "  get label() { return this.#label; }",
                                            "  name() { return this.label; }",
                                            "  size() { return super.size() + 1; }",
                                            "}",
                                            "class Desc { constructor(d) { this.d = d; }",
                                            "  compare(a, b) { return this.d ? b - a : a - b; } }",
                                            "var list = new java.util.ArrayList();",
                                            "list.add(1); list.add(3); list.add(2);",
                                            "java.util.Collections.sort(list, new Desc(true));",
                                            "Test.describe(new Box('box')) + '|' + list"),
                                    "test.js",
                                    1);
                    assertEquals("box:3|[3.0, 2.0, 1.0]", Context.toString(result));
                    return null;
                });
    }

    @Test
    public void instanceUsedFromJava() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ES6);
                    TopLevel scope = cx.initStandardObjects();
                    cx.evaluateString(
                            scope,
                            Utils.lines(
                                    "class Counter {",
                                    "  #n = 0;",
                                    "  get value() { return this.#n; }",
                                    "  inc() { return ++this.#n; }",
                                    "}",
                                    "var counter = new Counter();"),
                            "test.js",
                            1);
                    ScriptableObject counter = (ScriptableObject) scope.get("counter", scope);
                    ScriptableObject.callMethod(counter, "inc", new Object[0]);
                    ScriptableObject.callMethod(counter, "inc", new Object[0]);
                    assertEquals(
                            2,
                            ((Number) ScriptableObject.getProperty(counter, "value")).intValue());
                    return null;
                });
    }
}
