package rhino;

/** Can provide source code for a built in Function */
public class BuiltInSourceCodeProvider implements SourceCodeProvider {
    public static final BuiltInSourceCodeProvider INSTANCE = new BuiltInSourceCodeProvider();

    private BuiltInSourceCodeProvider() {}

    @Override
    public String getSource(JSDescriptor<?> desc, int start, int end) {
        return String.format("function %s() {\n\t[native code]\n}", desc.getFunctionName());
    }

    @Override
    public String getRawSource() {
        return String.format("/*[native code]*/\n}");
    }
}
