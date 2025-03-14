package jvm.custom.awt;

import java.io.InputStream;

import static jvm.JVMShared.unsafeCast;

@SuppressWarnings("unused")
public class Font {
    private final int size;
    private final int flags;
    private final String name;

    public String getName() {
        return name;
    }

    public Font(String name, int flags, int size) {
        this.name = name;
        this.flags = flags;
        this.size = size;
    }

    public int getSize() {
        return size;
    }

    public java.awt.Font deriveFont(int flags, float size) {
        return unsafeCast(new Font(name, flags, (int) size));
    }

    public static java.awt.Font decode(String nameAndSize) {
        return unsafeCast(new Font(nameAndSize, 0, -1));
    }

    public boolean canDisplay(int i) {
        // todo implement properly
        return true;
    }

    public static java.awt.Font createFont(int size, InputStream stream) {
        throw new RuntimeException("Creating fonts from InputStreams is not supported");
    }
}
