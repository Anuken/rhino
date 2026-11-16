package rhino.tests.type_info.test_object;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ZZZank
 */
public class NumberArrayList extends ArrayList<Number> {
    public static List<Number> createTestObject() {
        List<Number> list = new NumberArrayList();

        list.add(42);
        list.add(7.5);
        return list;
    }
}
