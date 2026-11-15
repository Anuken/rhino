/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import java.io.Serial;
import rhino.typedarrays.NativeTypedArrayView;

public final class NativeArrayIterator extends ES6Iterator {
    public enum ARRAY_ITERATOR_TYPE {
        ENTRIES,
        KEYS,
        VALUES
    }

    @Serial private static final long serialVersionUID = 3370645355934941865L;
    private static final String ITERATOR_TAG = "ArrayIterator";

    private static final ClassDescriptor DESCRIPTOR =
            ES6Iterator.makeDescriptor(ITERATOR_TAG, "Array Iterator");

    /** The descriptor of %ArrayIteratorPrototype%.next. */
    static final JSDescriptor<JSFunction> NEXT_DESCRIPTOR =
            DESCRIPTOR.findCtorDesc(ES6Iterator.NEXT_METHOD);

    private ARRAY_ITERATOR_TYPE type;

    /** Returns %ArrayIteratorPrototype% of the realm of a scope. */
    static Object getPrototype(VarScope scope) {
        return ScriptableObject.getTopScopeValue(scope, ITERATOR_TAG);
    }

    static void init(Context cx, VarScope scope, boolean sealed) {
        ES6Iterator.initialize(
                DESCRIPTOR, cx, (TopLevel) scope, new NativeArrayIterator(), sealed, ITERATOR_TAG);
    }

    /** Only for constructing the prototype object. */
    private NativeArrayIterator() {
        super();
    }

    public NativeArrayIterator(VarScope scope, Object arrayLike, ARRAY_ITERATOR_TYPE type) {
        super(scope, ITERATOR_TAG);
        this.index = 0;
        this.arrayLike = (Scriptable) arrayLike;
        this.type = type;
    }

    @Override
    public String getClassName() {
        return "Array Iterator";
    }

    @Override
    protected boolean isDone(Context cx, VarScope scope) {
        if (arrayLike instanceof NativeTypedArrayView) {
            NativeTypedArrayView<?> typedArray = (NativeTypedArrayView<?>) arrayLike;
            if (typedArray.isTypedArrayOutOfBounds()) {
                throw ScriptRuntime.typeErrorById("msg.typed.array.out.of.bounds");
            }
        }
        return index >= NativeArray.getLengthProperty(cx, arrayLike);
    }

    @Override
    protected Object nextValue(Context cx, VarScope scope) {
        if (type == ARRAY_ITERATOR_TYPE.KEYS) {
            return Integer.valueOf(index++);
        }

        // A hole reads through to the prototype chain
        Object value = ScriptableObject.getProperty(arrayLike, index);
        if (value == Scriptable.NOT_FOUND) {
            value = Undefined.instance;
        }

        if (type == ARRAY_ITERATOR_TYPE.ENTRIES) {
            value = cx.newArray(scope, new Object[] {Integer.valueOf(index), value});
        }

        index++;
        return value;
    }

    @Override
    protected String getTag() {
        return ITERATOR_TAG;
    }

    private Scriptable arrayLike;
    private int index;
}
