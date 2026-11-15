package rhino.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import org.junit.jupiter.api.Test;
import rhino.config.RhinoProperties;
import rhino.testutils.TestSource;

/**
 * Testcase for various property loading mechanism.
 *
 * @author Roland Praml, Foconis Analytics GmbH
 */
public class RhinoPropertiesTest {

    /** Tests explicit loading a file from classpath. */
    @Test
    void testClasspathLoad() {
        RhinoProperties properties = new RhinoProperties();
        properties.loadFromClasspath(getClass().getClassLoader(), "rhino-explicit.config");
        assertEquals("true", properties.get("test.rhino-explicit.loaded"));
        assertEquals("value1", properties.get("test.config.foo"));
        assertEquals("value2", properties.get("test.config.bar"));
    }

    /** Tests explicit loading a file. */
    @Test
    void testFileLoad() {
        RhinoProperties properties = new RhinoProperties();
        properties.loadFromFile(new File(TestSource.resolve("testsrc/rhino-explicit.config")));
        assertEquals("value1", properties.get("test.config.foo"));
        assertEquals("value2", properties.get("test.config.bar"));
    }

    @Test
    void testDefaultLoad() {
        System.setProperty("some.system.value", "value6");
        try {
            RhinoProperties properties = new RhinoProperties();
            properties.loadDefaults();
            assertEquals("value3", properties.get("test.config.foo"));
            assertEquals("value4-mod", properties.get("test.config.bar"));
            assertEquals("value5", properties.get("test.config.baz"));
            assertEquals("value6", properties.get("some.system.value"));
        } finally {
            System.clearProperty("some.system.value");
        }
    }

    /** System properties */
    @Test
    void testSystemOverride() {
        System.setProperty("testconfig.foo", "system-wins");
        try {
            RhinoProperties properties = new RhinoProperties();
            properties.loadDefaults();
            assertEquals("system-wins", properties.get("testconfig.foo"));
        } finally {
            System.clearProperty("testconfig.foo");
        }
    }

    @Test
    void testEnv() {
        RhinoProperties properties = new RhinoProperties();
        properties.loadDefaults();
        // TODO: can/shoud we set an environment value. so check, if we can read PATH
        // for now, we check for Path (and PATH)
        assertNotNull(properties.get("Path"));
    }
}
