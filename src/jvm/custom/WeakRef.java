package jvm.custom;

import jvm.GC;

import java.lang.ref.ReferenceQueue;

import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;

public class WeakRef {

    // will grow theoretically a lot...
    public static final IntHashMap lastDeleted = new IntHashMap(256);
    public static final IntHashSet ofInterest = new IntHashSet(256);

    private final int address, generation;

    public WeakRef(Object instance) {
        address = getAddr(instance);
        generation = GC.generation;
        ofInterest.add(address);
    }

    public WeakRef(Object instance, ReferenceQueue q) {
        this(instance);
        // idk what to do about this ref queue...
    }

    public Object get() {
        int ld = lastDeleted.get(address, 0);
        if (ld < generation) return ptrTo(address);// everything is fine :)
        else return null;// it was deleted
    }

}
