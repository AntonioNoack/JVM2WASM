package jvm.custom;

import jvm.JVM32;

import java.lang.ref.ReferenceQueue;

import static jvm.GarbageCollector.lockMallocMutex;
import static jvm.GarbageCollector.unlockMallocMutex;
import static jvm.JVM32.getAllocationStart;
import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;

@SuppressWarnings("rawtypes")
public class WeakRef<V> {

    public static final IntHashMap<WeakRef> weakRefInstances = new IntHashMap<>(256);

    public int address;
    public WeakRef next; // linked-list of references to that instance

    public WeakRef(V instance) {
        address = getAddr(instance);
        if (JVM32.unsignedGreaterThanEqual(address, getAllocationStart())) {
            lockMallocMutex();
            next = weakRefInstances.put(address, this);
            unlockMallocMutex();
        } // else don't register, because instance isn't tracked
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
