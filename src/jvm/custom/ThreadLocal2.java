package jvm.custom;

import annotations.NoThrow;

import java.util.function.Supplier;

import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;

@SuppressWarnings("unused")
public class ThreadLocal2<V> {

    @NoThrow
    public ThreadLocal2(Supplier<V> supplier) {
        this.supplier = supplier;
    }

    public static <V> ThreadLocal<V> withInitial(Supplier<V> supplier) {
        return ptrTo(getAddr(new ThreadLocal2<>(supplier)));
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
