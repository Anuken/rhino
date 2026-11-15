package rhino.tests.mindustry;

import static rhino.tests.mindustry.MindustryScripts.assertBoth;

import org.junit.jupiter.api.Test;

/** An adapter's prototype is its own implementation object, not a shared JavaAdapter.prototype. */
class JavaAdapterFieldIsolationTest {
    @Test
    void fieldsBelongToEachInstance() {
        assertBoth(
                "aA1,bB2",
                "var a = extend(F.Base, 1, {name(){ return 'a' + this.tag; }, tag: 'A'});"
                        + "var b = extend(F.Base, 2, {name(){ return 'b' + this.tag; }, tag: 'B'});"
                        + "a.describe() + ',' + b.describe()");
        assertBoth(
                "undefined,1",
                "var a = new JavaAdapter(F.WithDefault, {a(){ return this.k; }}); a.k = 1;"
                        + "var b = new JavaAdapter(F.WithDefault, {a(){ return 2; }});"
                        + "String(b.k) + ',' + F.callA(a)");
        // data properties of the implementation object are visible through this
        assertBoth(
                "3",
                "var a = new JavaAdapter(F.WithDefault, {a(){ return this.data.length; }, data: [1, 2, 3]});"
                        + "F.callA(a)");
        assertBoth("true", "String(new JavaAdapter(F.WithDefault, {a(){ return 1; }}) instanceof F.WithDefault)");
        assertBoth("q9", "var e = extend(F.Base, 4, {name(){ return 'q'; }, x: 9}); e.describe()");
    }
}
