package rhino.tests.mindustry;

import static rhino.tests.mindustry.MindustryScripts.assertBoth;
import static rhino.tests.mindustry.MindustryScripts.assertThrowsBoth;

import org.junit.jupiter.api.Test;
import rhino.RhinoException;

/**
 * ES6 const as Mindustry mods use it: block scoped, a fresh binding per loop iteration, and not
 * assignable. Mod scripts run wrapped in a strict function, as Mindustry's Scripts.run does.
 */
class ConstScopingTest {
    private static String mod(String body) {
        return "(function(){'use strict';\n" + body + "\n})();";
    }

    @Test
    void blockScoping() {
        assertBoth(
                "0,2,4|1,2|0,1,2",
                mod(
                        "var o = []; for(let i = 0; i < 3; i++){ const y = i * 2; o.push(y); }"
                                + "var r = []; { const x = 1; r.push(x); } { const x = 2; r.push(x); }"
                                + "var fs = []; for(var i = 0; i < 3; i++){ const x = i; fs.push(() => x); }"
                                + "return o.join() + '|' + r.join() + '|' + fs.map(f => f()).join();"));
        assertBoth(
                "0,2,4",
                "var o = []; for(var i = 0; i < 3; i++){ const y = i * 2; o.push(y); } o.join()");
        assertBoth(
                "1,2|oneother|0,11",
                mod(
                        "function f(b){ if(b){ const a = 1; return a; } else { const a = 2; return a; } }"
                                + "function g(v){ switch(v){ case 1: { const r = 'one'; return r; } default: { const r = 'other'; return r; } } }"
                                + "var d = []; for(var i = 0; i < 2; i++){ const {a, b} = {a: i, b: i * 10}; d.push(a + b); }"
                                + "return f(true) + ',' + f(false) + '|' + g(1) + g(2) + '|' + d.join();"));
        assertBoth("undefined", mod("{ const hidden = 1; } return typeof hidden;"));
        assertBoth("1", mod("const a = 1; { const a = 2; } return a;"));
        assertBoth("6,7,8", mod("var o = []; [5, 6, 7].forEach(v => { const w = v + 1; o.push(w); }); return o.join();"));
    }

    @Test
    void topLevelAcrossScripts() {
        // Mindustry evaluates global.js and then each script in the same scope
        assertBoth(
                "99,444,99",
                "const someValue = 99; var r = [someValue];",
                "r.push((function(){ const someValue = 444; return someValue; })()); r.push(someValue); r.join()");
        assertBoth(
                "p:s:m",
                "'use strict';\nlet scriptName = 'base.js';\nlet modName = 'none';\nconst print = text => 'p:' + text + ':' + modName;",
                "modName = 'm'\nscriptName = 's'",
                mod("return print(scriptName);"));
    }

    @Test
    void assignmentThrows() {
        assertThrowsBoth(RhinoException.class, "{ const b = 0; } b");
        assertThrowsBoth(RhinoException.class, "const a = 5125125; a = 0;");
        assertThrowsBoth(RhinoException.class, "{ const a = 5125125; a = 0; }");
        assertThrowsBoth(RhinoException.class, mod("const a = 1; a++;"));
        assertThrowsBoth(RhinoException.class, mod("const a = 1; a = 2;"));
    }
}
