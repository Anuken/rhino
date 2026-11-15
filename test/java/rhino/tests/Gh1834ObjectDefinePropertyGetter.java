package rhino.tests;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/gh1834-define-property-getter.js")
@LanguageVersion(Context.VERSION_ES6)
public class Gh1834ObjectDefinePropertyGetter extends ScriptTestsBase {}
