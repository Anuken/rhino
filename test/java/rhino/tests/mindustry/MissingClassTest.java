package rhino.tests.mindustry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import rhino.Context;
import rhino.ImporterTopLevel;
import rhino.NativeJavaClass;
import rhino.testutils.Utils;

/**
 * Classes whose signatures use types that are missing at runtime (stripped by RoboVM, absent on
 * Android, or optional mod dependencies) are still usable: only the affected members are lost.
 */
class MissingClassTest {
    /** Defines {@link MissingFixtures} itself, so it links against it, and hides the missing types. */
    static class HidingLoader extends ClassLoader {
        HidingLoader() {
            super(MissingClassTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals(MissingFixtures.Missing.class.getName())
                    || name.equals(MissingFixtures.GenericMissing.class.getName())) {
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith(MissingFixtures.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (this) {
                Class<?> c = findLoadedClass(name);
                if (c != null) return c;
                try (InputStream in =
                        getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    in.transferTo(out);
                    byte[] b = out.toByteArray();
                    return defineClass(name, b, 0, b.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        }
    }

    @Test
    void membersWithMissingTypesAreSkipped() throws Exception {
        HidingLoader loader = new HidingLoader();
        Class<?> usesMissing = loader.loadClass(MissingFixtures.UsesMissing.class.getName());
        Class<?> usesGeneric = loader.loadClass(MissingFixtures.UsesMissingGeneric.class.getName());
        Object a = usesMissing.getConstructor().newInstance();
        Object b = usesGeneric.getConstructor().newInstance();

        Utils.runWithAllModes(
                cx -> {
                    ImporterTopLevel scope = new ImporterTopLevel(cx);
                    scope.put("UsesMissing", scope, new NativeJavaClass(scope, usesMissing));
                    scope.put("a", scope, Context.javaToJS(a, scope));
                    scope.put("b", scope, Context.javaToJS(b, scope));
                    // HotSpot can't list the methods of this class at all, so only the object's
                    // Object methods remain; a script using it must not fail
                    assertEquals(
                            "true",
                            Context.toString(
                                    cx.evaluateString(
                                            scope,
                                            "typeof a.hashCode == 'function' && String(a) != ''",
                                            "test.js",
                                            1,
                                            null)));
                    assertEquals(
                            "42,undefined,undefined",
                            Context.toString(
                                    cx.evaluateString(
                                            scope,
                                            "b.ok() + ',' + typeof b.generic + ',' + typeof b.list",
                                            "test.js",
                                            1,
                                            null)));
                    return null;
                });
    }
}
