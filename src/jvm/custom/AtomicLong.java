package jvm.custom;

import annotations.NoThrow;

@SuppressWarnings("unused")
public class AtomicLong extends Number {
    private long value = 0;

    public AtomicLong() {
    }

    public AtomicLong(long v) {
        value = v;
    }

    @NoThrow
    public long getAndAdd(long added) {
        long v = value;
        value = v + added;
        return v;
    }

    @NoThrow
    public long addAndGet(long added) {
        long v = value + added;
        value = v;
        return v;
    }

    @NoThrow
    public long getAndIncrement() {
        return value++;
    }

    @NoThrow
    public long getAndDecrement() {
        return value--;
    }

    @NoThrow
    public long incrementAndGet() {
        return ++value;
    }

    @NoThrow
    public long decrementAndGet() {
        return --value;
    }

    @NoThrow
    public long get() {
        return value;
    }

    @NoThrow
    public void set(long v) {
        value = v;
    }

    @NoThrow
    public boolean compareAndSet(long test, long newValue) {
        if (test == value) {
            value = newValue;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int intValue() {
        return (int) value;
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
