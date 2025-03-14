package jvm.utf8v2;

import org.jetbrains.annotations.NotNull;

import static jvm.JVMShared.unsafeCast;

public class UTF8Slice implements CharSequence {

    private int hash;
    private final int start, length;
    private final UTF8String base;

    // guaranteed to stay the same
    UTF8Slice(UTF8String base, int start, int length) {
        this.base = base;
        this.start = start;
        this.length = length;
    }

    @Override
    public int hashCode() {
        if (hash != 0 || length == 0) return hash;
        int calcHash = 0;
        byte[] value = base.value;
        for (int i = start, e = start + length; i < e; i++) {
            calcHash = 31 * calcHash + (char) value[i];
        }
        hash = calcHash;
        return hash;
    }

    public int length() {
        return length;
    }

    @Override
    public char charAt(int i) {
        return (char) base.value[i + start];
    }

    @NotNull
    @Override
    public CharSequence subSequence(int start, int end) {
        return base.subSequence(start + this.start, end + this.start);
    }

    @NotNull
    @Override
    public String toString() {
        return unsafeCast(this);
    }

}
