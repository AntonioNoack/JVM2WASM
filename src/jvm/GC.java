package jvm;

import annotations.Alias;
import annotations.JavaScript;
import annotations.NoThrow;
import jvm.custom.WeakRef;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import static jvm.JVM32.*;
import static jvm.JavaLang.*;

public class GC {

    public static int freeMemory = 0;

    public static byte iteration = 0;

    public static final int GCOffset = 3;

    public static int[] largestGaps = new int[16];// 16 pointers
    private static int[] largestGapsTmp = new int[16];
    private static boolean hasGapsTmp = false;


    public static int generation = 1;

    @NoThrow
    @JavaScript(code = "markJSReferences()")
    private static native void markJSReferences();

    @NoThrow
    @Alias(names = "gc")
    public static void invokeGC() {
        // log("Running GC");
        GCX.hasGaps = false;
        // run gc:
        // - nothing is running -> we can safely ignore the stack
        iteration++;
        generation++;
        long t0 = System.nanoTime();
        traverseStaticInstances();
        markJSReferences();
        long t1 = System.nanoTime();
        GCX.hasGaps = findLargestGaps(largestGaps, null);
        long t2 = System.nanoTime();
        log("GC-Nanos:", (int) (t1 - t0), (int) (t2 - t1), generation);
    }

    @NoThrow // primary thread
    @Alias(names = "parallelGC0")
    public static void parallelGC0() {
        iteration++;
        generation++;
        long t0 = System.nanoTime();
        traverseStaticInstances();
        markJSReferences();
        // if we want to use gaps while collecting gaps,
        //  we need to jump over the gaps-in-use
        long t1 = System.nanoTime();
        log("GC-Traversal:", (int) (t1 - t0));
    }

    @NoThrow // runs on secondary thread; must not make allocations
    @Alias(names = "parallelGC1")
    public static void parallelGC1() {
        long t1 = System.nanoTime();
        hasGapsTmp = findLargestGaps(largestGapsTmp, largestGaps);
        long t2 = System.nanoTime();
        log("GC-ParallelGaps:", (int) (t2 - t1), generation);
    }

    @NoThrow // primary thread
    @Alias(names = "parallelGC2")
    public static void parallelGC2() {
        int[] tmp = largestGapsTmp;
        largestGapsTmp = largestGaps;
        largestGaps = tmp;
        GCX.hasGaps = hasGapsTmp;
    }

    @NoThrow
    public static int findGap(int size) {
        int ptr = getAddr(largestGaps);
        final int numGaps = arrayLength(ptr);
        ptr += arrayOverhead;
        final int endPtr = ptr + (numGaps << 2);
        final int sizeWithoutHelper = size - arrayOverhead;
        while (ptr < endPtr) {
            int array = read32(ptr);
            if (array != 0) {
                int available = arrayLength(array);
                // three cases:
                //  a) we fit perfectly
                //  b) we fit, and let stuff remain
                //  c) we don't fit
                if (available == sizeWithoutHelper) {
                    findGapReplace(ptr, size);
                    return array; // the new instance is placed where the array was
                } else if (unsignedGreaterThanEqual(available, size)) {
                    return findGapFitIntoSpace(array, size, available + arrayOverhead);
                }
            }
            ptr += 4;
        }
        return 0; // no gap was found
    }

    @NoThrow
    private static void findGapReplace(int ptr, int size) {
        // if (printCtr++ < 0) log("GC replacing", size);
        write32(ptr, 0); // nothing is remaining -> set entry to null
        freeMemory -= size;
    }

    @NoThrow
    private static int findGapFitIntoSpace(final int arrayPtr, final int size, final int available) {
        final int sizeWithHelper = size + arrayOverhead;
        // if (printCtr++ < 0) log("GC shrinking", ptr, available, size);
        // shrink array
        write32(arrayPtr + objectOverhead, available - sizeWithHelper);
        freeMemory -= size;
        // calculate new pointer, and we're done :D
        return arrayPtr + available - size; // the new object is placed at the end
    }

    @NoThrow
    private static int getInstanceSize(int instance, int clazz) {
        int size;
        if (unsignedLessThan(clazz - 1, 9)) { // clazz > 0 && clazz < 10
            // handle arrays by size
            size = arrayOverhead + (arrayLength(instance) << getTypeShift(clazz));
        } else {
            // handle class instance
            int sizesPtr = getAddr(classSizes) + arrayOverhead;
            size = read32(sizesPtr + (clazz << 2));
        }
        return size;
    }

    @NoThrow
    @Alias(names = "isParallelGC")
    @JavaScript(code = "return false;")
    private static native boolean isParallelGC();

    /**
     * iterate over all memory
     * - if instance is unused, free it = create byte[]
     * - if instance is monitored using WeakRef, mark it as still available
     * - find the largest unused memory spaces for reusing old space
     */
    @NoThrow
    private static boolean findLargestGaps(int[] largestGaps, int[] gapsInUse) {

        // - find the largest gaps to reuse the memory there
        // for that, iterate over all allocated memory
        int instance = getAllocationStart();
        final int iter = iteration;
        final int endPtr = getNextPtr();
        boolean wasUsed = true;
        int gapStart = instance;
        Arrays.fill(largestGaps, 0);
        final int nc = numClasses();

        // log("Scanning", instance, endPtr, iter);

        if (classSizes.length != nc) {
            throwJs("ClassSizes.length != nc");
        }

        int gapCounter = 0;

        smallestSize = arrayOverhead << 1;
        smallestSizeIndex = 0;

        freeMemory = 0;

        boolean isParallelGC = isParallelGC();
        while (unsignedLessThan(instance, endPtr)) {

            // when we find a not-used section, replace it with byte[] for faster future traversal (if possible)
            int clazz = readClass(instance);
            int size = getInstanceSize(instance, clazz);
            size = adjustCallocSize(size);

            boolean isUsed = read8(instance + GCOffset) == iter;
            if (!isUsed) {
                // isUsed needs to be re-set, if we have parallelGC, because the instance might be around again
                // WeakRef-references need to be deleted in ordinary and parallel GC
                isUsed = wasUsedByWeakRef(instance) & isParallelGC;
            }

            if (isUsed != wasUsed) {
                if (isUsed) {
                    gapCounter++;
                    // handle empty space in between
                    // what if this is > 2 GiB? will be an illegal array length ->
                    // nobody will access it anyway -> doesn't matter, as long as we compute everything with unsigned ints :)
                    int available = instance - gapStart;
                    freeMemory += available;
                    if (unsignedGreaterThan(available, smallestSize)) {
                        // log("Handling", instance, clazz);
                        // log("Found gap :)", available);
                        if (!contains(gapStart, gapsInUse)) {
                            handleGap(available, gapStart, largestGaps);
                        } // else skip gap for now
                    } // else gap too small for us to care for
                    // retry to find the error
                } else {
                    gapStart = instance;
                }
                wasUsed = isUsed;
            }

            instance += size;
        }

        // if the last object is not being used, we can reduce the nextPtr() by its size
        if (!wasUsed && getNextPtr() == endPtr) {
            setNextPtr(gapStart);
            // log("Reduced max memory to, by", gapStart, endPtr - gapStart);
        }

        // log("Done Scanning :), instances:", numInstances);
        // log("Gaps:", gapCounter);
        return gapCounter > 0;
    }

    @NoThrow
    private static boolean contains(int gapStart, int[] gapsInUse) {
        if (gapsInUse == null) return false;
        int ptr = getAddr(gapsInUse);
        int length = arrayLength(ptr); // will be 16
        int endPtr = ptr + arrayOverhead + (length << 2);
        while (ptr < endPtr) {
            if (read32(ptr) == gapStart) {
                return true;
            }
            ptr += 4;
        }
        return false;
    }

    @NoThrow
    private static boolean wasUsedByWeakRef(int instance) {
        lockMallocMutex();
        WeakRef weakRef = WeakRef.weakRefInstances.remove(instance);
        boolean wasReferenced = weakRef != null;
        unlockMallocMutex();
        while (weakRef != null) {
            weakRef.address = 0;
            WeakRef nextRef = weakRef.next;
            weakRef.next = null; // doesn't matter anymore, so unlink them for GC
            weakRef = nextRef;
        }
        return wasReferenced;
    }

    @NoThrow
    @Alias(names = "lockMallocMutex")
    public static native void lockMallocMutex();

    @NoThrow
    @Alias(names = "unlockMallocMutex")
    public static native void unlockMallocMutex();

    private static int smallestSize, smallestSizeIndex;

    @NoThrow
    private static void handleGap(int available, int gapStart, int[] largestGaps) {

        // first step: replace with byte array, so that next time we can skip over it faster
        // clear(gapStart, gapStart + available);

        lockMallocMutex();
        write32(gapStart, 5); // byte array, generation 0
        write32(gapStart + objectOverhead, available - arrayOverhead); // length
        unlockMallocMutex();

        int smallestSizeIndex = GC.smallestSizeIndex;
        largestGaps[smallestSizeIndex] = gapStart;

        // reevaluate smallest size
        int smallestSize = available;
        for (int i = 0; i < 16; i++) {
            int array = largestGaps[i];
            int prev = array == 0 ? 0 : arrayLength(array);
            if (unsignedLessThan(prev, smallestSize)) {
                smallestSize = prev;
                smallestSizeIndex = i;
            }
        }

        GC.smallestSize = smallestSize;
        GC.smallestSizeIndex = smallestSizeIndex;

    }

    @NoThrow
    private static void traverseStaticInstances() {
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
            int statePtr = instance + GCOffset;
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
            classSizes2[i] = JVM32.getInstanceSize(i);
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
