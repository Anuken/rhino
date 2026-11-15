package rhino.tests.es2020;

import rhino.Context;
import rhino.drivers.LanguageVersion;
import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest("testsrc/jstests/es2020/string-match-all.js")
@LanguageVersion(Context.VERSION_ES6)
public class StringMatchAllTest extends ScriptTestsBase {}
