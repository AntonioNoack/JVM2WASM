package jvm.utf8;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;

@SuppressWarnings("unused")
public class OutputStreamWriterUTF8 extends Writer {

    private final OutputStream dst;

    public OutputStreamWriterUTF8(OutputStream dst) {
        this.dst = dst;
    }

    // Used by HierarchicalDatabase.storeIndex -> OutputStream.writer()
    public OutputStreamWriterUTF8(OutputStream dst, Charset charset) {
        this(dst);
    }

    @Override
    public void write(int i) throws IOException {
        // UTF-8 decoding :)
        if (i >= 1 << 28) dst.write(0x80 | (i >>> 28));
        if (i >= 1 << 21) dst.write(0x80 | (i >>> 21));
        if (i >= 1 << 14) dst.write(0x80 | (i >>> 14));
        if (i >= 1 << 7) dst.write(0x80 | (i >>> 7));
        dst.write(i);
    }

    @Override
    public void write(@NotNull char[] chars, int start, int length) throws IOException {
        for (int i = 0; i < length; i++) dst.write(chars[start + i]);
    }

    @Override
    public void write(@NotNull String s, int start, int length) throws IOException {
        for (int i = 0; i < length; i++) dst.write(s.charAt(start + i));
    }

    @Override
    public void flush() throws IOException {
        dst.flush();
    }

    void flushBuffer() {
    }

    @Override
    public void close() throws IOException {
        dst.close();
    }
}
