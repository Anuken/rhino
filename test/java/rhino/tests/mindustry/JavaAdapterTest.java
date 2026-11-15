package rhino.tests.mindustry;

import static rhino.tests.mindustry.MindustryScripts.assertBoth;

import org.junit.jupiter.api.Test;

/** JavaAdapter behavior Mindustry scripts rely on, with generated adapter classes. */
class JavaAdapterTest {
    @Test
    void classObjectsWorkAsClasses() {
        assertBoth(
                "true",
                "var c = new java.lang.Object().getClass(); new JavaAdapter(c, {}) instanceof java.lang.Object");
        assertBoth("n3", "new JavaAdapter(F.baseClass(), {name(){ return 'n'; }}, 3).describe()");
        assertBoth("q4", "extend(F.baseClass(), 4, {name(){ return 'q'; }}).describe()");
    }

    @Test
    void defaultMethodsCanBeOverridden() {
        assertBoth(
                "5,7",
                "var w = new JavaAdapter(F.WithDefault, {a(){ return 5; }, b(){ return 7; }});"
                        + "F.callA(w) + ',' + F.callB(w)");
        assertBoth(
                "5,1",
                "var w = new JavaAdapter(F.WithDefault, {a(){ return 5; }}); F.callA(w) + ',' + F.callB(w)");
        assertBoth(
                "5,8",
                "var w = new F.WithDefault(){a(){ return 5; }, b(){ return 8; }}; F.callA(w) + ',' + F.callB(w)");
        assertBoth("5,1", "var w = new F.WithDefault(){a: () => 5}; F.callA(w) + ',' + F.callB(w)");
    }
}
