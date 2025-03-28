package engine.test;

public class SciMark {
    public static void test() {
        // run SciMark 2.0
        // https://math.nist.gov/scimark2/download_c.html

        // score on Windows 10 with Ryzen 5 2600 and 32 GB DDR4 3200 memory:
        // Composite Score: 2325.8121583619168
        // FFT (1024):      1634.7147034240681
        // SOR (100x100):   1593.8420361290785
        // Monte Carlo :    1425.0057674105672
        // Sparse matmult (N=1000, nz=5000): 2275.555418038082
        // LU (100x100):    4699.942866807789
        // java.vendor: Oracle Corporation
        // java.version: 18.0.1.1
        // os.arch: amd64
        // os.name: Windows 10
        // os.version: 10.0

        // score in WASM:
        // Composite Score: 30.117112201217985 (77.2x slower)
        // FFT (1024):      24.38815326998617  (67.0x slower)
        // SOR (100x100):   54.372315893202256 (29.3x slower)
        // Monte Carlo :    14.19992940825455  (100.3x slower)
        // Sparse matmult (N=1000, nz=5000): 27.788331503408706 (81.9x slower)
        // LU (100x100): 29.83683093123827 (157x slower)

        // score on Windows 10 with Ryzen 9 7950x3D and 32 GB DDR5 4800 memory:
        // Composite Score: 4646.1204508118535
        // FFT (1024): 2895.005394914011
        // SOR (100x100):   2306.3494956325403
        // Monte Carlo : 1431.178740330143
        // Sparse matmult (N=1000, nz=5000): 4086.422471688955
        // LU (100x100): 12511.64615149362
        //
        // java.vendor: Amazon.com Inc.
        // java.version: 1.8.0_402
        // os.arch: amd64
        // os.name: Windows 10
        // os.version: 10.0

        // score in WASM (27.03.2025):
		// Composite Score: 85.8622179629545 (54x slower)
		// FFT (1024): 66.81890261578864 (43x slower)
		// SOR (100x100):   159.25247497933898 (14x slower)
		// Monte Carlo : 29.305181274193863 (48x slower)
		// Sparse matmult (N=1000, nz=5000): 86.39071470757803 (47x slower)
		// LU (100x100): 87.54381623787297 (142x slower)

        // score as C++ compiled in debug-mode:
        // Composite Score: 169.530536 (27x slower)
        // FFT (1024): 75.234672 (38x slower)
        // SOR (100x100):   407.734887 (5.6x slower)
        // Monte Carlo : 63.913207 (22x slower)
        // Sparse matmult (N=1000, nz=5000): 206.217749 (20x slower)
        // LU (100x100): 94.552166 (132x slower)

        // score as C++ compiled in release-mode:
        // Composite Score: 3265.814701 (1.4x slower)
        // FFT (1024): 3037.423931 (1.05x faster)
        // SOR (100x100):   2611.934759 (1.13x faster)
        // Monte Carlo : 1126.992244 (1.27x slower)
        // Sparse matmult (N=1000, nz=5000): 4050.119758 (tie)
        // LU (100x100): 5502.602815 (2.27x slower)

        jnt.scimark2.commandline.main(new String[0]);
    }

    public static void main(String[] args) {
        test();
    }
}
