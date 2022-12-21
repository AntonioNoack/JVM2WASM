package jvm.utf8;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;

@SuppressWarnings("unused")
public class BufferedWriterUTF8 extends Writer {

    private final Writer dst;

    public BufferedWriterUTF8(Writer dst) {
        this.dst = dst;
    }

    @Override
    public void write(@NotNull char[] chars, int i, int i1) throws IOException {
        dst.write(chars, i, i1);
    }

    @Override
    public void write(@NotNull String s) throws IOException {
        dst.write(s);
    }

    @Override
    public void flush() throws IOException {
        dst.flush();
    }

    private void flushBuffer() {
    }

    @Override
    public void close() throws IOException {
        dst.close();
    }

    public void newLine() throws IOException {
        write('\n');
    }

    // todo replace contents as well :3

    // todo also replace JS fill() method :D


}
