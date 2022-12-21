package test;

import annotations.NoThrow;

public class LambdaTest {

    public interface Callable {
        void run();
    }

    @NoThrow
    void call(long x) {
    }

    @NoThrow
    public Callable test() {
        return () -> {
            for (float i = 0; i < 3; i++) {
                call((long) i);
            }
        };
    }

    public void test2() {
        test().run();
    }

    private static void arrayTest(short[] a0, byte[] a1, int[] a2, float[] a3, double[] a4) {
        a0[17]++;
        a1[17]++;
        a2[17]++;
        a3[17]++;
        a4[17]++;
    }

    private static int test(long a, int b, long c) {
        return (int) (a + b + c);
    }

    private static int test(int a, long b, int c) {
        return (int) (a + b + c);
    }

}
