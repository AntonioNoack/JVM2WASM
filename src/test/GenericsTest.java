package test;

public class GenericsTest<V> {
    static class B extends GenericsTest<int[]> {

    }

    static class C extends GenericsTest<Void> {

    }

    /*static class D implements Comparable<Float>, Comparable<Double> {

        @Override
        public int compareTo(@NotNull Double aDouble) {
            return 0;
        }
    }*/

}
