package rhino.tests;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/extensions/obsolete-generators.js")
@LanguageVersion(Context.VERSION_1_8)
public class ObsoleteGeneratorTest extends ScriptTestsBase {}
