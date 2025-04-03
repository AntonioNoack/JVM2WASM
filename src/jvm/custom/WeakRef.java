package jvm.custom;

import java.lang.ref.ReferenceQueue;

import static jvm.gc.GarbageCollector.lockMallocMutex;
import static jvm.gc.GarbageCollector.unlockMallocMutex;
import static jvm.JVM32.*;

@SuppressWarnings("rawtypes")
public class WeakRef<V> {

    public static final LongHashMap<WeakRef> weakRefInstances = new LongHashMap<>(256);

    // todo this address needs to become a long for 64-bit support...
    public long address;
    public WeakRef next; // linked-list of references to that instance

    public WeakRef(V instance) {
        address = getAddr(instance);
        if (isAfterAllocationStart()) {
            lockMallocMutex();
            next = weakRefInstances.put(address, this);
            unlockMallocMutex();
        } // else don't register, because instance isn't tracked
    }

    private boolean isAfterAllocationStart() {
        return isDynamicInstance(ptrTo(address));
    }

    @SuppressWarnings("unused")
    public WeakRef(V instance, ReferenceQueue q) {
        this(instance);
        // idk what to do about this ref queue...
    }

    public V get() {
        return ptrTo(address);
    }

}
