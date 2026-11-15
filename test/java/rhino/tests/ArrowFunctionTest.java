/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Objects;
import org.junit.jupiter.api.Test;
import rhino.Parser;
import rhino.ast.AstNode;
import rhino.ast.Block;
import rhino.ast.FunctionNode;
import rhino.ast.NodeVisitor;
import rhino.ast.ReturnStatement;
import rhino.testutils.Utils;

public class ArrowFunctionTest {
    /** Util class that sifts through nodes for first arrow function, then stores it and stops */
    private static class ArrowFnExtractor implements NodeVisitor {
        private FunctionNode functionNode;

        @Override
        public boolean visit(AstNode node) {
            if (functionNode != null) return false;
            if (node instanceof FunctionNode) {
                FunctionNode fn = (FunctionNode) node;
                if (fn.getFunctionType() == FunctionNode.ARROW_FUNCTION) {
                    functionNode = fn;
                    return false;
                }
            }
            return true;
        }
    }

    /** Parses given source line and extracts a first (top) arrow function node */
    private FunctionNode parseAndExtractArrowFn(String src) {
        ArrowFnExtractor arrowFnExtractor = new ArrowFnExtractor();
        Parser p = new Parser();
        p.parse(src, "eval", 1).visit(arrowFnExtractor);
        return Objects.requireNonNull(arrowFnExtractor.functionNode);
    }

    @Test
    public void arrowFnPositionInAssignment() {
        FunctionNode arrowFn = parseAndExtractArrowFn("var a = () => 1;");
        assertEquals(4, arrowFn.getPosition());
        assertEquals(8, arrowFn.getAbsolutePosition());
    }

    @Test
    public void arrowFnPositionInCall() {
        FunctionNode arrowFn = parseAndExtractArrowFn("test(() => { return 2; }, a);");
        assertEquals(5, arrowFn.getPosition());
        assertEquals(5, arrowFn.getAbsolutePosition());
    }

    @Test
    public void arrowFnWithArgsPosition() {
        FunctionNode arrowFn = parseAndExtractArrowFn("var a = (b, c) => b + c;");
        assertEquals(4, arrowFn.getPosition());
        assertEquals(8, arrowFn.getAbsolutePosition());
    }

    @Test
    public void arrowFnReturnPosition() {
        FunctionNode arrowFn = parseAndExtractArrowFn("test((cb) => cb() + 1);");
        ReturnStatement returnStatement = (ReturnStatement) arrowFn.getBody().getFirstChild();
        assertEquals(0, returnStatement.getPosition());
        assertEquals(13, returnStatement.getAbsolutePosition());
        assertEquals(8, returnStatement.getLength());
    }

    @Test
    public void arrowFnEvalToString() {
        String source = "eval(\"()=>this\").toString()";
        Utils.assertWithAllModes("()=>this", source);
    }

    @Test
    public void testArrowRhinoWithLineEndingOnlyThis() {
        FunctionNode arrowFn = parseAndExtractArrowFn("()=>this");

        assertEquals(0, arrowFn.getPosition());
        assertEquals(0, arrowFn.getAbsolutePosition());
        assertEquals(8, arrowFn.getLength());

        Block body = (Block) arrowFn.getBody();
        assertEquals(4, body.getPosition());
        assertEquals(4, body.getAbsolutePosition());
        assertEquals(4, body.getLength());
    }

    @Test
    public void testArrowRhinoWithLineEndingOnlyThisNl() {
        FunctionNode arrowFn = parseAndExtractArrowFn("()=>this\n");

        assertEquals(0, arrowFn.getPosition());
        assertEquals(0, arrowFn.getAbsolutePosition());
        assertEquals(8, arrowFn.getLength());

        Block body = (Block) arrowFn.getBody();
        assertEquals(4, body.getPosition());
        assertEquals(4, body.getAbsolutePosition());
        assertEquals(4, body.getLength());
    }

    @Test
    public void testArrowRhinoWithLineEndingThisWithProperty() {
        FunctionNode arrowFn = parseAndExtractArrowFn("()=>this.xs");

        assertEquals(0, arrowFn.getPosition());
        assertEquals(0, arrowFn.getAbsolutePosition());
        assertEquals(11, arrowFn.getLength());

        Block body = (Block) arrowFn.getBody();
        assertEquals(4, body.getPosition());
        assertEquals(4, body.getAbsolutePosition());
        assertEquals(7, body.getLength());
    }

    @Test
    public void testArrowRhinoWithLineEndingThisWithPropertyNl() {
        FunctionNode arrowFn = parseAndExtractArrowFn("()=>this.xs\n");

        assertEquals(0, arrowFn.getPosition());
        assertEquals(0, arrowFn.getAbsolutePosition());
        assertEquals(11, arrowFn.getLength());

        Block body = (Block) arrowFn.getBody();
        assertEquals(4, body.getPosition());
        assertEquals(4, body.getAbsolutePosition());
        assertEquals(7, body.getLength());
    }
}
