package jvm.utf8v2;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;
import java.util.Locale;

import static jvm.JVM32.getAllocationStart;
import static jvm.JVM32.unsignedLessThan;
import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;
import static jvm.JavaUtil.String_format;

@SuppressWarnings("unused")
public class UTF8String implements CharSequence, Comparable<String> {

    private int hash;
    public byte[] value;

    // guaranteed to stay the same
    public UTF8String(byte[] values, Charset charset) {
        byte[] clone = new byte[values.length];
        System.arraycopy(values, 0, clone, 0, values.length);
        this.value = clone;
    }

    // guaranteed to stay the same
    private UTF8String(byte[] values, boolean nothing) {
        this.value = values;
    }

    @Override
    public int hashCode() {
        if (hash != 0 || value.length == 0) return hash;
        int calcHash = 0;
        byte[] value = this.value;
        for (byte v : value) {
            calcHash = 31 * calcHash + (char) v;
        }
        hash = calcHash;
        return hash;
    }

    public int length() {
        return value.length;
    }

    @Override
    public char charAt(int i) {
        return (char) value[i];
    }

    @NotNull
    @Override
    public CharSequence subSequence(int start, int end) {
        // return UTF8String / Slice depending on whether included, and length :)
        if (start == 0 && end == length()) return this;
        int length = end - start;
        if (length + length >= this.length() || (length > 8 && unsignedLessThan(getAddr(this), getAllocationStart()))) {
            return new UTF8Slice(this, start, length);
        } else {
            byte[] bytes = new byte[length];
            System.arraycopy(value, start, bytes, 0, length);
            return new UTF8String(bytes, false);
        }
    }

    public String toLowerCase(Locale lx) {
        byte[] lc = null;
        byte[] uc = value;
        for (int i = 0, l = length(); i < l; i++) {
            byte c = uc[i];
            int d = c - 'A';
            if (unsignedLessThan(d, 26)) {
                if (lc == null) {
                    lc = new byte[l];
                    System.arraycopy(uc, 0, lc, 0, i);
                }
                c = (byte) (d + 'a');
            }
            if (lc != null) lc[i] = c;
        }
        if (lc == null) return ptrTo(getAddr(this));
        return ptrTo(getAddr(new UTF8String(lc, false)));
    }

    public String toUpperCase(Locale lx) {
        byte[] lc = null;
        byte[] uc = value;
        for (int i = 0, l = length(); i < l; i++) {
            byte c = uc[i];
            int d = c - 'a';
            if (unsignedLessThan(d, 26)) {
                if (lc == null) {
                    lc = new byte[l];
                    System.arraycopy(uc, 0, lc, 0, i);
                }
                c = (byte) (d + 'A');
            }
            if (lc != null) lc[i] = c;
        }
        if (lc == null) return ptrTo(getAddr(this));
        return ptrTo(getAddr(new UTF8String(lc, false)));
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof UTF8String)) return false;
        UTF8String s = (UTF8String) o;
        if (s.length() != length()) return false;
        if (s.hashCode() != hashCode()) return false;
        byte[] v0 = s.value, v1 = value;
        // could be made more efficient by comparing using raw bytes
        for (int i = 0, l = v0.length; i < l; i++) {
            if (v0[i] != v1[i]) return false;
        }
        return true;
    }

    public static String format(String format, Object[] args) {
        return String_format(null, format, args);
    }

    @NotNull
    @Override
    public String toString() {
        return ptrTo(getAddr(this));
    }

    @Override
    public int compareTo(@NotNull String other1) {
        UTF8String other = ptrTo(getAddr(other1));
        byte[] var5 = value;
        byte[] var6 = other.value;
        int var2 = var5.length;
        int var3 = var6.length;
        int var4 = Math.min(var2, var3);
        for (int var7 = 0; var7 < var4; ++var7) {
            byte var8 = var5[var7];
            byte var9 = var6[var7];
            if (var8 != var9) {
                return var8 - var9;
            }
        }
        return var2 - var3;
    }
}
