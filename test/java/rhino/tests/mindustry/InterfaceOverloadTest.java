package rhino.tests.mindustry;

import static rhino.tests.mindustry.MindustryScripts.assertBoth;
import static rhino.tests.mindustry.MindustryScripts.assertThrowsBoth;

import org.junit.jupiter.api.Test;

import rhino.EvaluatorException;

/** Objects passed to methods overloaded on interfaces pick the interface they implement. */
class InterfaceOverloadTest {
    @Test
    void objectsPickTheInterfaceTheyImplement() {
        assertBoth("comparator -1", "F.sort({compare(a, b){ return a - b; }})");
        assertBoth("floatf 6.0", "F.sort({get(v){ return v * 2; }})");
        assertBoth(
                "comparator 1",
                "class Desc { compare(a, b){ return b - a; } }; F.sort(new Desc())");
        // the choice depends on each object, not only on its class
        assertBoth(
                "comparator -1,floatf 3.0,comparator -1",
                "var objs = [{compare(a, b){ return a - b; }}, {get(v){ return v; }}];"
                        + "[0, 1, 0].map(i => F.sort(objs[i])).join()");
    }

    @Test
    void otherwiseAmbiguous() {
        assertThrowsBoth(EvaluatorException.class, "F.sort({})");
        assertThrowsBoth(EvaluatorException.class, "F.sort((a, b) => a - b)");
    }
}
