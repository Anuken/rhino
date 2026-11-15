package rhino.lc;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Member;
import java.lang.reflect.Modifier;
import java.util.List;
import rhino.lc.type.TypeInfo;

/**
 * @author ZZZank
 */
public abstract class ReflectUtils {

    /**
     * {@code true} if we are on a "modular" version of Java (Java 11 or up, excluding Android). It
     * does not use the SourceVersion class because this is not present on Android.
     */
    public static final boolean IS_MODULAR_JAVA;

    static {
        boolean isModularJava;
        try {
            Class.class.getMethod("getModule");
            isModularJava = true;
        } catch (NoSuchMethodException e) {
            isModularJava = false;
        }
        IS_MODULAR_JAVA = isModularJava;
    }

    /**
     * Turns off the access check that reflection otherwise does on every use of a member which is
     * accessible anyway.
     */
    public static void skipAccessCheck(AccessibleObject object, Member member) {
        if (Modifier.isPublic(member.getModifiers())
                && Modifier.isPublic(member.getDeclaringClass().getModifiers())) {
            try {
                object.setAccessible(true);
            } catch (RuntimeException e) {
                // e.g. a package that its module doesn't export, which reflection also refuses
            }
        }
    }

    public static String javaSignature(Class<?> type) {
        int arrayDimension = 0;
        while (type.isArray()) {
            arrayDimension++;
            type = type.getComponentType();
        }

        if (arrayDimension == 0) {
            return type.getName();
        }

        var builder = new StringBuilder(type.getName());
        for (int i = 0; i < arrayDimension; i++) {
            builder.append("[]");
        }
        return builder.toString();
    }

    public static String liveConnectSignature(List<TypeInfo> argTypes) {
        if (argTypes.isEmpty()) {
            return "()";
        }

        var builder = new StringBuilder();

        builder.append('(');
        var iter = argTypes.iterator();
        if (iter.hasNext()) {
            builder.append(javaSignature(iter.next().asClass()));
            while (iter.hasNext()) {
                builder.append(',').append(javaSignature(iter.next().asClass()));
            }
        }
        builder.append(')');

        return builder.toString();
    }
}
