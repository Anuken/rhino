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
 * An array view that stores 64-bit quantities and implements the JavaScript "Float64Array"
 * interface. It also implements List&lt;Double&gt; for direct manipulation in Java.
 */
public class NativeFloat64Array extends NativeTypedArrayView<Double> {
    @Serial private static final long serialVersionUID = -2289070055226752021L;

    private static final String CLASS_NAME = "Float64Array";
    private static final int BYTES_PER_ELEMENT = 8;

    private static final ClassDescriptor DESCRIPTOR;

    static {
        DESCRIPTOR =
                new ClassDescriptor.Builder(
                                CLASS_NAME,
                                3,
                                NativeTypedArrayView::typeError,
                                NativeFloat64Array::js_constructor)
                        .withProp(CTOR, "BYTES_PER_ELEMENT", value(8))
                        .withProp(PROTO, "BYTES_PER_ELEMENT", value(8))
                        .withProp(CTOR, SymbolKey.SPECIES, ScriptRuntimeES6::symbolSpecies)
                        .build();
    }

    public NativeFloat64Array() {}

    public NativeFloat64Array(NativeArrayBuffer ab, int off, int len) {
        super(ab, off, len, len * BYTES_PER_ELEMENT);
    }

    public NativeFloat64Array(int len) {
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
                NativeFloat64Array::new,
                8,
                TopLevel.Builtins.Float64Array);
    }

    @Override
    protected Object js_get(int index) {
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        return arrayBuffer.buffer.getDouble((index * BYTES_PER_ELEMENT) + offset);
    }

    @Override
    protected Object js_set(int index, Object c) {
        double val = ScriptRuntime.toNumber(c);
        if (checkIndex(index)) {
            return Undefined.instance;
        }
        arrayBuffer.buffer.putDouble((index * BYTES_PER_ELEMENT) + offset, val);
        return null;
    }

    @Override
    public Double get(int i) {
        ensureIndex(i);
        return (Double) js_get(i);
    }

    @Override
    public Double set(int i, Double aByte) {
        ensureIndex(i);
        return (Double) js_set(i, aByte);
    }
}
