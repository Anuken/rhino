package rhino.tests;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rhino.CompilerEnvirons;
import rhino.IRFactory;
import rhino.Parser;
import rhino.ast.AstRoot;
import rhino.ast.ScriptNode;
import rhino.testutils.TestSource;

public class ReadCommentsTest {

    @Test
    public void readComments() throws IOException {
        CompilerEnvirons compilerEnv = new CompilerEnvirons();
        compilerEnv.setInterpretedMode(false);
        compilerEnv.setRecordingComments(true);
        compilerEnv.setRecordingLocalJsDocComments(true);
        compilerEnv.setStrictMode(true);

        Parser p = new Parser(compilerEnv);
        String testJs;
        try (BufferedReader scriptIn =
                new BufferedReader(
                        new FileReader(TestSource.resolve("testsrc/jstests/withcomments.js")))) {
            testJs = scriptIn.lines().collect(Collectors.joining(System.lineSeparator()));
        }
        AstRoot ast = p.parse(testJs, "test", 1);
        IRFactory irf = new IRFactory(compilerEnv, testJs);
        ScriptNode tree = irf.transformTree(ast);

        Assertions.assertEquals(1, tree.getFunctions().size());
        Assertions.assertEquals(
                "/** @responseClass HttpAdapter */", tree.getFunctionNode(0).getJsDoc());
    }
}
