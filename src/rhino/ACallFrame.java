/* -*- Mode: java; tab-width: 8; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package rhino;

import rhino.debug.DebugFrame;
import rhino.debug.DebuggableScript;

import java.util.Arrays;

/**
 * Interface representing a call frame in the interpreter. This abstraction allows both the original
 * interpreter's CallFrame and InterpreterV2's CallFrameV2 to be used interchangeably for stack
 * traces and debugging purposes.
 */
public abstract class ACallFrame<T extends ACallFrame<T, U>, U extends ACompilerData<?, U>> {
    public final T parentFrame;
    public final short frameIndex;
    public final ACallFrame<?, ?> previousInterpreterFrame;
    public final int parentPC;
    public boolean frozen;

    public ScriptOrFn<?> fnOrScript;
    // descriptor of fnOrScript
    public final JSDescriptor<?> descriptor;
    public U compilerData;

    public final Object[] stack;
    public final byte[] stackAttributes;
    public final double[] doubleStack;

    public Object result;
    public double resultDbl;
    public int pc;
    public int stackTop = -1;
    public VarScope scope;

    // `this' might seem like it should be final, but is actually set
    // by a call to a super constructor and so must be mutable.
    public Object thisObj;
    public final Object newTarget;

    public final DebugFrame debuggerFrame;
    public final boolean useActivation;

    public ACallFrame<T, U> varSource;
    public final int localShift;
    public final short emptyStackTop;
    public Object throwable;
    final boolean parentStrictness;
    // whether the stack arrays were made for this frame rather than shared with or copied from
    // another, so they can be recycled once it returns
    final boolean ownsStack;

    ACallFrame(
            Context cx,
            Object thisObj,
            Object newTarget,
            ScriptOrFn<?> fnOrScript,
            U code,
            T parentFrame,
            ACallFrame<?, ?> previousInterpreterFrame) {
        compilerData = code;
        descriptor = fnOrScript.getDescriptor();
        debuggerFrame = cx.debugger != null ? cx.debugger.getFrame(cx, descriptor) : null;
        useActivation = descriptor.requiresActivationFrame();

        emptyStackTop = (short) (compilerData.maxVars + compilerData.maxLocals - 1);
        int maxFrameArray = compilerData.maxFrameSize;
        if (maxFrameArray != emptyStackTop + compilerData.maxStack + 1) Kit.codeBug();

        StackPool pool = cx.frameStackPool;
        int pooled = pool != null ? pool.take(maxFrameArray) : -1;
        if (pooled >= 0) {
            stack = pool.stacks[pooled];
            doubleStack = pool.doubleStacks[pooled];
            pool.stacks[pooled] = null;
            pool.doubleStacks[pooled] = null;
        } else {
            stack = new Object[maxFrameArray];
            doubleStack = new double[maxFrameArray];
        }
        ownsStack = true;
        // only const variables have attributes
        stackAttributes =
                descriptor.hasConstParamOrVar()
                        ? new byte[compilerData.maxVars]
                        : compilerData.noVarAttributes;

        this.fnOrScript = fnOrScript;
        localShift = compilerData.maxVars;
        varSource = this;
        this.thisObj = thisObj;
        this.newTarget = newTarget;

        this.parentFrame = parentFrame;
        if (parentFrame == null) {
            this.parentPC =
                    previousInterpreterFrame == null
                            ? -1
                            : previousInterpreterFrame.getPcSourceLineStart();
        } else {
            this.parentPC = parentFrame.getPcSourceLineStart();
        }
        this.previousInterpreterFrame = previousInterpreterFrame;
        frameIndex = (short) ((parentFrame == null) ? 0 : parentFrame.frameIndex + 1);
        if (frameIndex > cx.getMaximumInterpreterStackDepth()) {
            throw Context.reportRuntimeError("Exceeded maximum stack depth");
        }

        if (descriptor.getFunctionType() != 0) {
            this.parentStrictness =
                    ScriptRuntime.enterFunctionStrictness(cx, descriptor.isStrict());
        } else {
            this.parentStrictness = false;
        }
        // Initialize initial values of variables that change during
        // interpretation.
        result = Undefined.instance;
    }

    protected ACallFrame(T original, T parentFrame, ACallFrame<?, ?> previousInterpreterFrame) {
        this(original, parentFrame, previousInterpreterFrame, true, false);
    }

    protected ACallFrame(
            T original,
            T parentFrame,
            ACallFrame<?, ?> previousInterpreterFrame,
            boolean copyStack,
            boolean keepFrozen) {

        if (!original.frozen) Kit.codeBug();

        if (copyStack) {
            stack = Arrays.copyOf(original.stack, original.stack.length);
            stackAttributes =
                    Arrays.copyOf(original.stackAttributes, original.stackAttributes.length);
            doubleStack = Arrays.copyOf(original.doubleStack, original.doubleStack.length);
        } else {
            stack = original.stack;
            stackAttributes = original.stackAttributes;
            doubleStack = original.doubleStack;
        }

        localShift = original.localShift;
        ownsStack = false;

        frozen = keepFrozen;
        this.parentFrame = parentFrame;
        this.previousInterpreterFrame = previousInterpreterFrame;
        if (parentFrame == null) {
            frameIndex = 0;
            parentPC =
                    previousInterpreterFrame == null
                            ? -1
                            : previousInterpreterFrame.getPcSourceLineStart();
        } else {
            frameIndex = original.frameIndex;
            parentPC = parentFrame.parentPC;
        }

        fnOrScript = original.fnOrScript;
        descriptor = original.descriptor;
        compilerData = original.compilerData;

        if (original.varSource == original) {
            varSource = this;
        } else {
            varSource = original.varSource;
        }
        emptyStackTop = original.emptyStackTop;

        debuggerFrame = original.debuggerFrame;
        useActivation = original.useActivation;

        thisObj = original.thisObj;
        newTarget = original.newTarget;

        result = original.result;
        resultDbl = original.resultDbl;
        pc = original.pc;
        scope = original.scope;
        parentStrictness = original.parentStrictness;
        stackTop = original.stackTop;
    }

    /** Returns the index of this frame in the call stack (0 for the top-level frame). */
    public abstract int getFrameIndex();

    /** Returns the parent frame, or null if this is the top-level frame. */
    public abstract ACallFrame<T, U> getParentFrame();

    /**
     * Returns the program counter position for source line information. For the original
     * interpreter, this is the PC at the start of the current source line. For InterpreterV2, this
     * may be the current PC.
     */
    public abstract int getPcSourceLineStart();

    /** Returns the debuggable script data associated with this frame. */
    public abstract DebuggableScript getData();

    public int getParentPC() {
        return -1;
    }

    public ACallFrame<?, ?> getPreviousInterpreterFrame() {
        return null;
    }

    public ScriptOrFn<?> getFnOrScript() {
        return null;
    }

    Object getFromVars(int offset) {
        Object value = stack[offset];
        if (value == UniqueTag.DOUBLE_MARK) {
            return doubleStack[offset];
        } else {
            return value;
        }
    }

    void setInVars(int offset, Object value) {
        if (value instanceof Double
                && !((Double) value).isInfinite()
                && !((Double) value).isNaN()) {
            stack[offset] = UniqueTag.DOUBLE_MARK;
            doubleStack[offset] = ((Double) value);
        } else {
            stack[offset] = value;
        }
    }

    abstract T cloneFrozen();

    /**
     * Recycles the stack arrays of frames that have returned, as allocating them is a large part of
     * the cost of a call. Each context has its own pool.
     */
    static final class StackPool {
        private static final int MAX_SIZE = 64;
        private static final int PER_SIZE = 4;

        final Object[][] stacks = new Object[MAX_SIZE * PER_SIZE][];
        final double[][] doubleStacks = new double[MAX_SIZE * PER_SIZE][];
        private final byte[] counts = new byte[MAX_SIZE];

        /** Returns the index of pooled arrays of the given size, or -1. */
        int take(int size) {
            if (size >= MAX_SIZE || counts[size] == 0) {
                return -1;
            }
            return size * PER_SIZE + --counts[size];
        }

        /**
         * Pools the arrays of a frame that has returned, if they are not used by anything else.
         * Frozen frames belong to continuations or generators, and debugged ones may be inspected.
         */
        static void release(Context cx, ACallFrame<?, ?> frame) {
            if (!frame.ownsStack || frame.frozen || frame.debuggerFrame != null) {
                return;
            }
            int size = frame.stack.length;
            if (size >= MAX_SIZE) {
                return;
            }
            StackPool pool = cx.frameStackPool;
            if (pool == null) {
                pool = cx.frameStackPool = new StackPool();
            }
            int count = pool.counts[size];
            if (count < PER_SIZE) {
                Arrays.fill(frame.stack, null);
                pool.stacks[size * PER_SIZE + count] = frame.stack;
                pool.doubleStacks[size * PER_SIZE + count] = frame.doubleStack;
                pool.counts[size] = (byte) (count + 1);
            }
        }
    }
}
