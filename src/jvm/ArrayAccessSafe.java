package jvm;

import annotations.Alias;

import static jvm.JVM32.*;
import static jvm.JVMShared.ge_ub;
import static jvm.ThrowJS.throwJs;
import static utils.StaticClassIndices.*;

public class ArrayAccessSafe {

    @Alias(names = "i32ArrayStore")
    public static void arrayStore(Object instance, int index, int value) {
        //noinspection ConstantValue
        checkOutOfBounds(instance, index, ptrSize == 4 ? OBJECT_ARRAY : INT_ARRAY, INT_ARRAY);
        writeI32AtOffset(instance, arrayOverhead + (index << 2), value);
    }

    @Alias(names = "i64ArrayStore")
    public static void arrayStore(Object instance, int index, long value) {
        //noinspection ConstantValue
        checkOutOfBounds(instance, index, ptrSize == 8 ? OBJECT_ARRAY : LONG_ARRAY, LONG_ARRAY);
        writeI64AtOffset(instance, arrayOverhead + (index << 3), value);
    }

    @Alias(names = "f32ArrayStore")
    public static void arrayStore(Object instance, int index, float value) {
        checkOutOfBounds(instance, index, FLOAT_ARRAY);
        writeF32AtOffset(instance, arrayOverhead + (index << 2), value);
    }

    @Alias(names = "f64ArrayStore")
    public static void arrayStore(Object instance, int index, double value) {
        checkOutOfBounds(instance, index, DOUBLE_ARRAY);
        writeF64AtOffset(instance, arrayOverhead + (index << 3), value);
    }

    @Alias(names = "i16ArrayStore")
    public static void arrayStore(Object instance, int index, short value) {
        checkOutOfBounds(instance, index, SHORT_ARRAY, CHAR_ARRAY);
        writeI16AtOffset(instance, arrayOverhead + (index << 1), value);
    }

    @Alias(names = "i8ArrayStore")
    public static void arrayStore(Object instance, int index, byte value) {
        checkOutOfBounds(instance, index, BOOLEAN_ARRAY, BYTE_ARRAY);
        writeI8AtOffset(instance, arrayOverhead + index, value);
    }

    @Alias(names = "i32ArrayLoad")
    public static int arrayLoad32(Object instance, int index) {
        //noinspection ConstantValue
        checkOutOfBounds(instance, index, ptrSize == 4 ? OBJECT_ARRAY : INT_ARRAY, INT_ARRAY);
        return readI32AtOffset(instance, arrayOverhead + (index << 2));
    }

    @Alias(names = "i64ArrayLoad")
    public static long arrayLoad64(Object instance, int index) {
        //noinspection ConstantValue
        checkOutOfBounds(instance, index, ptrSize == 8 ? OBJECT_ARRAY : LONG_ARRAY, LONG_ARRAY);
        return readI64AtOffset(instance, arrayOverhead + (index << 3));
    }

    @Alias(names = "f32ArrayLoad")
    public static float arrayLoad32f(Object instance, int index) {
        checkOutOfBounds(instance, index, FLOAT_ARRAY);
        return readF32AtOffset(instance, arrayOverhead + (index << 2));
    }

    @Alias(names = "f64ArrayLoad")
    public static double arrayLoad64f(Object instance, int index) {
        checkOutOfBounds(instance, index, DOUBLE_ARRAY);
        return readF64AtOffset(instance, arrayOverhead + (index << 3));
    }

    @Alias(names = "s8ArrayLoad")
    public static byte arrayLoad8(Object instance, int index) {
        checkOutOfBounds(instance, index, BOOLEAN_ARRAY, BYTE_ARRAY);
        return readI8AtOffset(instance, arrayOverhead + index);
    }

    @Alias(names = "u16ArrayLoad")
    public static char arrayLoad16u(Object instance, int index) {
        checkOutOfBounds(instance, index, CHAR_ARRAY);
        return readU16AtOffset(instance, arrayOverhead + (index << 1));
    }

    @Alias(names = "s16ArrayLoad")
    public static short arrayLoad16s(Object instance, int index) {
        checkOutOfBounds(instance, index, SHORT_ARRAY);
        return readS16AtOffset(instance, arrayOverhead + (index << 1));
    }

    @Alias(names = "arrayLength")
    public static int arrayLength(Object instance) {
        if (instance == null) throw new NullPointerException("[].length");
        return readI32AtOffset(instance, objectOverhead);
    }

    private static void checkOutOfBounds(Object instance, int index, int expectedClassId) {
        checkOutOfBounds(instance, index, expectedClassId, expectedClassId);
    }

    private static void checkOutOfBounds(Object instance, int index, int expectedClassId1, int expectedClassId2) {
        if (instance == null) throw new NullPointerException("isOOB");
        // checkAddress(instance);
        int actualClassId = readClassId(instance);
        if (actualClassId != expectedClassId1 && actualClassId != expectedClassId2) {
            throwJs("Incorrect clazz!", getAddr(instance), actualClassId, expectedClassId1);
        }
        int length = readI32AtOffset(instance, objectOverhead);
        if (ge_ub(index, length)) {
            throw new IndexOutOfBoundsException();
        }
    }
}
