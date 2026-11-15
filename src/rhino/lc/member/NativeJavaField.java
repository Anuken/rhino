package rhino.lc.member;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import rhino.lc.ReflectUtils;
import rhino.lc.type.TypeInfo;
import rhino.lc.type.TypeInfoFactory;

/**
 * @author ZZZank
 */
public final class NativeJavaField {
    private final Field field;
    private final boolean isFinal;
    private final TypeInfo type;
    private final boolean generic;

    public NativeJavaField(Field field, TypeInfoFactory typeFactory) {
        this.field = field;
        this.isFinal = Modifier.isFinal(field.getModifiers());
        var genericType = field.getGenericType();
        this.type = typeFactory.create(genericType);
        this.generic = !(genericType instanceof Class);
        ReflectUtils.skipAccessCheck(field, field);
    }

    public Field raw() {
        return field;
    }

    public TypeInfo type() {
        return type;
    }

    /** Whether the declared type uses type parameters, so that it can depend on the owner type. */
    public boolean isGeneric() {
        return generic;
    }

    public Object get(Object javaObject) throws IllegalAccessException {
        return field.get(javaObject);
    }

    /** Note: will do nothing when called on a final field */
    public void set(Object javaObject, Object value) throws IllegalAccessException {
        if (isFinal) {
            // treat Java final the same as JavaScript [[READONLY]]
            return;
        }
        field.set(javaObject, value);
    }
}
