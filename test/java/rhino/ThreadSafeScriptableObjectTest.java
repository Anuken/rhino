package rhino;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import rhino.testutils.Utils;

public class ThreadSafeScriptableObjectTest {
    @Test
    public void canSealGlobalObjectWithoutDeadlock() {
        // a local factory, since replacing the global one would leak into other test classes
        ContextFactory factory = Utils.contextFactoryWithFeatures(Context.FEATURE_THREAD_SAFE_OBJECTS);

        try (Context cx = factory.enterContext()) {
            TopLevel global = cx.initStandardObjects();
            global.sealObject();

            // Registered by NativeJavaTopPackage
            assertNotNull(global.get("Packages", global));
        }
    }
}
