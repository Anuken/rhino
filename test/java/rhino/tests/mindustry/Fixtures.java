package rhino.tests.mindustry;

/** Java types the Mindustry regression tests extend and call. */
public class Fixtures {
    /** Fields named like methods, as in Mindustry's generated entity classes. */
    public static class Masked {
        public static int count = 2;
        public int value = 5;
        public boolean flag = false;
        public float health = 0f;

        public static int count() {
            return count + 1;
        }

        public int value(int add) {
            return value + add;
        }

        public boolean flag() {
            return true;
        }

        public float health() {
            return health;
        }

        public void health(float health) {
            this.health = health;
        }
    }

    public interface WithDefault {
        int a();

        default int b() {
            return 1;
        }
    }

    /** A superclass whose constructor calls an overridable method. */
    public abstract static class Base {
        public int x;
        public String log = "";

        public Base(int x) {
            this.x = x;
            init();
        }

        protected void init() {
            log += "init";
        }

        public abstract String name();

        public int twice(int v) {
            return v * 2;
        }

        public String describe() {
            return name() + x;
        }
    }

    public interface Prim {
        int i();

        boolean z();

        float f();

        String s();
    }

    /** Like arc's Floatf, which Seq.sort also takes besides Comparator. */
    public interface Floatf<T> {
        float get(T value);
    }

    public static String sort(Floatf<Object> f) {
        return "floatf " + f.get(3);
    }

    public static String sort(java.util.Comparator<Object> c) {
        return "comparator " + c.compare(1, 2);
    }

    public static void run(Runnable r) {
        r.run();
    }

    public static int callA(WithDefault w) {
        return w.a();
    }

    public static int callB(WithDefault w) {
        return w.b();
    }

    public static String callPrim(Prim p) {
        return p.i() + "," + p.z() + "," + p.f() + "," + p.s();
    }

    public static Class<?> baseClass() {
        return Base.class;
    }

    public static float takeFloat(float f) {
        return f;
    }

    public static String typeOf(Object o) {
        return o == null ? "null" : o.getClass().getName();
    }
}
