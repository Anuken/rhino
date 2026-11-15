/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import java.lang.reflect.Method;
import java.util.Map;
import rhino.lc.ReflectUtils;

/** Version of {@link JavaMembers} for modular JDKs. */
class JavaMembers_jdk11 extends JavaMembers {

    JavaMembers_jdk11(VarScope scope, Class<?> cl, boolean includeProtected) {
        super(scope, cl, includeProtected);
    }

    @Override
    void discoverPublicMethods(Class<?> clazz, Map<MethodSignature, Method> map) {
        if (isExportedClass(clazz)) {
            super.discoverPublicMethods(clazz, map);
        } else {
            Method[] methods = clazz.getMethods();
            for (Method method : methods) {
                method = findAccessibleMethod(method);
                registerMethod(map, method);
            }
        }
    }

    private static boolean isExportedClass(Class<?> clazz) {
        if (!ReflectUtils.IS_MODULAR_JAVA) {
            return true;
        }

        // clazz.getModule().isExported(clazz.getPackageName()), via reflection because Module
        // does not exist on Android
        try {
            Object module = ModuleAccess.GET_MODULE.invoke(clazz);
            Object packageName = ModuleAccess.GET_PACKAGE_NAME.invoke(clazz);
            return (Boolean) ModuleAccess.IS_EXPORTED.invoke(module, packageName);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** Only initialized on modular Java. */
    private static final class ModuleAccess {
        static final Method GET_MODULE;
        static final Method GET_PACKAGE_NAME;
        static final Method IS_EXPORTED;

        static {
            try {
                GET_MODULE = Class.class.getMethod("getModule");
                GET_PACKAGE_NAME = Class.class.getMethod("getPackageName");
                IS_EXPORTED = GET_MODULE.getReturnType().getMethod("isExported", String.class);
            } catch (NoSuchMethodException e) {
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private static Method findAccessibleMethod(Method method) {
        Class<?> cl = method.getDeclaringClass();
        final String methodName = method.getName();
        final Class<?>[] methodTypes = method.getParameterTypes();
        topLoop:
        do {
            for (Class<?> intface : cl.getInterfaces()) {
                try {
                    method = intface.getMethod(methodName, methodTypes);
                    break topLoop;
                } catch (NoSuchMethodException e) {
                }
            }
            cl = cl.getSuperclass();
            if (cl == null) {
                break;
            }
            if (isExportedClass(cl)) {
                try {
                    method = cl.getMethod(methodName, methodTypes);
                    break;
                } catch (NoSuchMethodException e) {
                }
            }
        } while (true);
        return method;
    }
}
