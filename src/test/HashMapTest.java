package test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static jvm.NativeLog.log;

public class HashMapTest {

    private static void assertTrue(boolean x) {
        if (!x) throw new IllegalStateException("expected true");
    }

    private static void assertFalse(boolean x) {
        if (x) throw new IllegalStateException("expected false");
    }

    private static void assertEquals(Object a, Object b) {
        if (!(a == b || a.equals(b)))
            throw new IllegalStateException(a + " != " + b);
    }

    // Check that a new HashMap returns 'true' for isEmpty
    public static void testIsEmptyForNewMap() {
        HashMap<String, Integer> map = new HashMap<>();
        assertTrue(map.isEmpty());
    }

    // Test puting an element makes isEmpty return 'false'

    public static void testputMakesIsEmptyFalse() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("Hello", 5);
        assertFalse(map.isEmpty());
    }

    // Check that size returns 0 for new HashMaps

    public static void testSizeForNewMap() {
        HashMap<String, Integer> map = new HashMap<>();
        assertEquals(0, map.size());
    }

    // Test size increases as elements are puted

    public static void testSizeIncrementsWhenputingElements() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("Hello", 5);
        assertEquals(1, map.size());
        map.put("Goodbye", 5);
        assertEquals(2, map.size());
    }

    // Make sure get returns the values puted under keys

    public static void testGetReturnsCorrectValue() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("Hello", 5);
        map.put("Goodbye", 6);
        assertEquals(5, map.get("Hello"));
        assertEquals(6, map.get("Goodbye"));
    }

    // Test thats an puted element replaces another with the same key

    public static void testReplacesValueWithSameKey() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("Hello", 5);
        map.put("Hello", 6);
        assertEquals(6, map.get("Hello"));
    }

    // Make sure that two (non-equal) keys with the same hash do not overwrite each other

    public static void testDoesNotOverwriteSeperateKeysWithSameHash() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("Ea", 5);
        map.put("FB", 6);
        assertEquals(5, map.get("Ea"));
        assertEquals(6, map.get("FB"));
    }

    // Make sure size doesn't decrement below 0

    public static void testRemoveDoesNotEffectNewMap() {
        HashMap<String, Integer> map = new HashMap<>();
        map.remove("Hello");
        assertEquals(0, map.size());
    }

    // Make sure that size decrements as elements are used
    public static void testRemoveDecrementsSize() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("Hello", 5);
        map.put("Goodbye", 6);
        map.remove("Hello");
        assertEquals(1, map.size());
        map.remove("Goodbye");
        assertEquals(0, map.size());
    }

    // Test elements are actually removed when remove is called
    public static void testRemoveDeletesElement() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("Hello", 5);
        map.remove("Hello");
        assertEquals(map.size(), 0);
        assertEquals(map.get("Hello"), null);
    }

    // Test that contains is 'false' for new maps

    public static void testContainsKeyForNewMap() {
        HashMap<String, Integer> map = new HashMap<>();
        assertFalse(map.containsKey("Hello"));
    }

    // Test that contains returns 'false' when key doesn't exist
    public static void testContainsKeyForNonExistingKey() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("Hello", 5);
        assertFalse(map.containsKey("Goodbye"));
    }

    // Make sure that contains returns 'true' when the key does exist
    public static void testContainsKeyForExistingKey() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("Hello", 5);
        assertTrue(map.containsKey("Hello"));
    }

    // Check that contains is not fooled by equivalent hash codes
    public static void testContainsKeyForKeyWithEquivalentHash() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("Ea", 5);
        assertFalse(map.containsKey("FB"));
    }

    public static void run() {

        testContainsKeyForNewMap();
        testContainsKeyForExistingKey();
        testContainsKeyForKeyWithEquivalentHash();
        testDoesNotOverwriteSeperateKeysWithSameHash();
        testIsEmptyForNewMap();
        testRemoveDecrementsSize();
        testRemoveDeletesElement();
        testReplacesValueWithSameKey();
        testRemoveDoesNotEffectNewMap();
        testputMakesIsEmptyFalse();
        testSizeIncrementsWhenputingElements();
        testContainsKeyForNonExistingKey();
        testGetReturnsCorrectValue();
        testSizeForNewMap();

        // to do potential cause for failure: dup_x2i32i32i32
        // to do check that it's correct!
        // to do actual cause: hashMap is calling hashcode without resolveIndirect

        HashMap<String, String> testMap = new HashMap<>();
        ArrayList<String> control = new ArrayList<>();

        try {

            log("2x: 0 v0 add");
            test(str0(), "v0", true, testMap, control);
            test(str0(), "v1", true, testMap, control);

            for (int i = 0; i < 1000000; i++) {
                double op = Math.random();
                String key = Integer.toHexString((int) (Math.random() * i));
                String value = "v" + Integer.toHexString(i);
                boolean add = op < 0.8;
                log(key, value, add ? "add" : "remove");
                test(key, value, add, testMap, control);
            }

        } catch (IllegalStateException e) {
            e.printStackTrace();
            log(Arrays.toString(testMap.entrySet().toArray()));
            log(Arrays.toString(control.toArray()));
        }

    }

    private static String str0() {
        return "" + (int) Math.cos(0.1);
    }

    private static void test(String key, String value, boolean add, HashMap<String, String> testMap, ArrayList<String> control) {
        log("adding", key, key.hashCode());
        if (add) {
            // add new key
            testMap.put(key, value);
            int idx = control.indexOf(key);
            if (idx < 0) {
                control.add(key);
                control.add(value);
            } else {
                control.set(idx + 1, value);
            }
        } else {
            // remove key
            testMap.remove(key);
            int index = control.indexOf(key);
            if (index >= 0) {
                control.remove(index + 1);
                control.remove(index);
            }
        }
        // check if the state is still correct
        assertEquals(testMap.size(), control.size() >> 1);
        for (int j = 0; j < control.size(); j += 2) {
            assertTrue(testMap.containsKey(control.get(j)));
            assertTrue(testMap.containsValue(control.get(j + 1)));
        }
    }

}
