package rhino.tests.type_info.test_object;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ZZZank
 */
public class IntegerArrayList extends ArrayList<Integer> {
    public static List<Integer> createTestObject() {
        List<Integer> list = new IntegerArrayList();

        list.add(42);
        list.add(7);
        return list;
    }
}
