package jvm;

import annotations.Alias;
import annotations.NoThrow;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static jvm.ArrayAccessUnchecked.arrayLength;
import static jvm.GarbageCollector.GC_OFFSET;
import static jvm.GarbageCollector.iteration;
import static jvm.JVM32.*;
import static jvm.JVMShared.*;
import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;
import static jvm.JavaReflect.getFieldOffset;
import static jvm.JavaReflect.getFields;
import static jvm.NativeLog.log;

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
        int fields = read32(getAddr(fieldOffsetsByClass) + arrayOverhead + (clazz << 2));// fieldsByClass[clazz]
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

    private static int[][] fieldOffsetsByClass;
    private static int[] staticFields;
    public static int[] classSizes;

    @NoThrow
    private static int findFieldsByClass(Class<Object> clazz, int classIdx) {

        Field[] fields = getFields(clazz);
        int instanceFieldCtr = 0;
        int staticFieldCtr = 0;

        if (fields != null) {
            for (Field field : fields) {
                int mods = field.getModifiers();
                // check if type is relevant
                if (!Modifier.isNative(mods)) {
                    if (Modifier.isStatic(mods)) {
                        staticFieldCtr++;
                    } else {
                        instanceFieldCtr++;
                    }
                }
            }
        }

        Class<Object> parentClass = clazz.getSuperclass();
        int[] parentFields = null;
        if (parentClass != null) {
            int parentClassIdx = getSuperClass(classIdx);
            parentFields = fieldOffsetsByClass[parentClassIdx];
            if (parentFields != null) {
                instanceFieldCtr += parentFields.length;
            }
        }

        if (instanceFieldCtr > 0) {
            int[] fieldOffsets = fieldOffsetsByClass[classIdx] = new int[instanceFieldCtr];
            instanceFieldCtr = 0;
            if (parentFields != null) {
                int parentFieldsLength = parentFields.length;
                System.arraycopy(parentFields, 0, fieldOffsets, 0, parentFieldsLength);
                instanceFieldCtr += parentFieldsLength;
            }
            if (fields != null) {
                for (Field field : fields) {
                    int mods = field.getModifiers();
                    // check if type is relevant
                    if (!Modifier.isNative(mods) && !Modifier.isStatic(mods)) {// masking could be optimized
                        fieldOffsets[instanceFieldCtr++] = getFieldOffset(field);
                    }
                }
            }
        }

        return staticFieldCtr;
    }

    @NoThrow
    private static void createGCFieldTable() {
        // classes
        int numClasses = numClasses();
        GCTraversal.fieldOffsetsByClass = new int[numClasses][];
        int[] classSizes = GCTraversal.classSizes = new int[numClasses];
        int staticFieldCtr = 0;
        for (int classIdx = 0; classIdx < numClasses; classIdx++) {
            classSizes[classIdx] = JVM32.getInstanceSize(classIdx);
            Class<Object> clazz = ptrTo(findClass(classIdx));
            staticFieldCtr += findFieldsByClass(clazz, classIdx);
        }
        int ctr = 0;
        int[] staticFields = GCTraversal.staticFields = new int[staticFieldCtr];
        // log("Counted {} static fields", staticFieldCtr);
        for (int i = 0; i < numClasses; i++) {
            Class<Object> clazz = ptrTo(findClass(i));
            Field[] fields = getFields(clazz);
            if (fields == null) continue;
            // count fields
            int staticOffset = findStatic(i, 0);
            for (Field field : fields) {
                int mods = field.getModifiers();
                // check if type is relevant
                if (!Modifier.isNative(mods) && Modifier.isStatic(mods)) {
                    staticFields[ctr++] = staticOffset + getFieldOffset(field);
                }
            }
        }
    }

    static {
        log("Creating GC field table");
        createGCFieldTable();
        log("Finished initializing GC");
    }

}
