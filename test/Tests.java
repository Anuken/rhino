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

        cx.setOptimizationLevel(-1);

        //redeclaration of const throws a different error in the interpreter for some reason
        assertThrows(EcmaError.class, () -> eval("const a = 5125125; a = 0;"));

        eval("u => u;");
        eval("var func = new Packages.java.util.function.Predicate(){test: u => true}; func.test(321);");
        assertEquals(Boolean.FALSE, eval("Tests.testPredicate(i => i > 0, 0)"));
        assertEquals(Boolean.TRUE, eval("Tests.testPredicate(i => i > 0, 1)"));
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
}
