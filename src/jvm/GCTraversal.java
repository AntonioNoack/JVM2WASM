package jvm;

import annotations.Alias;
import annotations.NoThrow;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static jvm.ArrayAccessUnchecked.arrayLength;
import static jvm.GC.GC_OFFSET;
import static jvm.GC.iteration;
import static jvm.JVM32.*;
import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;
import static jvm.JavaReflect.getFieldOffset;
import static jvm.JavaReflect.getFields;

/**
 * "Mark" of mark-and-sweep
 */
public class GCTraversal {

    @NoThrow
    static void traverseStaticInstances() {
        int ptr = getAddr(staticFields);
        int length = arrayLength(ptr);
        ptr += arrayOverhead;
        int endPtr = ptr + (length << 2);
        // log("Traversing static instances", ptr, endPtr);
        while (ptr < endPtr) {
            int fieldAddr = read32(ptr);
            int fieldValue = read32(fieldAddr);
            traverse(fieldValue);
            ptr += 4;
        }
    }

    @NoThrow
    @Alias(names = "gcMarkUsed")
    private static void traverse(int instance) {
        // check addr for ignored sections and NULL
        if (unsignedGreaterThanEqual(instance, getAllocationStart())) {
            int statePtr = instance + GC_OFFSET;
            byte state = read8(statePtr);
            byte iter = iteration;
            // log("Traversal", addr, iter, state);
            if (state != iter) {
                write8(statePtr, iter);
                int clazz = readClass(instance);
                if (clazz == 1) {
                    traverseObjectArray(instance);
                } else {
                    traverseInstance(instance, clazz);
                }
            }// else already done
        }// else ignore it
    }

    @NoThrow
    private static void traverseObjectArray(int instance) {
        int length = arrayLength(instance);
        int ptr = instance + arrayOverhead;
        int endPtr = ptr + (length << 2);
        while (unsignedLessThan(ptr, endPtr)) {
            traverse(read32(ptr));
            ptr += 4;
        }
    }

    @NoThrow
    private static void traverseInstance(int instance, int clazz) {
        int fields = read32(getAddr(fieldsByClass) + arrayOverhead + (clazz << 2));// fieldsByClass[clazz]
        if (fields == 0) return; // no fields to process
        int size = arrayLength(fields);
        fields += arrayOverhead;
        int endPtr = fields + (size << 2);
        while (fields < endPtr) {
            int offset = read32(fields);
            traverse(read32(instance + offset));
            fields += 4;
        }
    }

    private static int[][] fieldsByClass;
    private static int[] staticFields;
    public static int[] classSizes;

    private static void createGCFieldTable() {
        // classes
        int nc = numClasses();
        int[][] fieldsByClass2 = new int[nc][];
        fieldsByClass = fieldsByClass2;
        int[] classSizes2 = new int[nc];
        classSizes = classSizes2;
        int staticFieldCtr = 0;
        for (int i = 0; i < nc; i++) {
            classSizes2[i] = JVM32.getInstanceSizeC(i);
            Class<Object> clazz = ptrTo(findClass(i));
            int fields = getFields(clazz);
            if (fields == 0) continue;
            // count fields
            int fieldCtr = 0;
            int length = arrayLength(fields);
            int ptr = fields + arrayOverhead;
            for (int j = 0; j < length; j++) {
                Field field = ptrTo(read32(ptr));
                int mods = field.getModifiers();
                // check if type is relevant
                if (!Modifier.isNative(mods)) {
                    if (Modifier.isStatic(mods)) {
                        staticFieldCtr++;
                    } else {
                        fieldCtr++;
                    }
                }
                ptr += 4;
            }
            if (fieldCtr > 0) {
                int[] offsets = new int[fieldCtr];
                fieldsByClass2[i] = offsets;
                ptr = fields + arrayOverhead;
                fieldCtr = 0;
                for (int j = 0; j < length; j++) {
                    Field field = ptrTo(read32(ptr));
                    int mods = field.getModifiers();
                    // check if type is relevant
                    if (!Modifier.isNative(mods) && !Modifier.isStatic(mods)) {// masking could be optimized
                        offsets[fieldCtr++] = getFieldOffset(field);
                    }
                    ptr += 4;
                }
            }
        }
        int ctr = 0;
        int[] staticFields2 = new int[staticFieldCtr];
        // log("Counted {} static fields", staticFieldCtr);
        staticFields = staticFields2;
        for (int i = 0; i < nc; i++) {
            Class<Object> clazz = ptrTo(findClass(i));
            int fields = getFields(clazz);
            if (fields == 0) continue;
            // count fields
            int length = arrayLength(fields);
            int staticOffset = findStatic(i, 0);
            int ptr = fields + arrayOverhead;
            for (int j = 0; j < length; j++) {
                Field field = ptrTo(read32(ptr));
                int mods = field.getModifiers();
                // check if type is relevant
                if (!Modifier.isNative(mods)) {
                    if (Modifier.isStatic(mods)) {
                        staticFields2[ctr++] = staticOffset + getFieldOffset(field);
                    }
                }
                ptr += 4;
            }
        }
    }

    static {
        log("Creating GC field table");
        createGCFieldTable();
        log("Finished initializing GC");
    }

}
