package test;

public class BaseTest {
    static int test_tableSizeFor(int var0) {
        return var0 < 0 ? 1 : (var0 < 2 ? 3 : 4);
    }
}
