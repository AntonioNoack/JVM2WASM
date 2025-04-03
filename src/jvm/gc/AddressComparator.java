package jvm.gc;

import java.util.Comparator;

import static jvm.JVM32.getAddr;

/**
 * QuickSort for unsigned-int-arrays, in-place, stable and fast
 */
public class AddressComparator implements Comparator<Object> {
    static final AddressComparator INSTANCE = new AddressComparator();

    @Override
    public int compare(Object o1, Object o2) {
        return Integer.compareUnsigned(getAddr(o1), getAddr(o2));
    }
}
