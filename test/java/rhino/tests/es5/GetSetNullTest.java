package rhino.tests.es5;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/extensions/getset-null.js")
@LanguageVersion(Context.VERSION_1_8)
public class GetSetNullTest extends ScriptTestsBase {}
