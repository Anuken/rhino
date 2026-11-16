package rhino.lc.type.impl.factory;

import java.util.Map;
import rhino.lc.type.TypeInfo;
import rhino.lc.type.TypeInfoFactory;
import rhino.lc.type.impl.BasicClassTypeInfo;
import rhino.lc.type.impl.EnumTypeInfo;
import rhino.lc.type.impl.InterfaceTypeInfo;

/**
 * {@link TypeInfoFactory} caching class types in maps. {@code ClassValue} is not used because it is
 * missing on older Android versions and iOS.
 *
 * @author ZZZank
 */
public abstract class LegacyCacheFactory extends AbstractCacheFactory {
    private Map<Class<?>, TypeInfo> classCache;
    private volatile TypeInfo lastCreated;

    @Override
    protected void initCache() {
        super.initCache();
        classCache = createCache();
    }

    @Override
    public TypeInfo create(Class<?> clazz) {
        if (clazz == null || clazz == Object.class || clazz.isPrimitive()) {
            return TypeInfoFactory.matchPredefined(clazz);
        }
        // this runs whenever a Java object is wrapped, often for objects of one class in a row
        var cached = lastCreated;
        if (cached != null && cached.asClass() == clazz) {
            return cached;
        }
        cached = classCache.get(clazz);
        if (cached == null) {
            cached = getCached(classCache, clazz, this::createUncached);
        }
        lastCreated = cached;
        return cached;
    }

    private TypeInfo createUncached(Class<?> clazz) {
        final var predefined = TypeInfoFactory.matchPredefined(clazz);
        if (predefined != null) {
            return predefined;
        } else if (clazz.isArray()) {
            return toArray(create(clazz.getComponentType()));
        } else if (clazz.isEnum()) {
            return new EnumTypeInfo(clazz);
        } else if (clazz.isInterface()) {
            return new InterfaceTypeInfo(clazz);
        }
        return new BasicClassTypeInfo(clazz);
    }

    public static class Concurrent extends LegacyCacheFactory implements CachedFactory.Concurrent {}

    public static class WeakReference extends LegacyCacheFactory
            implements CachedFactory.WeakReference {}
}
