package rhino.tests.harmony;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/harmony/property-ordering.js")
@LanguageVersion(Context.VERSION_ES6)
public class PropertyOrderingTest extends ScriptTestsBase {}
