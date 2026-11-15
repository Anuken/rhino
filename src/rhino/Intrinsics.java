/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

/**
 * Runtime helpers that IRFactory can call from the trees it builds, through {@link
 * Token#INTRINSIC} nodes. Both the interpreter and compiled code call {@link #invoke}, so a
 * construct lowered onto intrinsics needs no support from either back end.
 *
 * <p>An intrinsic takes at most {@link #MAX_ARGS} arguments, which are the children of its node.
 */
public final class Intrinsics {

    public static final int MAX_ARGS = 4;

    // Class definition: see ClassRuntime
    public static final int CREATE_CLASS = 0, // (heritage, constructor, flags, name) -> class
            DEFINE_METHOD = 1, // (class, flags, key, function)
            DEFINE_PRIVATE_METHOD = 2, // (class, flags, private name, function)
            DEFINE_FIELD = 3, // (class, flags, key or private name, initializer or undefined)
            DEFINE_STATIC_BLOCK = 4, // (class, function)
            FINISH_CLASS = 5, // (class) -> class
            NEW_PRIVATE_NAME = 6, // (description) -> private name

            // Derived constructors
            SUPER_CALL = 7, // (class, new.target, argument array, this) -> new this
            CHECK_THIS = 8, // (this) -> this
            DERIVED_RETURN = 9, // (value, this) -> value checked once the body completes
            SUPER_GET = 10, // (this, super, key) -> value
            SUPER_SET = 11, // (this, super, key, value) -> value
            SUPER_CALL_METHOD = 12, // (this, super, key, argument array) -> result
            SUPER_REF = 13, // (this, super, key) -> Ref

            // Private names
            PRIVATE_GET = 14, // (object, private name) -> value
            PRIVATE_SET = 15, // (object, private name, value) -> value
            PRIVATE_CALL = 16, // (object, private name, argument array) -> result
            PRIVATE_IN = 17, // (private name, object) -> boolean
            PRIVATE_REF = 18, // (object, private name) -> Ref
            INIT_INSTANCE = 19, // (class, this) -> this, adding the class's private methods and fields
            PRIVATE_GET_OPTIONAL = 20, // (object, private name) -> value, undefined if no object

            // Calls with spread arguments
            CALL_SPREAD = 21, // (function, this, argument array, flags) -> result
            CALL_METHOD_SPREAD = 22, // (object, key, argument array, flags) -> result
            NEW_SPREAD = 23, // (constructor, argument array) -> object

            // Destructuring: see DestructuringIterator
            DSTR_ITER = 24, // (value) -> iterator of an array pattern
            DSTR_NEXT = 25, // (iterator) -> next value, or undefined once done
            DSTR_REST = 26, // (iterator) -> array of the remaining values
            DSTR_CLOSE = 27, // (iterator) -> undefined, closing it unless done
            REQUIRE_OBJECT = 28, // (value) -> value, throwing if null or undefined
            AWAIT_REQUEST = 29, // (value) -> what an async generator yields to await the value

            // for await: see AsyncIteration
            ASYNC_ITERATOR = 30, // (value) -> iteration
            ASYNC_NEXT = 31, // (iteration) -> what to await for the next result
            ASYNC_STEP = 32, // (iteration, awaited result) -> whether there is a value
            ASYNC_VALUE = 33, // (iteration) -> value
            ASYNC_OPEN = 34, // (iteration) -> whether the loop must close the iterator
            ASYNC_RETURN = 35, // (iteration) -> what to await for return()
            ASYNC_RETURN_CHECK = 36, // (awaited return result) -> undefined
            ASYNC_RETURN_QUIETLY = 37, // (iteration) -> what to await, closing on an exception
            ASYNC_RETURN_NO_AWAIT = 38, // (iteration) -> undefined, closing on return
            ASYNC_CAUGHT = 39, // (iteration, exception) -> undefined
            ASYNC_THREW = 40, // (iteration) -> whether the loop threw
            ASYNC_EXCEPTION = 41, // (iteration) -> what the loop threw
            COUNT = 42;

    /** Flags of the spread calls: whether the call or the object access is optional chained. */
    public static final int OPTIONAL_CALLEE = 1, OPTIONAL_OBJECT = 2;

    private static final byte[] ARG_COUNTS = {
        4, 4, 4, 4, 2, 1, 1, 4, 1, 2, 3, 4, 4, 3, 2, 3, 3, 2, 2, 2, 2, 4, 4, 2, 1, 1, 1, 1, 1, 1, 1, 1, 2, 1, 1, 1, 1, 1, 1, 2, 1, 1
    };

    private Intrinsics() {}

    /** Returns the number of arguments of an intrinsic. */
    public static int argCount(int id) {
        return ARG_COUNTS[id];
    }

    /** Returns whether an intrinsic returns a {@link Ref}. */
    public static boolean returnsRef(int id) {
        return id == SUPER_REF || id == PRIVATE_REF;
    }

    /** Returns the intrinsic that writes the reference an intrinsic reads, or -1. */
    static int setterOf(int id) {
        switch (id) {
            case SUPER_GET:
                return SUPER_SET;
            case PRIVATE_GET:
                return PRIVATE_SET;
            default:
                return -1;
        }
    }

    /** Returns the intrinsic that makes a {@link Ref} to what an intrinsic reads, or -1. */
    static int refOf(int id) {
        switch (id) {
            case SUPER_GET:
                return SUPER_REF;
            case PRIVATE_GET:
                return PRIVATE_REF;
            default:
                return -1;
        }
    }

    /** Returns the intrinsic that calls the method an intrinsic reads, or -1. */
    static int callOf(int id) {
        switch (id) {
            case SUPER_GET:
                return SUPER_CALL_METHOD;
            case PRIVATE_GET:
                return PRIVATE_CALL;
            default:
                return -1;
        }
    }

    /** Calls an intrinsic. Arguments past its argument count are ignored. */
    public static Object invoke(
            Object a0,
            Object a1,
            Object a2,
            Object a3,
            int id,
            Context cx,
            VarScope scope) {
        switch (id) {
            case CREATE_CLASS:
                return ClassRuntime.createClass(
                        cx, scope, a0, a1, ScriptRuntime.toInt32(a2), (String) a3);
            case DEFINE_METHOD:
                ClassRuntime.defineMethod(cx, a0, ScriptRuntime.toInt32(a1), a2, a3);
                return Undefined.instance;
            case DEFINE_PRIVATE_METHOD:
                ClassRuntime.definePrivateMethod(a0, ScriptRuntime.toInt32(a1), a2, a3);
                return Undefined.instance;
            case DEFINE_FIELD:
                ClassRuntime.defineField(a0, ScriptRuntime.toInt32(a1), a2, a3);
                return Undefined.instance;
            case DEFINE_STATIC_BLOCK:
                ClassRuntime.defineStaticBlock(a0, a1);
                return Undefined.instance;
            case FINISH_CLASS:
                return ClassRuntime.finishClass(cx, scope, a0);
            case NEW_PRIVATE_NAME:
                return new ClassRuntime.PrivateName((String) a0);
            case SUPER_CALL:
                return ClassRuntime.superCall(cx, scope, a0, a1, a2, a3);
            case CHECK_THIS:
                return ClassRuntime.checkThis(a0);
            case DERIVED_RETURN:
                return ClassRuntime.derivedReturn(a0, a1);
            case SUPER_GET:
                return ClassRuntime.superGet(cx, scope, a0, a1, a2);
            case SUPER_SET:
                return ClassRuntime.superSet(cx, scope, a0, a1, a2, a3);
            case SUPER_CALL_METHOD:
                return ClassRuntime.superCallMethod(cx, scope, a0, a1, a2, a3);
            case SUPER_REF:
                return ClassRuntime.superRef(a0, a1, a2);
            case PRIVATE_GET:
                return ClassRuntime.privateGet(cx, scope, a0, a1);
            case PRIVATE_SET:
                return ClassRuntime.privateSet(cx, scope, a0, a1, a2);
            case PRIVATE_CALL:
                return ClassRuntime.privateCall(cx, scope, a0, a1, a2);
            case PRIVATE_IN:
                return ClassRuntime.privateIn(a0, a1);
            case PRIVATE_REF:
                return ClassRuntime.privateRef(a0, a1);
            case PRIVATE_GET_OPTIONAL:
                return a0 == null || Undefined.isUndefined(a0)
                        ? Undefined.instance
                        : ClassRuntime.privateGet(cx, scope, a0, a1);
            case CALL_SPREAD:
                return callSpread(cx, scope, a0, a1, a2, ScriptRuntime.toInt32(a3));
            case CALL_METHOD_SPREAD:
                return callMethodSpread(cx, scope, a0, a1, a2, ScriptRuntime.toInt32(a3));
            case NEW_SPREAD:
                return newSpread(cx, scope, a0, a1);
            case DSTR_ITER:
                return DestructuringIterator.create(cx, scope, a0);
            case DSTR_NEXT:
                return ((DestructuringIterator) a0).next();
            case DSTR_REST:
                return ((DestructuringIterator) a0).rest();
            case DSTR_CLOSE:
                ((DestructuringIterator) a0).close();
                return Undefined.instance;
            case REQUIRE_OBJECT:
                if (isNullish(a0)) {
                    throw ScriptRuntime.typeErrorById(
                            "msg.dstr.null.undef", ScriptRuntime.toString(a0));
                }
                return a0;
            case ASYNC_ITERATOR:
                return AsyncIteration.create(cx, scope, a0);
            case ASYNC_NEXT:
                return ((AsyncIteration) a0).next(cx, scope);
            case ASYNC_STEP:
                return ((AsyncIteration) a0).step(cx, scope, a1);
            case ASYNC_VALUE:
                return ((AsyncIteration) a0).value();
            case ASYNC_OPEN:
                return ((AsyncIteration) a0).isOpen();
            case ASYNC_RETURN:
                return ((AsyncIteration) a0).callReturn(cx, scope);
            case ASYNC_RETURN_CHECK:
                AsyncIteration.checkReturn(a0);
                return Undefined.instance;
            case ASYNC_RETURN_QUIETLY:
                return ((AsyncIteration) a0).callReturnQuietly(cx, scope);
            case ASYNC_RETURN_NO_AWAIT:
                ((AsyncIteration) a0).callReturnWithoutAwaiting(cx, scope);
                return Undefined.instance;
            case ASYNC_CAUGHT:
                ((AsyncIteration) a0).caught(a1);
                return Undefined.instance;
            case ASYNC_THREW:
                return ((AsyncIteration) a0).threw();
            case ASYNC_EXCEPTION:
                return ((AsyncIteration) a0).exception();
            case AWAIT_REQUEST:
                return new NativeAsyncGenerator.AwaitRequest(a0);
            case INIT_INSTANCE:
                ClassRuntime.initializeInstance(cx, scope, (Scriptable) a1, (JSFunction) a0);
                return a1;
            default:
                throw Kit.codeBug();
        }
    }

    private static boolean isNullish(Object value) {
        return value == null || Undefined.isUndefined(value);
    }

    private static Object[] arguments(Context cx, Object array) {
        return ScriptRuntime.getArrayElements(cx, (Scriptable) array);
    }

    private static Object callSpread(
            Context cx, VarScope scope, Object fn, Object thisObj, Object args, int flags) {
        if ((flags & OPTIONAL_CALLEE) != 0 && isNullish(fn)) {
            return Undefined.instance;
        }
        if (!(fn instanceof Callable)) {
            throw ScriptRuntime.notFunctionError(fn);
        }
        return ((Callable) fn).call(cx, scope, thisObj, arguments(cx, args));
    }

    private static Object callMethodSpread(
            Context cx, VarScope scope, Object obj, Object key, Object args, int flags) {
        if ((flags & OPTIONAL_OBJECT) != 0 && isNullish(obj)) {
            return Undefined.instance;
        }
        ScriptRuntime.LookupResult method =
                (flags & OPTIONAL_CALLEE) != 0
                        ? ScriptRuntime.getElemAndThisOptional(obj, key, cx, scope)
                        : ScriptRuntime.getElemAndThis(obj, key, cx, scope);
        if (method == null || ((flags & OPTIONAL_CALLEE) != 0 && isNullish(method.getResult()))) {
            return Undefined.instance;
        }
        return method.call(cx, scope, arguments(cx, args));
    }

    private static Object newSpread(Context cx, VarScope scope, Object ctor, Object args) {
        if (!(ctor instanceof Constructable)) {
            throw ScriptRuntime.notFunctionError(ctor);
        }
        return ((Constructable) ctor).construct(cx, scope, arguments(cx, args));
    }
}
