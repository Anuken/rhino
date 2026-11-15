/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

/** */
package rhino.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rhino.CompilerEnvirons;
import rhino.Context;
import rhino.ErrorReporter;
import rhino.Parser;
import rhino.ast.AstRoot;

/**
 * @author André Bargull
 */
public class Bug688018Test {

    private Context cx;

    @BeforeEach
    public void setUp() {
        cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_1_8);
    }

    @AfterEach
    public void tearDown() {
        Context.exit();
    }

    private AstRoot parse(CharSequence cs) {
        CompilerEnvirons compilerEnv = new CompilerEnvirons();
        compilerEnv.initFromContext(cx);
        ErrorReporter compilationErrorReporter = compilerEnv.getErrorReporter();
        Parser p = new Parser(compilerEnv, compilationErrorReporter);
        return p.parse(cs.toString(), "<eval>", 1);
    }

    private String toSource(CharSequence cs) {
        return parse(cs).toSource();
    }

    @Test
    public void toSource() {
        assertEquals("void 0;\n", toSource("void 0;"));
        assertEquals("void 1;\n", toSource("void 1;"));
        assertEquals("void 'hello';\n", toSource("void 'hello';"));
        assertEquals("void fn();\n", toSource("void fn();"));
    }
}
