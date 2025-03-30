package jvm;

import annotations.Alias;
import annotations.Export;
import annotations.NoThrow;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static jvm.GCGapFinder.getInstanceSize;
import static jvm.GarbageCollector.GC_OFFSET;
import static jvm.GarbageCollector.iteration;
import static jvm.JVM32.*;
import static jvm.JVMShared.*;
import static jvm.JavaReflect.getFieldOffset;
import static jvm.JavaReflect.getFields;
import static jvm.NativeLog.log;
import static utils.StaticClassIndices.OBJECT_ARRAY;

/**
 * "Mark" of mark-and-sweep
 */
public class GCTraversal {

    @NoThrow
    static void traverseStaticInstances() {
        int numStaticFields = staticFieldOffsets.length;
        // validateAllClassIds();
        log("Traversing static instances", getAllocationStart(), getNextPtr(), numStaticFields);
        for (int offset : staticFieldOffsets) {
            traverse(readPtrAtOffset(null, offset));
        }
        log("Finished traversing static instances");
    }

    @NoThrow
    public static void validateAllClassIds() {
        int ptr = getAllocationStart();
        int end = getNextPtr();
        // log("Validating all dynamic instances", ptr, end, getStackDepth());
        while (unsignedLessThan(ptr, end)) {
            int size = getInstanceSize(ptr);
            // String className = classIdToInstance(readClassIdImpl(ptr)).getName();
            // log(className, ptr, size);
            ptr += size;
        }
        if (ptr != end) {
            log("Invalid end", ptr, end);
            throw new IllegalStateException();
        }
        // log("Finished validation");
    }

    @Export
    @NoThrow
    @Alias(names = "gcMarkUsed")
    private static void traverse(Object instance) {
        // check addr for ignored sections and NULL
        if (!isDynamicInstance(instance)) return; // no need for GC
        byte state = readI8AtOffset(instance, GC_OFFSET);

        int classId = readClassId(instance);
        /*if (classId >= 0 && classId < numClasses()) {
            String className = classIdToInstance(classId).getName();
            log("Traversal", className, getAddr(instance));
        } else {
            log("Invalid class ID", getAddr(instance), classId);
            throw new IllegalStateException();
        }*/

        byte iter = iteration;
        if (state == iter) return; // already handled

        writeI8AtOffset(instance, GC_OFFSET, iter);
        if (classId == OBJECT_ARRAY) {
            for (Object instanceI : (Object[]) instance) {
                traverse(instanceI);
            }
        } else {
            int[] offsets = instanceFieldOffsets[classId];
            if (offsets == null) return;
            for (int offset : offsets) {
                traverse(readPtrAtOffset(instance, offset));
            }
        }
    }

    public static int[][] instanceFieldOffsets;
    private static int[] staticFieldOffsets;
    private static final int[] emptyArray = new int[0];

    @NoThrow
    private static int findFieldsByClass(Class<Object> clazz, int classId) {

        Field[] fields = getFields(clazz);
        int instanceFieldCtr = 0;
        int staticFieldCtr = 0;

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

        Class<Object> parentClass = clazz.getSuperclass();
        int[] parentFields = null;
        if (parentClass != null) {
            int parentClassIdx = getSuperClassId(classId);
            parentFields = instanceFieldOffsets[parentClassIdx];
            instanceFieldCtr += parentFields.length;
        }

        if (instanceFieldCtr > 0) {
            int[] fieldOffsets = instanceFieldOffsets[classId] = new int[instanceFieldCtr];
            instanceFieldCtr = 0;
            if (parentFields != null) {
                int parentFieldsLength = parentFields.length;
                System.arraycopy(parentFields, 0, fieldOffsets, 0, parentFieldsLength);
                instanceFieldCtr += parentFieldsLength;
            }
            for (Field field : fields) {
                int mods = field.getModifiers();
                // check if type is relevant
                if (!Modifier.isNative(mods) && !Modifier.isStatic(mods)) {// masking could be optimized
                    fieldOffsets[instanceFieldCtr++] = getFieldOffset(field);
                }
            }
        }

        return staticFieldCtr;
    }

    @NoThrow
    private static void createGCFieldTable() {
        int numClasses = numClasses();
        int[][] instanceFieldOffsets = GCTraversal.instanceFieldOffsets = new int[numClasses][];
        int[] emptyArray = GCTraversal.emptyArray;
        for (int i = 0; i < numClasses; i++) {
            instanceFieldOffsets[i] = emptyArray;
        }
        int staticFieldCtr = 0;
        for (int classIdx = 0; classIdx < numClasses; classIdx++) {
            Class<Object> clazz = classIdToInstance(classIdx);
            staticFieldCtr += findFieldsByClass(clazz, classIdx);
        }
        int[] staticFields = GCTraversal.staticFieldOffsets = new int[staticFieldCtr];
        staticFieldCtr = 0;
        for (int classId = 0; classId < numClasses; classId++) {
            Class<Object> clazz = classIdToInstance(classId);
            Field[] fields = getFields(clazz);
            // count fields
            int staticOffset = findStatic(classId, 0);
            for (Field field : fields) {
                int mods = field.getModifiers();
                // check if type is relevant
                if (!Modifier.isNative(mods) && Modifier.isStatic(mods)) {
                    staticFields[staticFieldCtr++] = staticOffset + getFieldOffset(field);
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
