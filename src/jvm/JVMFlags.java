package jvm;

@SuppressWarnings("ConstantValue")
public class JVMFlags {
    public static boolean is32Bits = true;
    public static int ptrSize = is32Bits ? 4 : 8;
    public static int ptrSizeBits = is32Bits ? 2 : 3;
}
