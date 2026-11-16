package rhino.lc.type.impl.factory;

import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.Map;
import rhino.lc.type.TypeInfo;
import rhino.lc.type.TypeInfoFactory;
import rhino.lc.type.VariableTypeInfo;
import rhino.lc.type.impl.VariableTypeInfoImpl;

/**
 * {@link TypeInfoFactory} implementation with cache. The exact characteristic if the factory
 * depends on the characteristic of map backend created via {@link #initCache()}
 *
 * @author ZZZank
 */
public abstract class AbstractCacheFactory implements FactoryBase, CachedFactory {
    private Map<TypeVariable<?>, VariableTypeInfoImpl> variableCache;
    private Map<Class<?>, Map<VariableTypeInfo, TypeInfo>> consolidationMappingCache;

    public AbstractCacheFactory() {
        initCache();
    }

    protected void initCache() {
        variableCache = createCache();
        consolidationMappingCache = createCache();
    }

    @Override
    public TypeInfo create(TypeVariable<?> typeVariable) {
        return getCached(variableCache, typeVariable, raw -> new VariableTypeInfoImpl(raw, this));
    }

    protected interface Creator<K, V> {
        V create(K key);
    }

    /** Atomic get-or-create, like {@code computeIfAbsent}. */
    protected static <K, V> V getCached(Map<K, V> cache, K key, Creator<K, V> creator) {
        V value = cache.get(key);
        if (value == null) {
            synchronized (cache) {
                value = cache.get(key);
                if (value == null) {
                    value = creator.create(key);
                    cache.put(key, value);
                }
            }
        }
        return value;
    }

    @Override
    public Map<VariableTypeInfo, TypeInfo> getConsolidationMapping(Class<?> from) {
        if (from == null || from == Object.class || from.isPrimitive()) {
            return Collections.emptyMap();
        }
        // no computeIfAbsent because `computeTypeReplacement(...)` will recursively call this
        // method again
        var got = consolidationMappingCache.get(from);
        if (got == null) {
            got = computeConsolidationMapping(from);
            consolidationMappingCache.put(from, got);
        }
        return got;
    }
}
