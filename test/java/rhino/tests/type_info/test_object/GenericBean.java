package rhino.tests.type_info.test_object;

/**
 * @author ZZZank
 */
public class GenericBean<M extends Number> {
    private M value;

    public M publicValue;

    private M valueMultipleSetters;

    public M getValue() {
        return value;
    }

    public void setValue(M value) {
        this.value = value;
    }

    public M getValueMultipleSetters() {
        return valueMultipleSetters;
    }

    public void setValueMultipleSetters(M valueMultipleSetters) {
        this.valueMultipleSetters = valueMultipleSetters;
    }

    public void setValueMultipleSetters(String s) {
        throw new UnsupportedOperationException("Should not be called");
    }
}
