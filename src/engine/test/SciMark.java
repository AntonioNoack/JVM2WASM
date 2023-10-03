package engine.test;

public class SciMark {
	public static void test() {
		// run SciMark 2.0
		// https://math.nist.gov/scimark2/download_c.html
		// score on Windows 10 with Ryzen 5 2600 and 32 GB memory:
		// SciMark 2.0a
		//
		// Composite Score: 2325.8121583619168
		// FFT (1024):      1634.7147034240681
		// SOR (100x100):   1593.8420361290785
		// Monte Carlo :    1425.0057674105672
		// Sparse matmult (N=1000, nz=5000): 2275.555418038082
		// LU (100x100):    4699.942866807789
		//
		// java.vendor: Oracle Corporation
		// java.version: 18.0.1.1
		// os.arch: amd64
		// os.name: Windows 10
		// os.version: 10.0

		// SciMark 2.0a
		// Composite Score: 30.117112201217985 (77.2x)
		// FFT (1024):      24.38815326998617  (67.0x)
		// SOR (100x100):   54.372315893202256 (29.3x)
		// Monte Carlo :    14.19992940825455  (100.3x)
		// Sparse matmult (N=1000, nz=5000): 27.788331503408706 (81.9x)
		// LU (100x100): 29.83683093123827 (157x)
		// java.vendor: null
		// java.version: null
		// os.arch: null
		// os.name: Linux Web
		// os.version: null
		// log("Running SciMark");
		jnt.scimark2.commandline.main(new String[0]);
	}
}
