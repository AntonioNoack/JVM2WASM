package jvm;

import annotations.Alias;
import annotations.Export;
import annotations.JavaScript;
import annotations.NoThrow;
import jvm.custom.WeakRef;

import static jvm.ArrayAccessUnchecked.arrayLength;
import static jvm.GCGapFinder.findLargestGaps;
import static jvm.GCGapFinder.insertGapMaybe;
import static jvm.GCTraversal.traverseStaticInstances;
import static jvm.JVM32.*;
import static jvm.JVMShared.*;
import static jvm.JVMValues.failedToAllocateMemory;
import static jvm.JVMValues.reachedMemoryLimit;
import static jvm.NativeLog.log;

public class GarbageCollector {

    public static int freeMemory = 0;

    public static byte iteration = 0;

    public static final int GC_OFFSET = 3;
    public static final int BYTE_ARRAY_CLASS = 5;

    public static int[] largestGaps = new int[16];// 16 pointers
    static int[] largestGapsTmp = new int[16];

    public static int generation = 1;

    @NoThrow
    @JavaScript(code = "markJSReferences()")
    private static native void markJSReferences();

    @Export
    @NoThrow
    @Alias(names = "gc")
    public static void invokeGC() {
        // run gc:
        // - nothing is running -> we can safely ignore the stack
        nextGeneration();
        long t0 = System.nanoTime();
        traverseAliveInstances();
        long t1 = System.nanoTime();
        findLargestGaps(largestGaps, null);
        GarbageCollectorFlags.hasGaps = hasGaps();
        long t2 = System.nanoTime();
        log("GC-Nanos:", (int) (t1 - t0), (int) (t2 - t1), generation);
    }

    @Export
    @NoThrow
    @Alias(names = "concurrentGC0")
    public static void concurrentGC0() {
        nextGeneration();
        long t0 = System.nanoTime();
        traverseAliveInstances();
        long t1 = System.nanoTime();
        GCGapFinder.findLargestGapsInit(largestGaps);
        long t2 = System.nanoTime();
        log("GC-Nanos:", (int) (t1 - t0), (int) (t2 - t1), generation);
    }

    @Export
    @NoThrow
    @Alias(names = "concurrentGC1")
    public static boolean concurrentGC1() {
        boolean done = GCGapFinder.findLargestGapsStep(largestGaps, null);
        if (done) {
            GarbageCollectorFlags.hasGaps = hasGaps();
        }
        return done;
    }

    @NoThrow // primary thread
    @Alias(names = "parallelGC0")
    public static void parallelGC0() {
        nextGeneration();
        long t0 = System.nanoTime();
        traverseAliveInstances();
        // if we want to use gaps while collecting gaps,
        //  we need to jump over the gaps-in-use
        long t1 = System.nanoTime();
        log("GC-Traversal:", (int) (t1 - t0));
    }

    @NoThrow // runs on secondary thread; must not make allocations
    @Alias(names = "parallelGC1")
    public static void parallelGC1() {
        long t1 = System.nanoTime();
        findLargestGaps(largestGapsTmp, largestGaps);
        long t2 = System.nanoTime();
        log("GC-ParallelGaps:", (int) (t2 - t1), generation);
    }

    @NoThrow
    private static void traverseAliveInstances() {
        // this is called from JS/C++-main loop, so the stack is empty and can be skipped
        traverseStaticInstances();
        markJSReferences();
    }

    @NoThrow
    private static void nextGeneration() {
        iteration++;
        generation++;
    }

    @NoThrow // primary thread
    @Alias(names = "parallelGC2")
    public static void parallelGC2() {
        // mergeGaps();
        swapGaps();
        GarbageCollectorFlags.hasGaps = hasGaps();
    }

    @NoThrow
    private static void swapGaps() {
        int[] tmp = largestGapsTmp;
        largestGapsTmp = largestGaps;
        largestGaps = tmp;
    }

    @NoThrow
    private static void mergeGaps() {
        for (int gap : largestGapsTmp) {
            if (instanceOf(ptrTo(gap), BYTE_ARRAY_CLASS)) {
                int available = arrayLength(gap) + arrayOverhead;
                insertGapMaybe(gap, available, largestGaps);
            }
        }
    }

    @NoThrow
    private static boolean hasGaps() {
        for (int gap : largestGaps) {
            if (instanceOf(ptrTo(gap), BYTE_ARRAY_CLASS)) {
                return true;
            }
        }
        return false;
    }

    public static int allocateNewSpace(int size) {
        lockMallocMutex();
        int ptr = allocateNewSpace0(size);
        unlockMallocMutex();
        return ptr;
    }

    private static int allocateNewSpace0(int size) {
        int ptr = getNextPtr();
        // log("allocated", ptr, size);

        // clean memory
        int endPtr = ptr + size;

        // if ptr + size has a memory overflow over 0, throw OOM
        if (unsignedLessThan(endPtr, ptr)) {
            log("Memory overflow, size is probably too large", ptr, size);
            throw reachedMemoryLimit;
        }

        // check if we have enough size
        int allocatedSize = getAllocatedSize() - (b2i(!criticalAlloc) << 16);
        if (unsignedLessThan(allocatedSize, endPtr)) {

            // if not, call grow()
            // how much do we want to grow?
            int allocatedPages = allocatedSize >>> 16;
            // once this limit has been hit, only throw once
            int maxNumPages = 65536;// 128 ~ 16 MB; 4 GB = 65536;
            int remainingPages = maxNumPages - allocatedPages;
            int amountToGrow = allocatedPages >> 1;
            int minPagesToGrow = (endPtr >>> 16) + 1 - allocatedPages;
            if (amountToGrow > remainingPages) amountToGrow = remainingPages;
            // should be caught by ptr<0 && endPtr > 0
            if (minPagesToGrow > remainingPages) {
                log("Cannot grow enough", minPagesToGrow, remainingPages);
                throw reachedMemoryLimit;
            }
            if (amountToGrow < minPagesToGrow) amountToGrow = minPagesToGrow;
            if (!grow(amountToGrow)) {
                // if grow() fails, throw OOM error
                log("grow() failed", amountToGrow);
                throw failedToAllocateMemory;
            } else {
                // log("Grew", minPagesToGrow, amountToGrow);
                if (reachedMemoryLimit == null) log("Mmh, awkward");
            }
        }

        // prevent the very first allocations from overlapping
        ptr = getNextPtr();
        setNextPtr(ptr + size);
        return ptr;
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
    @Alias(names = "isParallelGC")
    @JavaScript(code = "return false;")
    static native boolean isParallelGC();

    @NoThrow
    static boolean unregisterWeakRef(int instance) {
        lockMallocMutex();
        @SuppressWarnings("rawtypes")
        WeakRef weakRef = WeakRef.weakRefInstances.remove(instance);
        boolean wasReferenced = weakRef != null;
        unlockMallocMutex();
        while (weakRef != null) {
            weakRef.address = 0;
            @SuppressWarnings("rawtypes")
            WeakRef nextRef = weakRef.next;
            weakRef.next = null; // doesn't matter anymore, so unlink them for GC
            weakRef = nextRef;
        }
        return wasReferenced;
    }

    @NoThrow
    static boolean contains(int gapStart, int[] gapsInUse) {
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
    @Alias(names = "lockMallocMutex")
    @JavaScript(code = "")
    public static native void lockMallocMutex();

    @NoThrow
    @Alias(names = "unlockMallocMutex")
    @JavaScript(code = "")
    public static native void unlockMallocMutex();

}
