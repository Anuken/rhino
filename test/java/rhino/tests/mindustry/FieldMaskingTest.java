package rhino.tests.mindustry;

import static rhino.tests.mindustry.MindustryScripts.assertBoth;

import org.junit.jupiter.api.Test;

/** A public field hides methods of the same name from property access, but calls find them. */
class FieldMaskingTest {
    @Test
    void instanceFields() {
        assertBoth("5,6", "var m = new F.Masked(); m.value + ',' + m.value(1)");
        assertBoth("number,true", "var m = new F.Masked(); typeof m.value + ',' + (m.value === 5)");
        assertBoth("falsy", "var m = new F.Masked(); m.flag ? 'truthy' : 'falsy'");
        assertBoth("3", "var m = new F.Masked(); m.health = 3; F.takeFloat(m.health)");
        assertBoth("7,7", "var m = new F.Masked(); m.health(7); m.health + ',' + m.health()");
    }

    @Test
    void staticFields() {
        assertBoth("2,3", "F.Masked.count + ',' + F.Masked.count()");
        assertBoth("number", "typeof F.Masked.count");
    }

    @Test
    void adapterSuperCalls() {
        assertBoth(
                "5,60",
                "var m = new JavaAdapter(F.Masked, { value(add){ return this.super$value(add) * 10; } });"
                        + "m.value + ',' + m.value(1)");
    }
}
