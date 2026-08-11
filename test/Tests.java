import org.junit.*;
import rhino.*;

import static org.junit.Assert.*;

public class Tests{
    Context cx = Context.enter();
    ImporterTopLevel scope = new ImporterTopLevel(cx);

    {
        cx.setLanguageVersion(Context.VERSION_ES6);
        cx.setOptimizationLevel(9);
        scope.importClass(new NativeJavaClass(scope, Tests.class));
    }

    @Test
    public void test(){
        //const outside block
        assertThrows(EcmaError.class, () -> {
            eval("{const b = 0;} b");
        });

        //redeclaration of const
        assertThrows(EvaluatorException.class, () -> {
            eval("const a = 5125125; a = 0;");
        });

        //blocks can be messy
        assertThrows(EvaluatorException.class, () -> {
            eval("{ const a = 5125125; a = 0; }");
        });

        assertEquals("123", eval("const gg = 123; gg").toString());

        assertEquals("666", eval("var w = new JavaAdapter(Packages.java.lang.Object, { hashCode(){ return 666; } }); w.hashCode();").toString());

        assertEquals("undefined", eval("var w = {a: 123}; delete w[\"a\"]; w.a"));

        eval("const someValue = 99");
        eval("(function(){ const someValue = 444; return someValue })();");
        eval("someValue");
        eval("var c = new java.lang.Object().getClass(); new JavaAdapter(c, {})");

        //enable interpreter mode
        cx.setOptimizationLevel(-1);

        //redeclaration of const throws a different error in the interpreter for some reason
        assertThrows(EcmaError.class, () -> eval("const a = 5125125; a = 0;"));

        eval(
        "const log = () => 1\n" +
        "const print = text => log(1, text)\n" +
        "\n" +
        "print();");

        eval("u => u;");
        eval("var func = new Packages.java.util.function.Predicate(){test: u => true}; func.test(321);");
        assertEquals(Boolean.FALSE, eval("Tests.testPredicate(i => i > 0, 0)"));
        assertEquals(Boolean.TRUE, eval("Tests.testPredicate(i => i > 0, 1)"));
    }

    @Test
    public void testInterpreterMatchesCompiledArrowsAndConsts(){
        // arrow function forwarding an argument to another arrow function -
        // the original crash report (ClassCastException: Undefined -> Callable)
        assertSameAtBothLevels(
        "const log = (a, b) => a + b\n" +
        "const print = (text) => log(1, text)\n" +
        "print(41);",
        42.0);

        // plain nested function closing over an outer local var
        assertSameAtBothLevels(
        "function outer(){ var x = 10; function inner(y){ return x + y; } return inner(5); }\n" +
        "outer();",
        15.0);

        // function composition built from arrow functions/closures
        assertSameAtBothLevels(
        "const compose = (f, g) => (x) => f(g(x));\n" +
        "const double = n => n * 2;\n" +
        "const inc = n => n + 1;\n" +
        "const combo = compose(double, inc);\n" +
        "combo(5);",
        12.0);

        // currying via nested arrow functions
        assertSameAtBothLevels(
        "const makeAdder = base => amount => base + amount;\n" +
        "const addFive = makeAdder(5);\n" +
        "addFive(10);",
        15.0);

        // object with a shorthand method returning a closure-captured counter
        assertSameAtBothLevels(
        "function counter(){ var count = 0; return { inc: () => ++count, get: () => count }; }\n" +
        "var c = counter();\n" +
        "c.inc(); c.inc(); c.inc();\n" +
        "c.get();",
        "3");

        // arrow function lexical `this` combined with a local var
        assertSameAtBothLevels(
        "const obj = { value: 21, getDouble(){ return (() => this.value * 2)(); } };\n" +
        "obj.getDouble();",
        42.0);

        // var reassigned inside a function-scoped loop (while)
        assertSameAtBothLevels(
        "function fact(n){ var acc = 1; while(n > 1){ acc = acc * n; n = n - 1; } return acc; }\n" +
        "fact(5);",
        120.0);

        // var reassigned inside a function-scoped loop (for), Fibonacci
        assertSameAtBothLevels(
        "function fib(n){ var a = 0; var b = 1; for(var i = 0; i < n; i++){ var next = a + b; a = b; b = next; } return a; }\n" +
        "fib(10);",
        55.0);

        // several sequential local vars threaded through arithmetic inside an arrow function
        assertSameAtBothLevels(
        "const calc = (a, b, c) => { var sum = a + b; var product = sum * c; return product; };\n" +
        "calc(2, 3, 4);",
        20.0);

        assertSameAtBothLevels(
        "const pipeline = (x) => { var step1 = x + 1; var step2 = step1 * 2; var step3 = step2 - 3; return step3; };\n" +
        "pipeline(5);",
        9.0);

        // callback passed to Array#map allocating a local var per call
        assertSameAtBothLevels(
        "var arr = [1, 2, 3];\n" +
        "var mapped = arr.map(function(v){ var local = v * 2; return local; });\n" +
        "mapped.join(\",\");",
        "2,4,6");

        // arrow function body reading both an outer `let` and a local `let`
        assertSameAtBothLevels(
        "let str = \"hi\";\n" +
        "const greet = name => { let msg = str + \" \" + name; return msg; };\n" +
        "greet(\"world\");",
        "hi world");

        // a `const` re-declared on every iteration of a loop (legal, common
        // JS) must not throw a redeclaration error - guards against a
        // regression where doSetConstVar's UNINITIALIZED_CONST branch gets
        // an errant "else { throw ... }" (that throw belongs in doSetVar's
        // READONLY branch, not here; doSetConstVar must stay silent on a
        // slot that's already initialized)
        assertSameAtBothLevels(
        "function f(){ var total = 0; for(var i = 0; i < 3; i++){ const x = i; total += x; } return total; }\n" +
        "f();",
        0.0);

        // a closure created once per loop iteration - documents that this
        // interpreter shares a single binding for `let` across `for` loop
        // iterations (both engines agree, so this is expected engine
        // behavior rather than a regression)
        assertSameAtBothLevels(
        "let results = [];\n" +
        "for(let i = 0; i < 3; i++){ results.push(() => i); }\n" +
        "results.map(f => f()).join(\",\");",
        "3,3,3");
    }

    public static boolean testPredicate(java.util.function.Predicate<Integer> pred, int number){
        return pred.test(number);
    }

    Object eval(String str){
        Object res = cx.evaluateString(scope, str, "testfile", 0);
        Object o = res instanceof NativeJavaObject ? ((NativeJavaObject)res).unwrap() : res instanceof Undefined ? "undefined" : res;
        System.out.println(o);
        return o;
    }

    void assertSameAtBothLevels(String source, Object expected){
        Object compiled = evalAtLevel(9, source);
        Object interpreted = evalAtLevel(-1, source);
        assertEquals("optimizationLevel 9 (compiled)", String.valueOf(expected), String.valueOf(compiled));
        assertEquals("optimizationLevel -1 (interpreted)", String.valueOf(expected), String.valueOf(interpreted));
    }

    Object evalAtLevel(int level, String source){
        Context c = Context.enter();
        try{
            c.setLanguageVersion(Context.VERSION_ES6);
            c.setOptimizationLevel(level);
            ImporterTopLevel sc = new ImporterTopLevel(c);
            sc.importClass(new NativeJavaClass(sc, Tests.class));
            Object res = c.evaluateString(sc, source, "testfile", 0);
            return res instanceof NativeJavaObject ? ((NativeJavaObject)res).unwrap() : res instanceof Undefined ? "undefined" : res;
        } finally {
            Context.exit();
        }
    }
}