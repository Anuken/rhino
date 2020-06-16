package rhino.typedarrays;

import rhino.*;

/**
 * An array view that stores 8-bit quantities and implements the JavaScript "Uint8Array" interface.
 * It also implements List&lt;Integer&gt; for direct manipulation in Java.
 */

public class NativeUint8Array
extends NativeTypedArrayView<Integer>{
    private static final String CLASS_NAME = "Uint8Array";

    public NativeUint8Array(){
    }

    public NativeUint8Array(NativeArrayBuffer ab, int off, int len){
        super(ab, off, len, len);
    }

    public NativeUint8Array(int len){
        this(new NativeArrayBuffer(len), 0, len);
    }

    @Override
    public String getClassName(){
        return CLASS_NAME;
    }

    public static void init(Context cx, Scriptable scope, boolean sealed){
        NativeUint8Array a = new NativeUint8Array();
        a.exportAsJSClass(MAX_PROTOTYPE_ID, scope, sealed);
    }

    @Override
    protected NativeUint8Array construct(NativeArrayBuffer ab, int off, int len){
        return new NativeUint8Array(ab, off, len);
    }

    @Override
    public int getBytesPerElement(){
        return 1;
    }

    @Override
    protected NativeUint8Array realThis(Scriptable thisObj, IdFunctionObject f){
        if(!(thisObj instanceof NativeUint8Array)){
            throw incompatibleCallError(f);
        }
        return (NativeUint8Array)thisObj;
    }

    @Override
    protected Object js_get(int index){
        if(checkIndex(index)){
            return Undefined.instance;
        }
        return ByteIo.readUint8(arrayBuffer.buffer, index + offset);
    }

    @Override
    protected Object js_set(int index, Object c){
        if(checkIndex(index)){
            return Undefined.instance;
        }
        int val = Conversions.toUint8(c);
        ByteIo.writeUint8(arrayBuffer.buffer, index + offset, val);
        return null;
    }

    @Override
    public Integer get(int i){
        if(checkIndex(i)){
            throw new IndexOutOfBoundsException();
        }
        return (Integer)js_get(i);
    }

    @Override
    public Integer set(int i, Integer aByte){
        if(checkIndex(i)){
            throw new IndexOutOfBoundsException();
        }
        return (Integer)js_set(i, aByte);
    }
}
