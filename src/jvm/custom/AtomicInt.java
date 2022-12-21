package jvm.custom;

import annotations.NoThrow;

@SuppressWarnings("unused")
public class AtomicInt extends Number {
    private int value = 0;

    public AtomicInt() {
    }

    public AtomicInt(int v) {
        value = v;
    }

    @NoThrow
    public int getAndAdd(int added) {
        int v = value;
        value = v + added;
        return v;
    }

    @NoThrow
    public int getAndSet(int set) {
        int v = value;
        value = set;
        return v;
    }

    @NoThrow
    public int addAndGet(int added) {
        int v = value + added;
        value = v;
        return v;
    }

    @NoThrow
    public int getAndIncrement() {
        return value++;
    }

    @NoThrow
    public int getAndDecrement() {
        return value--;
    }

    @NoThrow
    public int incrementAndGet() {
        return ++value;
    }

    @NoThrow
    public int decrementAndGet() {
        return --value;
    }

    @NoThrow
    public int get() {
        return value;
    }

    @Override
    public int intValue() {
        return value;
    }

    @Override
    public long longValue() {
        return value;
    }

    @Override
    public float floatValue() {
        return value;
    }

    @Override
    public double doubleValue() {
        return value;
    }
}
