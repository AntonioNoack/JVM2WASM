package jvm;

import annotations.NoThrow;

import static jvm.JVMShared.unsignedGreaterThan;
import static jvm.JVMShared.unsignedLessThanEqual;

/**
 * QuickSort for unsigned-int-arrays, in-place, stable and fast
 */
public class QuickSort {

    @NoThrow
    @SuppressWarnings("TailRecursion") // we build a large tree, so not really worth it, I think
    public static void quickSort(int[] values, int begin, int endIncl) {
        if (begin + 12 < endIncl) {
            int partitionIndex = partition(values, begin, endIncl);
            quickSort(values, begin, partitionIndex - 1);
            quickSort(values, partitionIndex + 1, endIncl);
        } else if (begin < endIncl) {
            // there is just a few elements so don't use the stack, and instead brute force
            slowSort(values, begin, endIncl);
        }
    }

    @NoThrow
    private static void slowSort(int[] values, int begin, int endIncl) {
        for (int i = begin; i < endIncl; i++) {
            for (int j = i + 1; j <= endIncl; j++) {
                int vi = values[i];
                int vj = values[j];
                if (unsignedGreaterThan(vi, vj)) {
                    values[j] = vi;
                    values[i] = vj;
                }
            }
        }
    }

    @NoThrow
    private static int partition(int[] values, int begin, int endIncl) {
        int pivot = values[endIncl];
        int i = (begin - 1);

        for (int j = begin; j < endIncl; j++) {
            int vj = values[j];
            if (unsignedLessThanEqual(vj, pivot)) {
                i++;

                int vi = values[i];
                values[j] = vi;
                values[i] = vj;
            }
        }

        int vi1 = values[i + 1];
        values[i + 1] = values[endIncl];
        values[endIncl] = vi1;

        return i + 1;
    }
}
