package rhino.debug;

import rhino.Context;

class NoOpDebugger implements Debugger {
    @Override
    public DebugFrame getFrame(Context cx, DebuggableScript fnOrScript) {
        return new NoOpDebugFrame();
    }

    private static class NoOpDebugFrame implements DebugFrame {}
}
