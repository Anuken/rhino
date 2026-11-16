/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package rhino.tests;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import rhino.ArrayLikeAbstractOperations;
import rhino.Context;
import rhino.ContextFactory;
import rhino.VarScope;

public class SerializationDisableTest {
    private static final class PolicyFactory extends ContextFactory {
        private final boolean enabled;

        PolicyFactory(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        protected boolean hasFeature(Context cx, int featureIndex) {
            if (featureIndex == Context.FEATURE_JAVA_SERIALIZATION) {
                return enabled;
            }
            return super.hasFeature(cx, featureIndex);
        }
    }

    private static final ContextFactory ENABLED = new PolicyFactory(true);
    private static final ContextFactory DISABLED = new PolicyFactory(false);

    private static Object makeObject(Context cx, VarScope s) {
        return cx.evaluateString(s, "var o = {s: \"Hello\", o: {who: \"World\"}}; o", "make.js", 1);
    }

    @Test
    public void testSerializationDisabled() throws Exception {
        try (Context cx = DISABLED.enterContext()) {
            var scope = cx.initSafeStandardObjects();
            Object o = makeObject(cx, scope);

            assertThrows(NotSerializableException.class, () -> serialize(o));
        }
    }

    @Test
    public void testSerializationDefault() throws Exception {
        try (Context cx = new ContextFactory().enterContext()) {
            var scope = cx.initSafeStandardObjects();
            Object o = makeObject(cx, scope);

            assertThrows(NotSerializableException.class, () -> serialize(o));
        }
    }

    @Test
    public void testDangerousComparatorDisabledByDefault() throws IOException {
        assertThrows(
                NotSerializableException.class,
                () -> serialize(new ArrayLikeAbstractOperations.StringLikeComparator()));
    }

    @Test
    public void testDangerousComparatorDeserializationDisabled() throws Exception {
        byte[] serialized;
        try (Context cx = ENABLED.enterContext()) {
            serialized = serialize(new ArrayLikeAbstractOperations.StringLikeComparator());
        }

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            assertThrows(InvalidObjectException.class, ois::readObject);
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(value);
        }
        return bos.toByteArray();
    }
}
