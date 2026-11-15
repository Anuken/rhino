/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino.typedarrays;

import static rhino.ClassDescriptor.Builder.value;
import static rhino.ClassDescriptor.Destination.CTOR;
import static rhino.ClassDescriptor.Destination.PROTO;

import java.io.Serial;
import rhino.ClassDescriptor;
import rhino.Context;
import rhino.JSFunction;
import rhino.ScriptRuntime;
import rhino.ScriptRuntimeES6;
import rhino.SymbolKey;
import rhino.TopLevel;
import rhino.Undefined;
import rhino.VarScope;

/**
 * An array view that stores 32-bit quantities and implements the JavaScript "Float32Array"
 * interface. It also implements List&lt;Float&gt; for direct manipulation in Java.
 */
public class NativeFloat32Array extends NativeTypedArrayView<Float> {
    @Serial private static final long serialVersionUID = -1986336631829489295L;

    private static final String CLASS_NAME = "Float32Array";
    private static final int BYTES_PER_ELEMENT = 4;

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                3,
                                NativeTypedArrayView::typeError,
                                NativeFloat32Array::js_constructor)
                        .withProp(CTOR, "BYTES_PER_ELEMENT", value(4))
                        .withProp(PROTO, "BYTES_PER_ELEMENT", value(4))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .build();
    }

    public NativeFloat32Array() {}

    public NativeFloat32Array(NativeArrayBuffer ab, int off, int len) {
        super(ab, off, len, len * BYTES_PER_ELEMENT);
    }

    public NativeFloat32Array(int len) {
        this(new NativeArrayBuffer(len * BYTES_PER_ELEMENT), 0, len);
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    public static JSFunction init(Context cx, VarScope scope, boolean sealed) {
        return NativeTypedArrayView.initSubClass(cx, scope, DESCRIPTOR, sealed);
    }

    @Override
    public int getBytesPerElement() {
        return BYTES_PER_ELEMENT;
    }

    private static Object js_constructor(
            Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
        return NativeTypedArrayView.js_constructor(
                cx,
                f,
                nt,
                s,
                thisObj,
                args,
                NativeFloat32Array::new,
                4,
                TopLevel.Builtins.Float32Array);
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        return arrayBuffer.buffer.getFloat((index * BYTES_PER_ELEMENT) + offset);
    }

    @Override
    protected Object js_set(int index, Object c) {
        float val = (float) ScriptRuntime.toNumber(c);
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        arrayBuffer.buffer.putFloat((index * BYTES_PER_ELEMENT) + offset, val);
        return null;
    }

    @Override
    public Float get(int i) {
        ensureIndex(i);
        return (Float) js_get(i);
    }

    @Override
    public Float set(int i, Float aByte) {
        ensureIndex(i);
        return (Float) js_set(i, aByte);
    }
}
