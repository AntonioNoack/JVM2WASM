package jvm;

import static jvm.JVMShared.createNativeArray1;
import static utils.StaticClassIndices.OBJECT_ARRAY;

public class JVMValues {
    // these must be allocated at the start of the application, so their memory is prepared
    public static Error
            reachedMemoryLimit = new OutOfMemoryError("Reached memory limit"),
            failedToAllocateMemory = new OutOfMemoryError("Failed to allocate more memory");

    public static final Object[] emptyArray = (Object[]) createNativeArray1(0, OBJECT_ARRAY);
}
