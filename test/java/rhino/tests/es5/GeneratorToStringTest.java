package rhino.tests.es5;

import org.junit.jupiter.api.Test;
import rhino.testutils.Utils;

public class GeneratorToStringTest {
    @Test
    public void generatorsTest() {
        Utils.assertWithAllModesTopLevelScope_ES6(
                "function* f() {\n" + "    yield 1;\n" + "  }",
                "function* f() {\n" + "    yield 1;\n" + "  }; f.toString();");
    }
}
