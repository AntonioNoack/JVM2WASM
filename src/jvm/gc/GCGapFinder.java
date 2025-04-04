package jvm.gc;

import annotations.NoThrow;
import annotations.WASM;
import jvm.custom.WeakRef;

import java.util.ArrayList;

import static jvm.ArrayAccessUnchecked.arrayLength;
import static jvm.gc.GCTraversal.instanceFieldOffsets;
import static jvm.gc.GarbageCollector.*;
import static jvm.JVM32.*;
import static jvm.JVMShared.*;
import static jvm.NativeLog.log;
import static jvm.ThrowJS.throwJs;
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

    @NoThrow
    private static void fillZeros(int[] largestGaps) {
        // must not use Arrays.fill, because that uses the stack-ptr, and we might run async
        for (int i = 0; i < 16; i++) {
            largestGaps[i] = 0;
        }
    }

    private static boolean handleWeakRefs(Object instance) {

        // log("handleWeakRefs");

        // isUsed needs to be re-set, if we have parallelGC, because the instance might be around again
        // WeakRef-references need to be deleted in ordinary and parallel GC

        // jump to next entry; zero iterations in most cases
        while (nextWeakRefInstance != null && unsignedLessThanI(instance, nextWeakRefInstance)) {
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

    private static int currPtr, endPtr, gapStart;
    private static boolean wasUsed;

    public static void findLargestGapsInit(int[] largestGaps) {
        // log("Finding largest gaps");
        fillZeros(largestGaps);

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

    public static boolean findLargestGapsStep(int[] largestGaps, int[] gapsInUse) {
        int remainingBudget = 20_000;

        // - find the largest gaps to reuse the memory there
        // for that, iterate over all allocated memory
        int instancePtr = currPtr;
        int gapStart = GCGapFinder.gapStart;
        final int iteration = GarbageCollector.iteration;
        final int endPtr = GCGapFinder.endPtr;
        boolean wasUsed = GCGapFinder.wasUsed;

        // log("[Gaps]", instancePtr);

        int freedMemory = 0;
        while (unsignedLessThan(instancePtr, endPtr)) {

            // when we find a not-used section, replace it with byte[] for faster future traversal (if possible)
            int classId = readClassIdImpl(instancePtr);
            int size = getInstanceSize(instancePtr, classId);
            // log("Gaps", instancePtr, classId, size);
            validateClassId(classId);

            boolean isUsed = read8(instancePtr + GC_OFFSET) == iteration;
            if (!isUsed) {
                isUsed = handleWeakRefs(ptrTo(instancePtr));
            }

            if (!isUsed && contains(instancePtr, gapsInUse)) {
                // already considered as a gap, and should not be written;
                // to not throw them away for one GC iteration, we merge them later
                isUsed = true;
            }

            if (isUsed != wasUsed) {
                if (isUsed) {
                    int available = instancePtr - gapStart;
                    freedMemory += available;
                    handleGap(gapStart, available, largestGaps);
                } else {
                    gapStart = instancePtr;
                }
                wasUsed = isUsed;
            }

            instancePtr += size;

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
    public static void findLargestGaps(int[] largestGaps, int[] gapsInUse) {
        findLargestGapsInit(largestGaps);
        while (true) {
            boolean done = findLargestGapsStep(largestGaps, gapsInUse);
            if (done) break;
        }
    }

    @NoThrow
    private static void finishFindingGaps(int endPtr, int gapStart) {
        lockMallocMutex();
        if (getNextPtr() == endPtr) {
            setNextPtr(gapStart);
            log("Reduced max memory to, by", gapStart, endPtr - gapStart);
        }
        unlockMallocMutex();
    }

    @NoThrow
    private static void replaceWithByteArray(int gapStart, int available) {
        // log("Replacing Gap", gapStart, available);
        write32(gapStart, BYTE_ARRAY_CLASS); // byte array, generation 0
        write32(gapStart + objectOverhead, available - arrayOverhead); // length
    }

    @NoThrow
    static void handleGap(int gapStart, int available, int[] largestGaps) {
        // to do if a gap was > 2B elements, we'd have an issue...
        if (unsignedGreaterThanEqual(available, arrayOverhead)) { // should always be true
            // first step: replace with byte array, so that next time we can skip over it faster
            replaceWithByteArray(gapStart, available);
            insertGapMaybe(gapStart, available, largestGaps);
        }
    }

    @NoThrow
    static void insertGapMaybe(int gapStart, int available, int[] largestGaps) {

        if (unsignedLessThanEqual(available, smallestGapSize)) {
            // gap too small for us to care for
            return;
        }

        int smallestSizeIndex = GCGapFinder.smallestGapSizeIndex;
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

        GCGapFinder.smallestGapSize = smallestSize;
        GCGapFinder.smallestGapSizeIndex = smallestSizeIndex;

    }

    @NoThrow
    public static int getInstanceSize(int addr) {
        int classId = readClassIdImpl(addr);
        if (classId < 0 || classId >= numClasses()) {
            log("Invalid class ID", addr, classId);
            throwJs();
        }
        return getInstanceSize(addr, classId);
    }

    @NoThrow
    public static int getInstanceSize(int addr, int classId) {
        if (classId >= FIRST_ARRAY && classId <= LAST_ARRAY) {
            // handle arrays by size
            int length = arrayLength(addr);
            return getArraySizeInBytes(length, classId);
        } else {
            // handle class instance
            return getInstanceSizeNonArray(classId);
        }
    }

}
