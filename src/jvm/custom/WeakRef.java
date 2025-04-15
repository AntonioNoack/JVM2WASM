package jvm.custom;

import annotations.PureJavaScript;
import annotations.UnsafePointerField;

import java.lang.ref.ReferenceQueue;

import static jvm.JVMShared.isDynamicInstance;
import static jvm.Pointer.getAddrS;
import static jvm.gc.GarbageCollector.lockMallocMutex;
import static jvm.gc.GarbageCollector.unlockMallocMutex;

@SuppressWarnings("rawtypes")
public class WeakRef<V> {

    public static final LongHashMap<WeakRef> weakRefInstances = new LongHashMap<>(256);

    @UnsafePointerField
    public V address;

    public WeakRef next; // linked-list of references to that instance

    @PureJavaScript(code = "arg0.value = new WeakRef(arg1);")
    public WeakRef(V instance) {
        address = instance;
        if (isAfterAllocationStart()) {
            lockMallocMutex();
            next = weakRefInstances.put(getAddrS(address), this);
            unlockMallocMutex();
        } // else don't register, because instance isn't tracked
    }

    private boolean isAfterAllocationStart() {
        return isDynamicInstance(address);
    }

    @SuppressWarnings("unused")
    public WeakRef(V instance, ReferenceQueue q) {
        this(instance);
        // idk what to do about this ref queue...
    }

    @PureJavaScript(code = "return arg0.value.deref();")
    public V get() {
        return address;
    }

}
