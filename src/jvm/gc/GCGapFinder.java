package jvm.gc;

import annotations.NoThrow;
import annotations.UnsafePointerField;
import jvm.Pointer;
import jvm.custom.WeakRef;

import java.util.ArrayList;

import static jvm.ArrayAccessUnchecked.arrayLength;
import static jvm.JVM32.*;
import static jvm.JVMShared.unsignedGreaterThanEqual;
import static jvm.JVMShared.unsignedLessThan;
import static jvm.JVMShared.*;
import static jvm.NativeLog.log;
import static jvm.Pointer.unsignedLessThan;
import static jvm.Pointer.*;
import static jvm.ThrowJS.throwJs;
import static jvm.gc.GCTraversal.instanceFieldOffsets;
import static jvm.gc.GarbageCollector.*;
import static utils.StaticClassIndices.FIRST_ARRAY;
import static utils.StaticClassIndices.LAST_ARRAY;

/**
 * "Sweep" of mark-and-sweep
 */
public class GCGapFinder {

    private static final ArrayList<Object> nextWeakRef = new ArrayList<>(64);
    private static int nextWeakRefIndex;
    private static Object nextWeakRefInstance;

    private static int smallestGapSize, smallestGapSizeIndex;

    private static boolean handleWeakRefs(Object instance) {

        // log("handleWeakRefs");

        // isUsed needs to be re-set, if we have parallelGC, because the instance might be around again
        // WeakRef-references need to be deleted in ordinary and parallel GC

        // jump to next entry; zero iterations in most cases
        while (nextWeakRefInstance != null &&
                unsignedLessThan(castToPtr(instance), castToPtr(nextWeakRefInstance))) {
            nextWeakRefInstance = nextWeakRef.get(nextWeakRefIndex++);
        }

        if (instance == nextWeakRefInstance) {
            nextWeakRefInstance = nextWeakRef.get(nextWeakRefIndex++);
            return unregisterWeakRef(instance) & isParallelGC();
        } else return false;
    }

    private static void prepareWeakRefs() {
        lockMallocMutex();// is it ok to set this once for parallel GC? I think so
        WeakRef.weakRefInstances.collectKeys(nextWeakRef);
        unlockMallocMutex();

        nextWeakRef.sort(AddressComparator.INSTANCE);
        nextWeakRef.add(null);

        nextWeakRefIndex = 1;
        nextWeakRefInstance = nextWeakRef.get(0);
    }

    private static void checkStatistics() {
        if (instanceFieldOffsets.length != numClasses()) {
            throwJs("instanceFieldOffsets.length != nc", instanceFieldOffsets.length, numClasses());
        }
    }

    @UnsafePointerField
    private static Pointer currPtr, endPtr, gapStart;

    private static boolean wasUsed;

    public static void findLargestGapsInit(byte[][] largestGaps) {
        // log("Finding largest gaps");
        // must not use Arrays.fill, because that uses the stack-ptr, and we might run async
        for (int i = 0, l = largestGaps.length; i < l; i++) {
            largestGaps[i] = null;
        }

        // log("Scanning", instance, endPtr, iter);
        checkStatistics();

        smallestGapSize = arrayOverhead << 1;
        smallestGapSizeIndex = 0;
        freeMemory = 0;
        currPtr = getAllocationStart();
        endPtr = getNextPtr();
        gapStart = currPtr;
        wasUsed = true;

        // log("Preparing WeakRefs");
        prepareWeakRefs();
    }

    public static boolean findLargestGapsStep(byte[][] largestGaps, byte[][] gapsInUse) {
        int remainingBudget = 20_000;

        // - find the largest gaps to reuse the memory there
        // for that, iterate over all allocated memory
        Pointer instancePtr = currPtr;
        Pointer gapStart = GCGapFinder.gapStart;
        final int iteration = GarbageCollector.iteration;
        final Pointer endPtr = GCGapFinder.endPtr;
        boolean wasUsed = GCGapFinder.wasUsed;

        // log("[Gaps]", instancePtr);

        long freedMemory = 0;
        while (unsignedLessThan(instancePtr, endPtr)) {

            // when we find a not-used section, replace it with byte[] for faster future traversal (if possible)
            int classId = readClassId(instancePtr);
            Pointer size = getInstanceSize(instancePtr, classId);
            // log("Gaps", instancePtr, classId, size);
            validateClassId(classId);

            boolean isUsed = readI8AtOffset(instancePtr, GC_OFFSET) == iteration;
            if (!isUsed) {
                isUsed = handleWeakRefs(instancePtr);
            }

            if (!isUsed && contains(instancePtr, gapsInUse)) {
                // already considered as a gap, and should not be written;
                // to not throw them away for one GC iteration, we merge them later
                isUsed = true;
            }

            if (isUsed != wasUsed) {
                if (isUsed) {
                    long available = diff(instancePtr, gapStart);
                    freedMemory += available;
                    if (available > Integer.MAX_VALUE) {
                        // todo if gap.length > 2B, create multiple arrays
                        throwJs("Gap too large to handle");
                    }
                    handleGap(gapStart, (int) available, largestGaps);
                } else {
                    gapStart = instancePtr;
                }
                wasUsed = isUsed;
            }

            instancePtr = add(instancePtr, size);

            if (remainingBudget-- == 0) {
                // save temporary state
                GCGapFinder.currPtr = instancePtr;
                GarbageCollector.freeMemory += freedMemory;
                GCGapFinder.gapStart = gapStart;
                GCGapFinder.wasUsed = wasUsed;
                return false; // not yet done
            }
        }

        // if the last object is not being used, we can reduce the nextPtr() by its size
        if (!wasUsed) {
            finishFindingGaps(endPtr, gapStart);
        }
        GarbageCollector.freeMemory += freedMemory;
        GCGapFinder.currPtr = null;
        GCGapFinder.endPtr = null;
        GCGapFinder.gapStart = null;

        // log("Done Scanning :), instances:", numInstances);
        // log("Gaps:", gapCounter);
        // we're finally done :)
        nextWeakRef.clear(); // to prevent them from being GCed
        return true;
    }

    /**
     * iterate over all memory
     * - if instance is unused, free it = create byte[]
     * - if instance is monitored using WeakRef, mark it as still available
     * - find the largest unused memory spaces for reusing old space
     */
    @NoThrow
    public static void findLargestGaps(byte[][] largestGaps, byte[][] gapsInUse) {
        findLargestGapsInit(largestGaps);
        while (true) {
            boolean done = findLargestGapsStep(largestGaps, gapsInUse);
            if (done) break;
        }
    }

    @NoThrow
    private static void finishFindingGaps(Pointer endPtr, Pointer gapStart) {
        lockMallocMutex();
        if (getNextPtr() == endPtr) {
            setNextPtr(gapStart);
            log("Reduced max memory to, by", gapStart, sub(endPtr, gapStart));
        }
        unlockMallocMutex();
    }

    @NoThrow
    static void handleGap(Pointer gapStart, int available, byte[][] largestGaps) {
        // to do if a gap was > 2B elements, we'd have an issue...
        if (unsignedGreaterThanEqual(available, arrayOverhead)) { // should always be true
            // first step: replace with byte array, so that next time we can skip over it faster
            // log("Replacing Gap", gapStart, available);
            write32(gapStart, BYTE_ARRAY_CLASS); // byte array, generation 0
            writeI32AtOffset(gapStart, objectOverhead, available - arrayOverhead); // length
            insertGapMaybe(unsafeCast(gapStart), available, largestGaps);
        }
    }

    @NoThrow
    static void insertGapMaybe(byte[] gapStart, int available, byte[][] largestGaps) {

        if (unsignedLessThanEqual(available, smallestGapSize)) {
            // gap too small for us to care for
            return;
        }

        int smallestSizeIndex = GCGapFinder.smallestGapSizeIndex;
        largestGaps[smallestSizeIndex] = gapStart;

        // reevaluate smallest size
        int smallestSize = available;
        for (int i = 0; i < 16; i++) {
            byte[] array = largestGaps[i];
            int size = array == null ? 0 : array.length;
            if (unsignedLessThan(size, smallestSize)) {
                smallestSize = size;
                smallestSizeIndex = i;
            }
        }

        GCGapFinder.smallestGapSize = smallestSize;
        GCGapFinder.smallestGapSizeIndex = smallestSizeIndex;

    }

    @NoThrow
    public static Pointer getInstanceSize(Pointer addr) {
        int classId = readClassId(addr);
        if (classId < 0 || classId >= numClasses()) {
            log("Invalid class ID", addr, classId);
            throwJs();
        }
        return getInstanceSize(addr, classId);
    }

    @NoThrow
    public static Pointer getInstanceSize(Pointer addr, int classId) {
        if (classId >= FIRST_ARRAY && classId <= LAST_ARRAY) {
            // handle arrays by size
            int length = arrayLength(addr);
            return getArraySizeInBytes(length, classId);
        } else {
            // handle class instance
            return ptrTo(getInstanceSizeNonArray(classId));
        }
    }

}
