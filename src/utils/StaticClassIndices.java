package utils;

import me.anno.utils.assertions.AssertionsKt;
import translator.GeneratorIndex;

public class StaticClassIndices {
    public static final int OBJECT = 0;
    public static final int OBJECT_ARRAY = 1;
    public static final int INT_ARRAY = 2;
    public static final int FLOAT_ARRAY = 3;
    public static final int BOOLEAN_ARRAY = 4;
    public static final int BYTE_ARRAY = 5;
    public static final int CHAR_ARRAY = 6;
    public static final int SHORT_ARRAY = 7;
    public static final int LONG_ARRAY = 8;
    public static final int DOUBLE_ARRAY = 9;
    public static final int STRING = 10;

    public static final int NATIVE_INT = 16;
    public static final int NATIVE_LONG = 17;
    public static final int NATIVE_FLOAT = 18;
    public static final int NATIVE_DOUBLE = 19;
    public static final int NATIVE_BOOLEAN = 20;
    public static final int NATIVE_BYTE = 21;
    public static final int NATIVE_SHORT = 22;
    public static final int NATIVE_CHAR = 23;
    public static final int NATIVE_VOID = 24;

    public static final int FIRST_NATIVE = NATIVE_INT;
    public static final int LAST_NATIVE = NATIVE_VOID;

    public static final int FIRST_ARRAY = OBJECT_ARRAY;
    public static final int LAST_ARRAY = DOUBLE_ARRAY;
    public static final int NUM_ARRAYS = LAST_ARRAY - FIRST_ARRAY + 1;

    private static int getClassIndex(String className) {
        return GeneratorIndex.INSTANCE.getClassIndex(className);
    }

    public static void validateClassIndices() {
        assertEquals(getClassIndex("java/lang/Object"), OBJECT);
        assertEquals(getClassIndex("java/lang/String"), STRING);
        assertEquals(getClassIndex("[]"), OBJECT_ARRAY);
        assertEquals(getClassIndex("[I"), INT_ARRAY);
        assertEquals(getClassIndex("[F"), FLOAT_ARRAY);
        assertEquals(getClassIndex("[Z"), BOOLEAN_ARRAY);
        assertEquals(getClassIndex("[B"), BYTE_ARRAY);
        assertEquals(getClassIndex("[C"), CHAR_ARRAY);
        assertEquals(getClassIndex("[S"), SHORT_ARRAY);
        assertEquals(getClassIndex("[J"), LONG_ARRAY);
        assertEquals(getClassIndex("[D"), DOUBLE_ARRAY);
        assertEquals(getClassIndex("int"), NATIVE_INT);
        assertEquals(getClassIndex("long"), NATIVE_LONG);
        assertEquals(getClassIndex("float"), NATIVE_FLOAT);
        assertEquals(getClassIndex("double"), NATIVE_DOUBLE);
        assertEquals(getClassIndex("boolean"), NATIVE_BOOLEAN);
        assertEquals(getClassIndex("byte"), NATIVE_BYTE);
        assertEquals(getClassIndex("short"), NATIVE_SHORT);
        assertEquals(getClassIndex("char"), NATIVE_CHAR);
        assertEquals(getClassIndex("void"), NATIVE_VOID);
    }

    private static void assertEquals(int expected, int actual) {
        AssertionsKt.assertEquals(expected, actual, "Expected same value");
    }
}
