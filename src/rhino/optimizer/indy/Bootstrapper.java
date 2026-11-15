package rhino.optimizer.indy;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import rhino.optimizer.DynamicOp;

/**
 * The bootstrap method of the invokedynamic instructions in compiled code, used only when {@code
 * Codegen.INVOKEDYNAMIC} is set. Each call site is an inline cache: values seen at the site are
 * linked to type-specialized fast paths from {@link Linker}, each behind a guard and chained in
 * front of a relink handle. The site permanently switches to the generic implementation of its
 * {@link DynamicOp} as soon as a value without a fast path shows up, or after {@link #MAX_RELINKS}
 * relinks.
 */
@SuppressWarnings("AndroidJdkLibsChecker")
public final class Bootstrapper {
    private static final int MAX_RELINKS = 8;

    private static final MethodHandle RELINK;

    static {
        try {
            RELINK =
                    MethodHandles.lookup()
                            .findVirtual(
                                    InlineCache.class,
                                    "relink",
                                    MethodType.methodType(Object.class, Object[].class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Bootstrapper() {}

    @SuppressWarnings("unused")
    public static CallSite bootstrap(MethodHandles.Lookup lookup, String name, MethodType type) {
        String operand = DynamicOp.operandOf(name);
        // Slot maps can shortcut on identical property name strings.
        return new InlineCache(
                DynamicOp.forName(name), operand == null ? null : operand.intern(), type);
    }

    static final class InlineCache extends MutableCallSite {
        private final DynamicOp op;
        private final String name;
        private int relinks;

        InlineCache(DynamicOp op, String name, MethodType type) {
            super(type);
            this.op = op;
            this.name = name;
            setTarget(
                    RELINK.bindTo(this)
                            .asCollector(Object[].class, type.parameterCount())
                            .asType(type));
        }

        Object relink(Object[] args) throws Throwable {
            MethodHandle invocation;
            synchronized (this) {
                Linker.Guarded fast =
                        relinks++ < MAX_RELINKS ? Linker.link(op, name, type(), args) : null;
                if (fast == null) {
                    invocation = Linker.generic(op, name, type());
                    setTarget(invocation);
                } else {
                    invocation = fast.target;
                    setTarget(MethodHandles.guardWithTest(fast.guard, fast.target, getTarget()));
                }
            }
            return invocation.invokeWithArguments(args);
        }
    }
}
