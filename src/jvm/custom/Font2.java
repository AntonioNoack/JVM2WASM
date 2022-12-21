package jvm.custom;

import java.awt.*;
import java.io.InputStream;

import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;

public class Font2 {
    private final int size;
    private final int flags;
    private final String name;

    public String getName() {
        return name;
    }

    public Font2(String name, int flags, int size) {
        this.name = name;
        this.flags = flags;
        this.size = size;
    }

    public int getSize() {
        return size;
    }

    public Font deriveFont(int flags, float size) {
        return ptrTo(getAddr(new Font2(name, flags, (int) size)));
    }

    public static Font decode(String nameAndSize) {
        return ptrTo(getAddr(new Font2(nameAndSize, 0, -1)));
    }

    public boolean canDisplay(int i) {
        // todo implement properly
        return true;
    }

    public static Font createFont(int size, InputStream stream) {
        throw new RuntimeException("Creating fonts from InputStreams is not supported");
    }
}
