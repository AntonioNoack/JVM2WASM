package jvm;

import annotations.Alias;
import annotations.NoThrow;

import static jvm.JVM32.*;

public class MemDebug {

    @NoThrow
    @Alias(names = "r64f")
    public static double r64f(int addr) {
        return read64f(addr);
    }

    @NoThrow
    @Alias(names = "r64")
    public static long r64(int addr) {
        return read64(addr);
    }

    @NoThrow
    @Alias(names = "r32f")
    public static float r32f(int addr) {
        return read32f(addr);
    }

    @NoThrow
    @Alias(names = "r32")
    public static int r32(int addr) {
        return read32(addr);
    }

    @NoThrow
    @Alias(names = "rCl")
    public static int rCl(int addr) {
        return readClass(addr);
    }

    @NoThrow
    @Alias(names = "r16")
    public static int r16(int addr) {
        return read16u(addr);
    }

    @NoThrow
    @Alias(names = "r8")
    public static int r8(int addr) {
        return read8(addr);
    }

    @NoThrow
    @Alias(names = "w64f")
    public static void w64f(int addr, double v) {
        write64(addr, v);
    }

    @NoThrow
    @Alias(names = "w64")
    public static void w64(int addr, long v) {
        write64(addr, v);
    }

    @NoThrow
    @Alias(names = "w32f")
    public static void w32f(int addr, float v) {
        write32(addr, v);
    }

    @NoThrow
    @Alias(names = "w32")
    public static void w32(int addr, int v) {
        write32(addr, v);
    }

    @NoThrow
    @Alias(names = "w16")
    public static void w16(int addr, short s) {
        write16(addr, s);
    }

    @NoThrow
    @Alias(names = "w8")
    public static void w8(int addr, byte b) {
        write8(addr, b);
    }

}
