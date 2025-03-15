package jvm;

import static jvm.JVM32.createNativeArray1;
import static jvm.JVM32.ptrTo;

public class JVMValues {
    // these must be allocated at the start of the application, so their memory is prepared
    public static Error
            reachedMemoryLimit = new OutOfMemoryError("Reached memory limit"),
            failedToAllocateMemory = new OutOfMemoryError("Failed to allocate more memory");

    public static final Object emptyArray = ptrTo(createNativeArray1(0, 1));
}
