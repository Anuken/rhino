package rhino.tests.mindustry;

import java.util.List;

/** Classes for {@link MissingClassTest}; that test hides {@link Missing} and {@link GenericMissing}. */
public class MissingFixtures {
    public static class Missing {}

    public static class GenericMissing<T> {}

    public static class UsesMissing {
        public int ok() {
            return 42;
        }

        public void bad(Missing m) {}
    }

    public static class UsesMissingGeneric {
        public List<GenericMissing<String>> list;

        public int ok() {
            return 42;
        }

        public List<GenericMissing<String>> generic() {
            return null;
        }
    }
}
