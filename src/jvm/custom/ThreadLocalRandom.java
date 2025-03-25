package jvm.custom;

import java.util.Random;

public class ThreadLocalRandom extends Random {
    private ThreadLocalRandom() {
        super(System.currentTimeMillis());
    }

    public static final ThreadLocalRandom INSTANCE = new ThreadLocalRandom();

    public static ThreadLocalRandom current() {
        return INSTANCE;
    }
}
