package jvm.utf8;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

@SuppressWarnings("unused")
public class IntStreamV2 implements IntStream {

    private final String str;

    public IntStreamV2(String str) {
        this.str = str;
    }

    @Override
    public int[] toArray() {
        // todo proper encoding
        int[] cp = new int[str.length()];
        for (int i = 0, l = cp.length; i < l; i++) {
            cp[i] = str.charAt(i);
        }
        return cp;
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        // todo proper encoding
        return new PrimitiveIterator.OfInt() {
            int index = 0;

            @Override
            public int nextInt() {
                return str.charAt(index++);
            }

            @Override
            public boolean hasNext() {
                return index < str.length();
            }
        };
    }

    @Override
    public IntStream filter(IntPredicate intPredicate) {
        return null;
    }

    @Override
    public IntStream map(IntUnaryOperator intUnaryOperator) {
        return null;
    }

    @Override
    public <U> Stream<U> mapToObj(IntFunction<? extends U> intFunction) {
        return null;
    }

    @Override
    public LongStream mapToLong(IntToLongFunction intToLongFunction) {
        return null;
    }

    @Override
    public DoubleStream mapToDouble(IntToDoubleFunction intToDoubleFunction) {
        return null;
    }

    @Override
    public IntStream flatMap(IntFunction<? extends IntStream> intFunction) {
        return null;
    }

    @Override
    public IntStream distinct() {
        return null;
    }

    @Override
    public IntStream sorted() {
        return null;
    }

    @Override
    public IntStream peek(IntConsumer intConsumer) {
        return null;
    }

    @Override
    public IntStream limit(long l) {
        return null;
    }

    @Override
    public IntStream skip(long l) {
        return null;
    }

    @Override
    public void forEach(IntConsumer intConsumer) {

    }

    @Override
    public void forEachOrdered(IntConsumer intConsumer) {

    }

    @Override
    public int reduce(int i, IntBinaryOperator intBinaryOperator) {
        return 0;
    }

    @Override
    public OptionalInt reduce(IntBinaryOperator intBinaryOperator) {
        return null;
    }

    @Override
    public <R> R collect(Supplier<R> supplier, ObjIntConsumer<R> objIntConsumer, BiConsumer<R, R> biConsumer) {
        return null;
    }

    @Override
    public int sum() {
        return 0;
    }

    @Override
    public OptionalInt min() {
        return null;
    }

    @Override
    public OptionalInt max() {
        return null;
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public OptionalDouble average() {
        return null;
    }

    @Override
    public IntSummaryStatistics summaryStatistics() {
        return null;
    }

    @Override
    public boolean anyMatch(IntPredicate intPredicate) {
        return false;
    }

    @Override
    public boolean allMatch(IntPredicate intPredicate) {
        return false;
    }

    @Override
    public boolean noneMatch(IntPredicate intPredicate) {
        return false;
    }

    @Override
    public OptionalInt findFirst() {
        return null;
    }

    @Override
    public OptionalInt findAny() {
        return null;
    }

    @Override
    public LongStream asLongStream() {
        return null;
    }

    @Override
    public DoubleStream asDoubleStream() {
        return null;
    }

    @Override
    public Stream<Integer> boxed() {
        return null;
    }

    @Override
    public IntStream sequential() {
        return null;
    }

    @Override
    public IntStream parallel() {
        return null;
    }

    @NotNull
    @Override
    public IntStream unordered() {
        return null;
    }

    @NotNull
    @Override
    public IntStream onClose(Runnable runnable) {
        return null;
    }

    @Override
    public void close() {

    }

    @Override
    public Spliterator.OfInt spliterator() {
        return null;
    }

    @Override
    public boolean isParallel() {
        return false;
    }
}
