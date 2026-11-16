package rhino.tests.es6;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import rhino.Context;
import rhino.JavaScriptException;
import rhino.TopLevel;
import rhino.testutils.Utils;

class TemporalDeadZoneTest {

    private static final String HARNESS =
            Utils.lines(
                    "var out = [];",
                    "function t(name, f) {",
                    "  try { out.push(name + ':' + f()) }",
                    "  catch (e) { out.push(name + ':' + (e instanceof ReferenceError ? 'TDZ' : e)) }",
                    "}");

    /** Each way of reaching a binding before its declaration runs throws a ReferenceError. */
    @Test
    void referencesBeforeInitializationThrow() {
        Utils.assertWithAllModes_ES6(
                "closure:TDZ,1|closureConst:TDZ|prior:TDZ|self:TDZ|typeof:TDZ|assign:TDZ"
                        + "|inc:TDZ|compound:TDZ|destructure:TDZ|call:TDZ|switch:TDZ"
                        + "|forOfHead:TDZ|forInHead:TDZ|extends:TDZ|computedKey:TDZ"
                        + "|classBefore:TDZ|iife:TDZ|eval:TDZ|defaultLater:TDZ|defaultSelf:TDZ"
                        + "|loop:0,0,1",
                Utils.lines(
                        HARNESS,
                        "t('closure', () => { function f() { return x }",
                        "  var r = []; try { f() } catch (e) { r.push('TDZ') } let x = 1; r.push(f()); return r });",
                        "t('closureConst', () => { var f = () => x; f(); const x = 1 });",
                        "t('prior', () => { x; let x });",
                        "t('self', () => { let x = x });",
                        "t('typeof', () => { typeof x; let x });",
                        "t('assign', () => { function f() { x = 1 } f(); let x });",
                        "t('inc', () => { x++; let x = 1 });",
                        "t('compound', () => { x += 1; let x = 1 });",
                        "t('destructure', () => { [x] = [1]; let x });",
                        "t('call', () => { f(); let f = () => 1 });",
                        "t('switch', () => { switch (1) { case 0: let x = 1; break; case 1: return x } });",
                        "t('forOfHead', () => { let x = [1]; for (let x of x) {} });",
                        "t('forInHead', () => { let x = {}; for (const x in x) {} });",
                        "t('extends', () => { class C extends C {} });",
                        "t('computedKey', () => { class C { [C]() {} } });",
                        "t('classBefore', () => { new C(); class C {} });",
                        "t('iife', () => { const x = (() => x)() });",
                        "t('eval', () => { eval('x'); let x = 1 });",
                        "t('defaultLater', () => ((a = b, b) => 0)());",
                        "t('defaultSelf', () => (function (a = a) {})());",
                        "t('loop', () => { var r = []; for (var i = 0; i < 2; i++) {",
                        "  if (i) r.push(g()); let y = i; var g = () => y; r.push(y) }",
                        "  return r });",
                        "out.join('|')"));
    }

    /** References that cannot run before initialization keep working. */
    @Test
    void referencesAfterInitialization() {
        Utils.assertWithAllModes_ES6(
                "a:1,undefined,function|b:0,1|c:3|d:120|e:C|f:TypeError|g:4|h:7,1",
                Utils.lines(
                        HARNESS,
                        "t('a', () => { let x = 1; let u; function f() { return x } return [f(), typeof u, typeof g];"
                                + " function g() {} });",
                        "t('b', () => { var r = []; for (let i = 0; i < 2; i++) { const k = i; r.push(() => k) }"
                                + " return r.map(f => f()) });",
                        "t('c', () => { const o = { m() { return o.v }, v: 3 }; return o.m() });",
                        "t('d', () => { const fact = n => n ? n * fact(n - 1) : 1; return fact(5) });",
                        "t('e', () => { class C { static m() { return C.name } } return C.m() });",
                        "t('f', () => { class C { static m() { C = 1 } } try { C.m() } catch (e) { return e.name } });",
                        "t('g', () => ((a, b = a) => b)(4));",
                        "t('h', () => { switch (0) { case 0: let x = 7; var r = [x];"
                                + " case 1: r.push(1); return r } });",
                        "out.join('|')"));
    }

    /**
     * Lexical bindings at the top level of separately evaluated scripts, as embedders run them: a
     * later script reads them, and one a script never got to initialize stays uninitialized.
     */
    @Test
    void topLevelBindingsAcrossScripts() {
        Utils.runWithAllModes(
                cx -> {
                    cx.setLanguageVersion(Context.VERSION_ES6);
                    TopLevel scope = cx.initStandardObjects();
                    cx.evaluateString(
                            scope,
                            "let name = 'base'; const log = s => name + ':' + s;"
                                    + " function get() { return name }",
                            "global.js",
                            1);
                    assertEquals(
                            "base:x,base,mod",
                            cx.evaluateString(
                                    scope,
                                    "(function() { 'use strict'; let r = [log('x'), get()];"
                                            + " name = 'mod'; r.push(get()); return r.join() })()",
                                    "mod.js",
                                    1));
                    try {
                        cx.evaluateString(
                                scope,
                                "function early() { return late } throw 1; let late = 1",
                                "failed.js",
                                1);
                    } catch (JavaScriptException e) {
                        // The script never initializes late
                    }
                    assertEquals(
                            "ReferenceError,ReferenceError",
                            cx.evaluateString(
                                    scope,
                                    "var r = []; try { late } catch (e) { r.push(e.name) }"
                                            + " try { typeof late } catch (e) { r.push(e.name) }"
                                            + " r.join()",
                                    "later.js",
                                    1));
                    return null;
                });
    }
}
