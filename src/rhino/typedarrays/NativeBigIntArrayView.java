package rhino.typedarrays;

import java.math.BigInteger;
import rhino.ScriptRuntime;

public abstract class NativeBigIntArrayView extends NativeTypedArrayView<BigInteger> {
    protected NativeBigIntArrayView() {}

    protected NativeBigIntArrayView(NativeArrayBuffer ab, int off, int len, int byteLen) {
        super(ab, off, len, byteLen);
    }

    @Override
    protected Object toNumeric(Object num) {
        return ScriptRuntime.toBigInt(num);
    }
}
