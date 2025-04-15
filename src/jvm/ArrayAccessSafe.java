package jvm;

import annotations.Alias;

import static jvm.ArrayAccessUnchecked.*;
import static jvm.JVMFlags.ptrSize;
import static jvm.JVMShared.*;
import static jvm.ThrowJS.throwJs;
import static utils.StaticClassIndices.*;

public class ArrayAccessSafe {

    @Alias(names = "i32ArrayStore")
    public static void arrayStore32(Object instance, int index, int value) {
        checkOutOfBounds(instance, index, ptrSize == 4 ? OBJECT_ARRAY : INT_ARRAY, INT_ARRAY);
        arrayStoreU32(instance, index, value);
    }

    @Alias(names = "i64ArrayStore")
    public static void arrayStore(Object instance, int index, long value) {
        checkOutOfBounds(instance, index, ptrSize == 8 ? OBJECT_ARRAY : LONG_ARRAY, LONG_ARRAY);
        arrayStoreU64(instance, index, value);
    }

    @Alias(names = "ptrArrayStore")
    public static void arrayStorePtr(Object[] instance, int index, Object value) {
        checkOutOfBounds(instance, index, OBJECT_ARRAY);
        arrayStoreUPtr(instance, index, value);
    }

    @Alias(names = "f32ArrayStore")
    public static void arrayStore32(float[] instance, int index, float value) {
        checkOutOfBounds(instance, index, FLOAT_ARRAY);
        arrayStoreU32(instance, index, value);
    }

    @Alias(names = "f64ArrayStore")
    public static void arrayStore64(double[] instance, int index, double value) {
        checkOutOfBounds(instance, index, DOUBLE_ARRAY);
        arrayStoreU64(instance, index, value);
    }

    @Alias(names = "i16ArrayStore")
    public static void arrayStore16(Object instance, int index, short value) {
        checkOutOfBounds(instance, index, SHORT_ARRAY, CHAR_ARRAY);
        ArrayAccessUnchecked.arrayStoreU32(instance, index, value);
    }

    @Alias(names = "i8ArrayStore")
    public static void arrayStore8(Object instance, int index, byte value) {
        checkOutOfBounds(instance, index, BOOLEAN_ARRAY, BYTE_ARRAY);
        arrayStore8U(instance, index, value);
    }

    @Alias(names = "i32ArrayLoad")
    public static int arrayLoad32(Object instance, int index) {
        checkOutOfBounds(instance, index, ptrSize == 4 ? OBJECT_ARRAY : INT_ARRAY, INT_ARRAY);
        return arrayLoad32U(instance, index);
    }

    @Alias(names = "i64ArrayLoad")
    public static long arrayLoad64(Object instance, int index) {
        checkOutOfBounds(instance, index, ptrSize == 8 ? OBJECT_ARRAY : LONG_ARRAY, LONG_ARRAY);
        return arrayLoad64U(instance, index);
    }

    @Alias(names = "f32ArrayLoad")
    public static float arrayLoad32f(float[] instance, int index) {
        checkOutOfBounds(instance, index, FLOAT_ARRAY);
        return arrayLoad32fU(instance, index);
    }

    @Alias(names = "f64ArrayLoad")
    public static double arrayLoad64f(double[] instance, int index) {
        checkOutOfBounds(instance, index, DOUBLE_ARRAY);
        return arrayLoad64fU(instance, index);
    }

    @Alias(names = "s8ArrayLoad")
    public static byte arrayLoad8(Object instance, int index) {
        checkOutOfBounds(instance, index, BOOLEAN_ARRAY, BYTE_ARRAY);
        return arrayLoad8U(instance, index);
    }

    @Alias(names = "u16ArrayLoad")
    public static char arrayLoad16u(char[] instance, int index) {
        checkOutOfBounds(instance, index, CHAR_ARRAY);
        return arrayLoad16uU(instance, index);
    }

    @Alias(names = "s16ArrayLoad")
    public static short arrayLoad16s(short[] instance, int index) {
        checkOutOfBounds(instance, index, SHORT_ARRAY);
        return arrayLoad16sU(instance, index);
    }

    @Alias(names = "arrayLength")
    public static int arrayLength(Object instance) {
        if (instance == null) throw new NullPointerException("[].length");
        int classId = readClassId(instance);
        if (classId < FIRST_ARRAY || classId > LAST_ARRAY) {
            throw new ClassCastException("[].length for non-array");
        }
        return arrayLengthU(instance);
    }

    private static void checkOutOfBounds(Object instance, int index, int expectedClassId) {
        checkOutOfBounds(instance, index, expectedClassId, expectedClassId);
    }

    private static void checkOutOfBounds(Object instance, int index, int expectedClassId1, int expectedClassId2) {
        if (instance == null) throw new NullPointerException("isOOB");
        // checkAddress(instance);
        int actualClassId = readClassId(instance);
        if (actualClassId != expectedClassId1 && actualClassId != expectedClassId2) {
            throwJs("Incorrect clazz!", castToPtr(instance), actualClassId, expectedClassId1);
        }
        int length = arrayLengthU(instance);
        if (ge_ub(index, length)) {
            throw new IndexOutOfBoundsException();
        }
    }
}
