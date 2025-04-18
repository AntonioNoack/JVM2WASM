package jvm.gc;

import annotations.*;
import jvm.Pointer;
import jvm.custom.WeakRef;

import static jvm.JVMFlags.is32Bits;
import static jvm.JVMShared.unsignedGreaterThanEqual;
import static jvm.JVMShared.*;
import static jvm.JVMValues.failedToAllocateMemory;
import static jvm.JVMValues.reachedMemoryLimit;
import static jvm.NativeLog.log;
import static jvm.Pointer.unsignedLessThan;
import static jvm.Pointer.*;
import static jvm.gc.GCGapFinder.findLargestGaps;
import static jvm.gc.GCTraversal.traverseStaticInstances;

public class GarbageCollector {

    public static long freeMemory = 0;

    public static byte iteration = 0;
    public static int generation = 1;

    public static final int GC_OFFSET = 3;
    public static final int BYTE_ARRAY_CLASS = 5;

    public static byte[][] largestGaps = new byte[16][];// 16 pointers
    static byte[][] largestGapsTmp = new byte[16][];

    @NoThrow
    @JavaScript(code = "markJSReferences()")
    private static native void markJSReferences();

    @Export
    @NoThrow
    @Alias(names = "gc")
    @PureJavaScript(code = "")
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
        log("GC-Nanos:", (t1 - t0), (t2 - t1), generation);
    }

    @Export
    @NoThrow
    @Alias(names = "concurrentGC0")
    @PureJavaScript(code = "")
    public static void concurrentGC0() {
        nextGeneration();
        long t0 = System.nanoTime();
        traverseAliveInstances();
        long t1 = System.nanoTime();
        GCGapFinder.findLargestGapsInit(largestGaps);
        long t2 = System.nanoTime();
        log("GC-Nanos:", (t1 - t0), (t2 - t1), generation);
    }

    @Export
    @NoThrow
    @Alias(names = "concurrentGC1")
    @PureJavaScript(code = "")
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
        byte[][] tmp = largestGapsTmp;
        largestGapsTmp = largestGaps;
        largestGaps = tmp;
    }

    @NoThrow
    private static boolean hasGaps() {
        for (byte[] gap : largestGaps) {
            if (gap != null) {
                return true;
            }
        }
        return false;
    }

    public static Pointer allocateNewSpace(Pointer size) {
        lockMallocMutex();
        Pointer ptr = allocateNewSpace0(size);
        unlockMallocMutex();
        return ptr;
    }

    private static Pointer allocateNewSpace0(Pointer size) {
        Pointer ptr = getNextPtr();
        // log("allocated", ptr, size);

        // clean memory
        Pointer endPtr = add(ptr, size);

        // if ptr + size has a memory overflow over 0, throw OOM
        if (unsignedLessThan(endPtr, ptr)) {
            log("Memory overflow, size is probably too large", ptr, size);
            throw reachedMemoryLimit;
        }

        // check if we have enough size
        Pointer allocatedSize = sub(getAllocatedSize(), b2i(!criticalAlloc) << 16);
        if (unsignedLessThan(allocatedSize, endPtr)) {

            // if not, call grow()
            // how much do we want to grow?
            long allocatedPages = getAddrS(allocatedSize) >>> 16;
            // once this limit has been hit, only throw once
            long maxNumPages = is32Bits ? 65536 : Integer.MAX_VALUE;// 128 ~ 16 MB; 4 GB = 65536;
            long remainingPages = maxNumPages - allocatedPages;
            long amountToGrow = allocatedPages >> 1;
            long minPagesToGrow = (getAddrS(endPtr) >>> 16) + 1 - allocatedPages;
            if (amountToGrow > remainingPages) amountToGrow = remainingPages;
            // should be caught by ptr<0 && endPtr > 0
            if (minPagesToGrow > remainingPages) {
                log("Cannot grow enough", minPagesToGrow, remainingPages);
                throw reachedMemoryLimit;
            }
            if (amountToGrow < minPagesToGrow) amountToGrow = minPagesToGrow;
            if (!growS(amountToGrow)) {
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
        setNextPtr(add(ptr, size));
        return ptr;
    }

    @NoThrow
    public static Pointer findGap(int size) {
        final int sizeWithoutHelper = size - arrayOverhead;
        byte[][] largestGaps = GarbageCollector.largestGaps;
        for (int i = 0, l = largestGaps.length; i < l; i++) {
            byte[] array = largestGaps[i];
            if (array != null) {
                // three cases:
                //  a) we fit perfectly
                //  b) we fit, and let stuff remain
                //  c) we don't fit
                int available = array.length;
                if (available == sizeWithoutHelper) {
                    // if (printCtr++ < 0) log("GC replacing", size);
                    largestGaps[i] = null; // nothing is remaining -> set entry to null
                    freeMemory -= size;
                    return castToPtr(array); // the new instance is placed where the array was
                } else if (unsignedGreaterThanEqual(available, size)) {
                    return findGapFitIntoSpace(castToPtr(array), size, available + arrayOverhead);
                }
            }
        }
        return null; // no gap was found
    }

    @NoThrow
    private static Pointer findGapFitIntoSpace(final Pointer arrayPtr, final int size, final int available) {
        final int sizeWithHelper = size + arrayOverhead;
        // if (printCtr++ < 0) log("GC shrinking", ptr, available, size);
        // shrink array
        writeI32AtOffset(arrayPtr, objectOverhead, available - sizeWithHelper);
        freeMemory -= size;
        // calculate new pointer, and we're done :D
        return add(arrayPtr, available - size); // the new object is placed at the end
    }

    @NoThrow
    @Alias(names = "isParallelGC")
    @JavaScript(code = "return false;")
    static native boolean isParallelGC();

    @NoThrow
    static boolean unregisterWeakRef(Object instance) {
        lockMallocMutex();
        @SuppressWarnings("rawtypes")
        WeakRef weakRef = WeakRef.weakRefInstances.remove(getAddrS(instance));
        boolean wasReferenced = weakRef != null;
        unlockMallocMutex();
        while (weakRef != null) {
            weakRef.address = null;
            @SuppressWarnings("rawtypes")
            WeakRef nextRef = weakRef.next;
            weakRef.next = null; // doesn't matter anymore, so unlink them for GC
            weakRef = nextRef;
        }
        return wasReferenced;
    }

    @NoThrow
    static boolean contains(Object gapStart, byte[][] gapsInUse) {
        return gapStart instanceof byte[] && contains((byte[]) gapStart, gapsInUse);
    }

    @NoThrow
    static boolean contains(byte[] gapStart, byte[][] gapsInUse) {
        if (gapsInUse == null) return false;
        for (byte[] ptr : gapsInUse) {
            if (ptr == gapStart) return true;
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
