package rhino.lc.type.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EnumTypeInfo extends ClassTypeInfo {

    public static String getName(Object e) {
        return ((Enum<?>) e).name();
    }

    private List<Object> constants;

    public EnumTypeInfo(Class<?> type) {
        super(type);
    }

    @Override
    public boolean isEnum() {
        return true;
    }

    @Override
    public List<Object> enumConstants() {
        if (constants == null) {
            constants =
                    Collections.unmodifiableList(
                            Arrays.<Object>asList(asClass().getEnumConstants()));
        }

        return constants;
    }
}
