package utils;

public class WASMTypes {

    public static final String f32 = "f32";
    public static final String f64 = "f64";
    public static final String i32 = "i32";
    public static final String i64 = "i64";

    public static final int numWASMTypes = 4;

    public static boolean isWASMType(String name) {
        switch (name) {
            case i32:
            case i64:
            case f32:
            case f64:
                return true;
            default:
                return false;
        }
    }

    public static int getWASMTypeIndex(WASMType wasmType) {
        return wasmType.ordinal();
    }

}
