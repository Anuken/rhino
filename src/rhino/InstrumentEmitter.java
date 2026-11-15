package rhino;

public abstract class InstrumentEmitter {

    public enum Type {
        PARSE,
        COMPILE_INTERPRETER,
        COMPILE_CLASSFILE,
        SCRIPT_EXEC,
        SAFE_OBJECTS_INIT,
        OBJECTS_INIT,
        TEST_EVENT
    }

    /** Always a no-op: JFR (jdk.jfr) is not available on every supported platform. */
    public static final InstrumentEmitter emitter = new NullEmitter();

    public abstract Object startEvent(Type type);

    public abstract void endEvent(Object event, Object... data);

    private static class NullEmitter extends InstrumentEmitter {

        private NullEmitter() {}

        @Override
        public Object startEvent(Type type) {
            return null;
        }

        @Override
        public void endEvent(Object event, Object... data) {
            // Do nothing here.
        }
    }
}
