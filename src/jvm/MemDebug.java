package jvm;

import annotations.Alias;
import annotations.Export;
import annotations.NoThrow;

import static jvm.JVM32.readClassId;
import static jvm.JVMShared.*;

public class MemDebug {

    @Export
    @NoThrow
    @Alias(names = "r64f")
    public static double r64f(Pointer addr) {
        return read64f(addr);
    }

    @Export
    @NoThrow
    @Alias(names = "r64")
    public static long r64(Pointer addr) {
        return read64(addr);
    }

    @Export
    @NoThrow
    @Alias(names = "r32f")
    public static float r32f(Pointer addr) {
        return read32f(addr);
    }

    @Export
    @NoThrow
    @Alias(names = "r32")
    public static int r32(Pointer addr) {
        return read32(addr);
    }

    @Export
    @NoThrow
    @Alias(names = "rCl")
    public static int rCl(Object addr) {
        return readClassId(addr);
    }

    @Export
    @NoThrow
    @Alias(names = "r16")
    public static int r16(int addr) {
        return read16u(addr);
    }

    @Export
    @NoThrow
    @Alias(names = "r8")
    public static int r8(int addr) {
        return read8(addr);
    }

    @Export
    @NoThrow
    @Alias(names = "w64f")
    public static void w64f(int addr, double v) {
        write64(addr, v);
    }

    @Export
    @NoThrow
    @Alias(names = "w64")
    public static void w64(int addr, long v) {
        write64(addr, v);
    }

    @Export
    @NoThrow
    @Alias(names = "w32f")
    public static void w32f(int addr, float v) {
        write32(addr, v);
    }

    @Export
    @NoThrow
    @Alias(names = "w32")
    public static void w32(int addr, int v) {
        write32(addr, v);
    }

    @Export
    @NoThrow
    @Alias(names = "w16")
    public static void w16(int addr, short s) {
        write16(addr, s);
    }

    @Export
    @NoThrow
    @Alias(names = "w8")
    public static void w8(int addr, byte b) {
        write8(addr, b);
    }

}
