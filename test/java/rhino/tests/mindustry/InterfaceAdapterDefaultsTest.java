package rhino.tests.mindustry;

import static rhino.tests.mindustry.MindustryScripts.assertBoth;

import org.junit.jupiter.api.Test;

/** JS objects converted to interfaces return zero values from methods they don't define. */
class InterfaceAdapterDefaultsTest {
    @Test
    void missingMethodsReturnDefaults() {
        assertBoth("0,false,0.0,x", "F.callPrim({s(){ return 'x'; }})");
        assertBoth("null", "String(F.callPrim({i(){ return 1; }, z(){ return true; }, f(){ return 2; }}).split(',')[3])");
    }
}
