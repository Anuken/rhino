package rhino.tests.es5;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rhino.Context;
import rhino.VarScope;

/**
 * @see <a
 *     href="https://github.com/mozilla/rhino/issues/651">https://github.com/mozilla/rhino/issues/651</a>
 */
public class StringSearchTest {
    private Context cx;
    private VarScope scope;

    @BeforeEach
    public void setUp() {
        cx = Context.enter();
        cx.setLanguageVersion(Context.VERSION_1_8);
        scope = cx.initStandardObjects();
    }

    @AfterEach
    public void tearDown() {
        Context.exit();
    }

    @Test
    public void search() {
        Object result = cx.evaluateString(scope, "String.prototype.search(1, 1)", "test.js", 1);
        assertEquals(-1, result);
    }
}
