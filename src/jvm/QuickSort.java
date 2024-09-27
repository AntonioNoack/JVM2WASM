package jvm;

import annotations.NoThrow;

public class QuickSort {

    @NoThrow
    public static void quickSort(int[] arr, int begin, int endIncl) {
        if (begin < endIncl) {
            int partitionIndex = partition(arr, begin, endIncl);

            quickSort(arr, begin, partitionIndex - 1);
            quickSort(arr, partitionIndex + 1, endIncl);
        }
    }

    @NoThrow
    private static int partition(int[] arr, int begin, int endIncl) {
        int pivot = arr[endIncl];
        int i = (begin - 1);

        for (int j = begin; j < endIncl; j++) {
            if (arr[j] <= pivot) {
                i++;

                int swapTemp = arr[i];
                arr[i] = arr[j];
                arr[j] = swapTemp;
            }
        }

        int swapTemp = arr[i + 1];
        arr[i + 1] = arr[endIncl];
        arr[endIncl] = swapTemp;

        return i + 1;
    }
}
