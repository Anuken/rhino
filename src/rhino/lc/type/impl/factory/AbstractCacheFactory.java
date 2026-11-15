package rhino.lc.type.impl.factory;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.lang.reflect.TypeVariable;
import java.util.Collections;
import java.util.Map;
import rhino.Context;
import rhino.lc.type.TypeInfo;
import rhino.lc.type.TypeInfoFactory;
import rhino.lc.type.VariableTypeInfo;
import rhino.lc.type.impl.VariableTypeInfoImpl;

/**
 * {@link TypeInfoFactory} implementation with cache. The exact characteristic if the factory
 * depends on the characteristic of map backend created via {@link #initCache()}
 *
 * <p>This factory is serializable, but none of its cached objects will be serialized.
 *
 * @author ZZZank
 */
public abstract class AbstractCacheFactory implements FactoryBase, CachedFactory {
    @Serial private static final long serialVersionUID = 4533445095188189419L;

    private transient Map<TypeVariable<?>, VariableTypeInfoImpl> variableCache;
    private transient Map<Class<?>, Map<VariableTypeInfo, TypeInfo>> consolidationMappingCache;

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

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        Context.checkSerializationEnabled();
        out.defaultWriteObject();
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        Context.checkDeserializationEnabled();
        in.defaultReadObject();
        initCache();
    }

    @Serial
    private void readObjectNoData() throws InvalidObjectException {
        throw new InvalidObjectException("Missing serialized Rhino object data");
    }
}
