package jvm.gc;

import annotations.NoThrow;

import static jvm.ArrayAccessUnchecked.arrayLength;
import static jvm.JVM32.*;
import static jvm.JVMShared.*;
import static jvm.NativeLog.log;
import static jvm.gc.GCGapFinder.getInstanceSize;
import static jvm.gc.GCGapFinder.insertGapMaybe;
import static jvm.gc.GarbageCollector.*;

@SuppressWarnings("unused")
public class GCUtils {

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
}
