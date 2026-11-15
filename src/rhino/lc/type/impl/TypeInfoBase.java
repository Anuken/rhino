package rhino.lc.type.impl;

import java.lang.reflect.Array;
import rhino.lc.type.TypeFormatContext;
import rhino.lc.type.TypeInfo;

public abstract class TypeInfoBase implements TypeInfo {
    private volatile Object emptyArray;
    private byte wrapperKind;

    @Override
    public int wrapperKind() {
        // checking for List and Map is slow, and done for every wrapped object
        int kind = wrapperKind;
        if (kind == 0) {
            kind = TypeInfo.super.wrapperKind();
            wrapperKind = (byte) kind;
        }
        return kind;
    }

    @Override
    public Object newArray(int length) {
        if (length == 0) {
            if (emptyArray == null) {
                synchronized (this) {
                    if (emptyArray == null) {
                        emptyArray = Array.newInstance(asClass(), 0);
                    }
                }
            }

            return emptyArray;
        }

        return Array.newInstance(asClass(), length);
    }

    @Override
    public final String toString() {
        return toString(TypeFormatContext.DEFAULT);
    }

    @Override
    public String toString(TypeFormatContext ctx) {
        var builder = new StringBuilder();
        ctx.append(builder, this);
        return builder.toString();
    }
}
