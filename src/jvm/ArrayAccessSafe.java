package jvm;

import annotations.Alias;

import static jvm.JVM32.*;

public class ArrayAccessSafe {

    @Alias(names = "i64ArrayStore")
    public static void arrayStore(int instance, int index, long value) {
        checkOutOfBounds(instance, index, 8);
        write64(instance + arrayOverhead + (index << 3), value);
    }

    @Alias(names = "i32ArrayStore")
    public static void arrayStore(int instance, int index, int value) {
        checkOutOfBounds(instance, index);
        int clazz = readClass(instance);
        if (clazz != 1 && clazz != 2) throwJs("Incorrect clazz! i32", instance, clazz);
        write32(instance + arrayOverhead + (index << 2), value);
    }

    @Alias(names = "f64ArrayStore")
    public static void arrayStore(int instance, int index, double value) {
        checkOutOfBounds(instance, index, 9);
        write64(instance + arrayOverhead + (index << 3), value);
    }

    @Alias(names = "f32ArrayStore")
    public static void arrayStore(int instance, int index, float value) {
        checkOutOfBounds(instance, index, 3);
        write32(instance + arrayOverhead + (index << 2), value);
    }

    @Alias(names = "i16ArrayStore")
    public static void arrayStore(int instance, int index, short value) {
        checkOutOfBounds(instance, index);
        int clazz = readClass(instance);
        if (clazz != 6 && clazz != 7) throwJs("Incorrect clazz! i16", instance, clazz);
        write16(instance + arrayOverhead + (index << 1), value);
    }

    @Alias(names = "i8ArrayStore")
    public static void arrayStore(int instance, int index, byte value) {
        checkOutOfBounds(instance, index);
        int clazz = readClass(instance);
        if (clazz != 4 && clazz != 5) throw new ClassCastException("Incorrect clazz!");
        write8(instance + arrayOverhead + index, value);
    }

    @Alias(names = "i64ArrayLoad")
    public static long arrayLoad64(int instance, int index) {
        checkOutOfBounds(instance, index, 8);
        return read64(instance + arrayOverhead + (index << 3));
    }

    @Alias(names = "i32ArrayLoad")
    public static int arrayLoad32(int instance, int index) {
        checkOutOfBounds(instance, index);
        int clazz = readClass(instance);
        if (clazz != 1 && clazz != 2) throw new ClassCastException("Incorrect clazz!");
        return read32(instance + arrayOverhead + (index << 2));
    }

    @Alias(names = "f64ArrayLoad")
    public static double arrayLoad64f(int instance, int index) {
        checkOutOfBounds(instance, index, 9);
        return read64f(instance + arrayOverhead + (index << 3));
    }

    @Alias(names = "f32ArrayLoad")
    public static float arrayLoad32f(int instance, int index) {
        checkOutOfBounds(instance, index, 3);
        return read32f(instance + arrayOverhead + (index << 2));
    }

    @Alias(names = "u16ArrayLoad")
    public static char arrayLoad16u(int instance, int index) {
        checkOutOfBounds(instance, index, 6);
        return read16u(instance + arrayOverhead + (index << 1));
    }

    @Alias(names = "s16ArrayLoad")
    public static short arrayLoad16s(int instance, int index) {
        checkOutOfBounds(instance, index, 7);
        return read16s(instance + arrayOverhead + (index << 1));
    }

    @Alias(names = "s8ArrayLoad")
    public static byte arrayLoad8(int instance, int index) {
        checkOutOfBounds(instance, index);
        int clazz = readClass(instance);
        if (clazz != 4 && clazz != 5) throw new ClassCastException("Incorrect clazz!");
        return read8(instance + arrayOverhead + index);
    }

    @Alias(names = "arrayLength")
    public static int arrayLength(int instance) {
        if (instance == 0) throw new NullPointerException("[].length");
        return read32(instance + objectOverhead);
    }
}
