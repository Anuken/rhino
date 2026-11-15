package rhino.tests;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/redefined-undefined.js")
@LanguageVersion(Context.VERSION_ES6)
public class RedefinedUndefinedTest extends ScriptTestsBase {}
