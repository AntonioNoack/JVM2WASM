package jvm;

import annotations.NoThrow;
import jvm.custom.WeakRef;
import me.anno.utils.structures.arrays.IntArrayList;

import static jvm.ArrayAccessUnchecked.arrayLength;
import static jvm.GC.*;
import static jvm.GCTraversal.classSizes;
import static jvm.JVM32.*;
import static jvm.JavaLang.getAddr;

/**
 * "Sweep" of mark-and-sweep
 */
public class GCGapFinder {

    private static final IntArrayList sortedWeakRefInstances = new IntArrayList(64, null);

    private static int smallestSize, smallestSizeIndex;

    @NoThrow
    private static void fillZeros(int[] largestGaps) {
        // must not use Arrays.fill, because that uses the stack-ptr, and we might run async
        for (int i = 0; i < 16; i++) {
            largestGaps[i] = 0;
        }
    }


    private static int[] nextWeakRef;
    private static int nextWeakRefIndex;
    private static int nextWeakRefInstance;


    private static boolean handleWeakRefs(int instance) {

        // isUsed needs to be re-set, if we have parallelGC, because the instance might be around again
        // WeakRef-references need to be deleted in ordinary and parallel GC

        // jump to next entry; zero iterations in most cases
        while (unsignedLessThan(instance, nextWeakRefInstance)) {
            nextWeakRefInstance = nextWeakRef[nextWeakRefIndex++];
        }

        if (instance == nextWeakRefInstance) {
            nextWeakRefInstance = nextWeakRef[nextWeakRefIndex++];
            return unregisterWeakRef(instance) & isParallelGC();
        } else return false;
    }

    private static void prepareWeakRefs() {
        IntArrayList nextWeakRef0 = sortedWeakRefInstances;
        lockMallocMutex();// is it ok to set this once for parallel GC? I think so
        WeakRef.weakRefInstances.collectKeys(nextWeakRef0);
        unlockMallocMutex();

        int[] nwr = nextWeakRef = nextWeakRef0.getValues();
        QuickSort.quickSort(nwr, 0, nextWeakRef0.getSize() - 1);
        nextWeakRef0.addUnsafe(-1);

        nextWeakRefIndex = 1;
        nextWeakRefInstance = nextWeakRef0.get(0);
    }

    private static void checkStatistics() {
        if (classSizes.length != numClasses()) {
            throwJs("ClassSizes.length != nc");
        }
    }

    private static int currPtr, endPtr, gapStart;
    private static boolean wasUsed;

    public static void findLargestGapsInit(int[] largestGaps) {
        fillZeros(largestGaps);

        // log("Scanning", instance, endPtr, iter);
        checkStatistics();

        smallestSize = arrayOverhead << 1;
        smallestSizeIndex = 0;
        freeMemory = 0;
        currPtr = getAllocationStart();
        endPtr = getNextPtr();
        gapStart = currPtr;
        wasUsed = true;

        prepareWeakRefs();
    }

    public static boolean findLargestGapsStep(int[] largestGaps, int[] gapsInUse) {
        int remainingBudget = 20_000;

        // - find the largest gaps to reuse the memory there
        // for that, iterate over all allocated memory
        int instance = currPtr;
        int gapStart = GCGapFinder.gapStart;
        final int iteration = GC.iteration;
        final int endPtr = GCGapFinder.endPtr;
        boolean wasUsed = GCGapFinder.wasUsed;

        int freedMemory = 0;
        while (unsignedLessThan(instance, endPtr)) {

            // when we find a not-used section, replace it with byte[] for faster future traversal (if possible)
            int size = getInstanceSizeI(instance);

            boolean isUsed = read8(instance + GC_OFFSET) == iteration;
            if (!isUsed) {
                isUsed = handleWeakRefs(instance);
            }

            if (!isUsed && contains(instance, gapsInUse)) {
                // already considered as a gap, and should not be written;
                // to not throw them away for one GC iteration, we merge them later
                isUsed = true;
            }

            if (isUsed != wasUsed) {
                if (isUsed) {
                    int available = instance - gapStart;
                    freedMemory += available;
                    handleGap(gapStart, available, largestGaps);
                } else {
                    gapStart = instance;
                }
                wasUsed = isUsed;
            }

            instance += size;

            if (remainingBudget-- == 0) {
                // save temporary state
                GCGapFinder.currPtr = instance;
                GC.freeMemory += freedMemory;
                GCGapFinder.gapStart = gapStart;
                GCGapFinder.wasUsed = wasUsed;
                return false; // not yet done
            }
        }

        // if the last object is not being used, we can reduce the nextPtr() by its size
        if (!wasUsed) {
            finishFindingGaps(endPtr, gapStart);
        }
        GC.freeMemory += freedMemory;

        // log("Done Scanning :), instances:", numInstances);
        // log("Gaps:", gapCounter);
        // we're finally done :)
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
        write32(gapStart, BYTE_ARRAY_CLASS); // byte array, generation 0
        write32(gapStart + objectOverhead, available - arrayOverhead); // length
    }

    @NoThrow
    static void handleGap(int gapStart, int available, int[] largestGaps) {
        if (unsignedGreaterThanEqual(available, arrayOverhead)) {
            // first step: replace with byte array, so that next time we can skip over it faster
            replaceWithByteArray(gapStart, available);
        }
        insertGapMaybe(gapStart, available, largestGaps);
    }

    @NoThrow
    static void insertGapMaybe(int gapStart, int available, int[] largestGaps) {

        if (unsignedLessThanEqual(available, smallestSize)) {
            // gap too small for us to care for
            return;
        }

        int smallestSizeIndex = GCGapFinder.smallestSizeIndex;
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

        GCGapFinder.smallestSize = smallestSize;
        GCGapFinder.smallestSizeIndex = smallestSizeIndex;

    }


    @NoThrow
    static int getInstanceSizeI(int instance) {
        return getInstanceSizeIC(instance, readClass(instance));
    }

    @NoThrow
    private static int getInstanceSizeIC(int instance, int classIndex) {
        int size;
        if (unsignedLessThan(classIndex - 1, 9)) { // clazz > 0 && clazz < 10
            // handle arrays by size
            size = arrayOverhead + (arrayLength(instance) << getTypeShift(classIndex));
        } else {
            // handle class instance
            int sizesPtr = getAddr(classSizes) + arrayOverhead;
            size = read32(sizesPtr + (classIndex << 2));
        }
        size = adjustCallocSize(size);
        return size;
    }

}
