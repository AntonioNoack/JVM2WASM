package jvm.gc;

import annotations.NoThrow;
import jvm.Pointer;

import static jvm.JVMShared.*;
import static jvm.NativeLog.log;
import static jvm.Pointer.add;
import static jvm.gc.GCGapFinder.getInstanceSize;
import static jvm.gc.GCGapFinder.insertGapMaybe;
import static jvm.gc.GarbageCollector.largestGaps;
import static jvm.gc.GarbageCollector.largestGapsTmp;

@SuppressWarnings("unused")
public class GCUtils {

    @NoThrow
    private static void mergeGaps() {
        for (byte[] gap : largestGapsTmp) {
            if (gap != null) {
                int available = gap.length + arrayOverhead;
                insertGapMaybe(gap, available, largestGaps);
            }
        }
    }

    @NoThrow
    public static void validateAllClassIds() {
        Pointer ptr = getAllocationStart();
        Pointer end = getNextPtr();
        // log("Validating all dynamic instances", ptr, end, getStackDepth());
        while (Pointer.unsignedLessThan(ptr, end)) {
            Pointer size = getInstanceSize(ptr);
            // String className = classIdToInstance(readClassIdImpl(ptr)).getName();
            // log(className, ptr, size);
            ptr = add(ptr, size);
        }
        if (ptr != end) {
            log("Invalid end", ptr, end);
            throw new IllegalStateException();
        }
        // log("Finished validation");
    }
}
