package rhino.tests;

import rhino.drivers.RhinoTest;
import rhino.drivers.ScriptTestsBase;

@RhinoTest(value = "testsrc/jstests/call-undefined-properties.js")
public class CallUndefinedPropertiesTest extends ScriptTestsBase {}
