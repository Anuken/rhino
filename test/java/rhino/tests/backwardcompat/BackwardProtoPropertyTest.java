package rhino.tests.backwardcompat;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/backwardcompat/backward-proto-property.js")
@LanguageVersion(Context.VERSION_1_8)
public class BackwardProtoPropertyTest extends ScriptTestsBase {}
