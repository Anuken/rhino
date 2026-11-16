package rhino.tests.type_info;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import rhino.*;
import rhino.lc.type.TypeInfoFactory;
import rhino.lc.type.impl.factory.LegacyCacheFactory;

/**
 * @author ZZZank
 */
public class CustomTypeInfoFactoryTest {

    @Test
    public void associateAfterInit() throws Exception {
        var contextFactory = new ContextFactory();

        try (var cx = contextFactory.enterContext()) {
            var scope = cx.initStandardObjects();

            var toAssociate = AlwaysFailFactory.INSTANCE;
            var associated = toAssociate.associate(scope);

            Assertions.assertNotSame(toAssociate, associated);
        }
    }

    @Test
    public void associate() throws Exception {
        var contextFactory = new ContextFactory();

        try (var cx = contextFactory.enterContext()) {
            var scope = new TopLevel();
            new NoGenericNoCacheFactory().associate(scope);
            cx.initStandardObjects(scope);

            var typeFactory = TypeInfoFactory.get(scope);

            Assertions.assertInstanceOf(NoGenericNoCacheFactory.class, typeFactory);
        }
    }

    @Test
    public void defaultFactory() throws Exception {
        var contextFactory = new ContextFactory();

        try (var cx = contextFactory.enterContext()) {
            var scope = cx.initStandardObjects();

            var typeFactory = TypeInfoFactory.get(scope);

            Assertions.assertInstanceOf(LegacyCacheFactory.Concurrent.class, typeFactory);
        }
    }
}
