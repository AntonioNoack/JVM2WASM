package utils;

public class WASMTypes {

    public static final String f32 = "f32";
    public static final String f64 = "f64";
    public static final String i32 = "i32";
    public static final String i64 = "i64";

    public static final int numWASMTypes = 4;

    public static int getWASMTypeIndex(String wasmType) {
        switch (wasmType) {
            case i32:
                return 0;
            case i64:
                return 1;
            case f32:
                return 2;
            case f64:
                return 3;
            default:
                throw new IllegalArgumentException(wasmType);
        }
    }

}
