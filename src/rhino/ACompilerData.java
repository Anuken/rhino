package rhino;

public abstract class ACompilerData<T extends ScriptOrFn<T>, U extends ACompilerData<?, U>>
        extends JSCode<T> {
    public final int[] exceptionTable;

    public final int maxVars;
    public final int maxLocals;
    public final int maxStack;
    public final int maxFrameSize;

    /** Variable attributes shared by the frames of functions without const variables. */
    final byte[] noVarAttributes;

    protected ACompilerData(
            int maxVars, int maxLocals, int maxStack, int maxFrameSize, int[] exceptionTable) {
        this.maxVars = maxVars;
        this.maxLocals = maxLocals;
        this.maxStack = maxStack;
        this.maxFrameSize = maxFrameSize;
        this.exceptionTable = exceptionTable;
        this.noVarAttributes = new byte[maxVars];
    }

    public abstract int getLineNumberFromPc(int pc, int pcLineStart);
}
