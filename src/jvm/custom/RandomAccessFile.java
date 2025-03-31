package jvm.custom;

import java.io.JavaIO;

import static jvm.NativeLog.log;

// todo for C++ use actual file implementations, for Web use virtual files
public class RandomAccessFile {
    private long position = 0;
    private final String path;

    @SuppressWarnings("unused")
    public RandomAccessFile(String path, String mode) {
        this.path = path;
    }

    @SuppressWarnings("unused")
    public void seek(long newPosition) {
        position = newPosition;
    }

    // There indeed are two functions. I'm not sure, why
    @SuppressWarnings("unused")
    public void write(byte[] srcBytes, int readOffset, int length) {
        writeBytes(srcBytes, readOffset, length);
    }

    public void writeBytes(byte[] srcBytes, int readOffset, int length) {
        JavaIO.FileInfo fi = JavaIO.files.get(path);
        if (fi == null) {
            log("Missing file for RandomAccessFile.writeBytes", path);
            return;
        }
        byte[] dstBytes = fi.content;
        System.arraycopy(srcBytes, readOffset, dstBytes, (int) position, length);
    }

    public void close() {
    }
}
