package jvm.custom;

import annotations.NoThrow;

import java.util.function.Supplier;

import static jvm.JVMShared.unsafeCast;

// in theory, we could replace all instances of this with the immediate result...
@SuppressWarnings("unused")
public class ThreadLocal2<V> {

    @NoThrow
    public ThreadLocal2(Supplier<V> supplier) {
        this.supplier = supplier;
    }

    public static <V> ThreadLocal<V> withInitial(Supplier<V> supplier) {
        return unsafeCast(new ThreadLocal2<>(supplier));
    }

    private V object;
    private Supplier<V> supplier;

    public V get() {
        Supplier<V> supplier = this.supplier;
        if (supplier != null) {
            object = supplier.get();
            this.supplier = null;
        }
        return object;
    }
}
