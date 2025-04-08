package jvm.gc;

import java.util.Comparator;

import static jvm.Pointer.getAddrS;

/**
 * QuickSort for unsigned-int-arrays, in-place, stable and fast
 */
public class AddressComparator implements Comparator<Object> {
    static final AddressComparator INSTANCE = new AddressComparator();

    @Override
    public int compare(Object o1, Object o2) {
        return Long.compareUnsigned(getAddrS(o1), getAddrS(o2));
    }
}
