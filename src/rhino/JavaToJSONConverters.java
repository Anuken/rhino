/* -*- Mode: java; tab-width: 4; indent-tabs-mode: 1; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class provides implementations of converters for Java objects to be used by the
 * JSON.stringify method.
 *
 * <p>JSON.stringify will automatically convert instances of java.util.Map to javascript objects.
 * Instances of java.util.Collection and java Arrays will be converted to javascript arrays.
 *
 * <p>This is a final effort at conversion for other java objects that appear as values, and may be
 * preempted by objects which define a toJSON() method or by a replacer function passed to
 * JSON.stringify. The return value will, in turn, be converted according to {@link
 * Context#javaToJS} and stringified.
 *
 * @author Tony Germano
 */
public class JavaToJSONConverters {

    /** Converts a Java object to a value that JSON.stringify can handle. */
    public interface Converter {
        Object apply(Object value);
    }

    private JavaToJSONConverters() {}

    /** Convert Object to its toString() value. */
    public static final Converter STRING = o -> o.toString();

    /** Always return undefined */
    public static final Converter UNDEFINED = o -> Undefined.instance;

    /** Always return an empty object */
    public static final Converter EMPTY_OBJECT = o -> Collections.emptyMap();

    /** Throw a TypeError naming the class that could not be converted */
    public static final Converter THROW_TYPE_ERROR =
            o -> {
                throw ScriptRuntime.typeErrorById(
                        "msg.json.cant.serialize", o.getClass().getName());
            };

    /**
     * Convert JavaBean to an object as long as it has at least one readable property
     *
     * <p>If unable to determine properties or if none exist, null is returned. This method can be
     * called from other converters to provide an alternate value on a returned null.
     */
    public static final Converter BEAN =
            value -> {
                LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
                TreeMap<String, Method> getters = findBeanGetters(value.getClass());
                for (Map.Entry<String, Method> entry : getters.entrySet()) {
                    Object propValue;
                    try {
                        propValue = entry.getValue().invoke(value);
                    } catch (Exception e) {
                        continue;
                    }
                    properties.put(entry.getKey(), propValue);
                }

                if (properties.size() == 0) return null;

                LinkedHashMap<String, Object> obj = new LinkedHashMap<>();
                obj.put("beanClass", value.getClass().getName());
                obj.put("properties", properties);
                return obj;
            };

    /** Finds bean property read methods, sorted by name, like java.beans.Introspector does. */
    private static TreeMap<String, Method> findBeanGetters(Class<?> cls) {
        HashMap<String, Method> isGetters = new HashMap<>();
        HashMap<String, Method> getGetters = new HashMap<>();
        for (Method method : cls.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    || method.getDeclaringClass() == Object.class
                    || method.getParameterTypes().length != 0) {
                continue;
            }
            String name = method.getName();
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class && name.length() > 2 && name.startsWith("is")) {
                isGetters.put(name.substring(2), method);
            } else if (returnType != void.class && name.length() > 3 && name.startsWith("get")) {
                String key = name.substring(3);
                Method existing = getGetters.get(key);
                // prefer the most specific return type, which skips covariant bridge methods
                if (existing == null || existing.getReturnType().isAssignableFrom(returnType)) {
                    getGetters.put(key, method);
                }
            }
        }

        // isX() takes precedence over getX()
        getGetters.putAll(isGetters);
        TreeMap<String, Method> result = new TreeMap<>();
        for (Map.Entry<String, Method> entry : getGetters.entrySet()) {
            result.put(decapitalize(entry.getKey()), entry.getValue());
        }
        return result;
    }

    /** Same as {@code java.beans.Introspector.decapitalize}. */
    private static String decapitalize(String name) {
        if (name.length() > 1
                && Character.isUpperCase(name.charAt(1))
                && Character.isUpperCase(name.charAt(0))) {
            return name;
        }
        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }
}
