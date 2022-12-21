package jvm.custom;

import kotlin.Lazy;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.internal.DefaultConstructorMarker;

@SuppressWarnings("unused")
public class SimpleLazy<T> implements Lazy<T> {

    private final Function0<T> generator;

    public SimpleLazy(Function0<T> generator) {
        this.generator = generator;
    }

    public SimpleLazy(Function0<T> generator, Object sth, int a, DefaultConstructorMarker sthElse) {
        this.generator = generator;
    }

    private T value;
    private boolean inited;

    @Override
    public T getValue() {
        if (!inited) {
            value = generator.invoke();
            inited = true;
        }
        return value;
    }

    @Override
    public boolean isInitialized() {
        return inited;
    }
}
