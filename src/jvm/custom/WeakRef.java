package jvm.custom;

import java.lang.ref.ReferenceQueue;

import static jvm.GC.lockMallocMutex;
import static jvm.GC.unlockMallocMutex;
import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;

public class WeakRef<V> {

    public static final IntHashMap<WeakRef> weakRefInstances = new IntHashMap<>(256);

    public int address;
    public WeakRef next; // linked-list of references to that instance

    public WeakRef(V instance) {
        address = getAddr(instance);
        lockMallocMutex();
        next = weakRefInstances.put(address, this);
        unlockMallocMutex();
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
