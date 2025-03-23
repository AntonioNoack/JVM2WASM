package jvm.custom;

import java.util.concurrent.TimeUnit;

public class Semaphore {
    int capacity = 0;

    public Semaphore(int value) {
        this.capacity = value;
    }

    public void acquire() {
        acquire(1);
    }

    public void acquire(int delta) {
        capacity -= delta;
    }

    public void release() {
        release(1);
    }

    public void release(int delta) {
        capacity += delta;
    }

    public boolean tryAcquire(int delta, long numTimeout, TimeUnit timeoutUnit) {
        if (capacity >= delta) {
            acquire(delta);
            return true;
        } else return false;
    }
}
