package rhino;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;

/**
 * Represents code that is executable, and potentially resumable. Those concepts are separated out
 * as {@link JSCodeExec} and {@link JSCodeResume} to allow for the use of functional interfaces.
 */
public abstract class JSCode<T extends ScriptOrFn<T>> implements JSCodeExec<T>, JSCodeResume<T> {

    protected JSCode() {
        if (!(this instanceof ACompilerData)
                && !RhinoException.compiledCodeCreated
                && getClass().getName().startsWith("rhino.optimizer.")) {
            RhinoException.compiledCodeCreated = true;
        }
    }

    /**
     * Represents a mutable {@link JSCode} object in the process of being created, usually as a
     * child of a {@link JSDescriptor.Builder}. This is necessary because during compilationthere
     * may not be a concrete, class, method, or interpreter data structure yet,
     */
    public abstract static class Builder<U extends ScriptOrFn<U>> {

        /** Builds and returns the {@link JSCode} object. */
        public abstract JSCode<U> build();
    }

    /**
     * A builder for a null code entry. This may be used when either the constructor or call action
     * of a function is not supported and so will be null.
     */
    public static class NullBuilder<V extends ScriptOrFn<V>> extends Builder<V> {

        @Override
        public JSCode<V> build() {
            return null;
        }
    }

    public static final JSCode<JSFunction> NOT_CALLABLE = new NotCallable();

    /** The call behavior of a class constructor, which may only be constructed. */
    public static final JSCode<JSFunction> CLASS_CONSTRUCTOR_CALL = new ClassConstructorCall();

    private static class ClassConstructorCall extends JSCode<JSFunction> implements Serializable {

        private static final long serialVersionUID = 1L;

        @Override
        public Object execute(
                Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
            throw ScriptRuntime.typeErrorById("msg.class.not.new", f.getFunctionName());
        }

        @Override
        public Object resume(Context cx, JSFunction f, Object state, VarScope s, int op, Object v) {
            return null;
        }

        private Object readResolve() {
            return CLASS_CONSTRUCTOR_CALL;
        }
    }

    private static class NotCallable extends JSCode<JSFunction> implements Serializable {

        private static final long serialVersionUID = -31340315773728063L;

        @Override
        public Object execute(
                Context cx, JSFunction f, Object nt, VarScope s, Object thisObj, Object[] args) {
            throw ScriptRuntime.typeError("Not callable as function");
        }

        @Override
        public Object resume(Context cx, JSFunction f, Object state, VarScope s, int op, Object v) {
            return null;
        }

        @Serial
        private void readObject(ObjectInputStream stream)
                throws IOException, ClassNotFoundException {
            Context.checkDeserializationEnabled();
            stream.defaultReadObject();
        }

        @Serial
        private void writeObject(ObjectOutputStream stream) throws IOException {
            Context.checkSerializationEnabled();
            stream.defaultWriteObject();
        }

        @Serial
        private void readObjectNoData() throws InvalidObjectException {
            throw new InvalidObjectException("Missing serialized Rhino object data");
        }
    }
}
