package rhino.tests;

import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest(value = "testsrc/jstests/inside-strict-mode.js")
public class InsideStrictModeTest extends ScriptTestsBase {}
