package rhino.lc.member;

/**
 * @author ZZZank
 */
public class ExecutableOverload {
    public final String name;
    public final ExecutableBox[] methods;

    /**
     * The function object that scripts see for these methods, created on first access by the member
     * table that owns this overload.
     */
    public volatile Object function;

    public ExecutableOverload(String name, ExecutableBox[] methods) {
        this.name = name;
        this.methods = methods;
    }

    public static final class WithField extends ExecutableOverload {
        public final NativeJavaField field;

        public WithField(ExecutableOverload old, NativeJavaField field) {
            super(old.name, old.methods);
            this.field = field;
        }
    }
}
