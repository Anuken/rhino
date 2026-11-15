package rhino.lc.type.impl.factory;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import rhino.lc.type.TypeInfoFactory;

/**
 * {@link Concurrent}: provides strong reference, thread-safe, performant cache
 *
 * <p>{@link WeakReference}: provides weak reference, thread-safe, less performant cache
 *
 * @author ZZZank
 */
interface CachedFactory extends TypeInfoFactory {
    <K, V> Map<K, V> createCache();

    interface Concurrent extends CachedFactory {

        @Override
        default <K, V> Map<K, V> createCache() {
            return new ConcurrentHashMap<>();
        }
    }

    interface WeakReference extends CachedFactory {

        @Override
        default <K, V> Map<K, V> createCache() {
            return Collections.synchronizedMap(new WeakHashMap<>());
        }
    }
}
