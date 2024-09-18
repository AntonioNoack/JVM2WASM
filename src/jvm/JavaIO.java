package jvm;

import annotations.Alias;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.Charset;

import static jvm.JVM32.read32;
import static jvm.JavaLang.*;

public class JavaIO {

    /*@Alias(name = "new_java_io_OutputStreamWriter_Ljava_io_OutputStreamV")
    public static void newOutputStreamWriterOSV(OutputStreamWriter wr, OutputStream os)
            throws NoSuchFieldException, IllegalAccessException {
        if (os == null) throw new NullPointerException("OutputStream must not be null!");
        OutputStreamWriter.class.getField("lock").set(wr, os);
    }

    @Alias(name = "new_java_io_BufferedWriter_Ljava_io_WriterIV")
    public static void newBufferedWriterWrIV(BufferedWriter wr, Writer os, int var2)
            throws NoSuchFieldException, IllegalAccessException {
        BufferedWriter.class.getField("lock").set(wr, os);
        if (var2 <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        } else {
            // ^^, evil trick to save memory
            if(var2 > 64) var2 = 64;
            BufferedWriter.class.getField("out").set(wr, os);
            BufferedWriter.class.getField("cb").set(wr, JVM.byteStrings ? new byte[var2] : new char[var2]);
            BufferedWriter.class.getField("nChars").setInt(wr, var2);
            // default
            // BufferedWriter.class.getField("nextChar").set(wr, 0);
            BufferedWriter.class.getField("lineSeparator").set(wr, "\n");
        }
    }

    @Alias(name = "java_io_OutputStreamWriter_write_ACIIV")
    public static void OutputStreamWriter_write(OutputStreamWriter wr, char[] data, int start, int length)
            throws NoSuchFieldException, IllegalAccessException, IOException {
        OutputStream os = (OutputStream) OutputStreamWriter.class.getField("lock").get(wr);
        for (int i = 0; i < length; i++) {
            char c = data[start + i];
            if (c < 128) {
                // ASCII -> fine :)
                os.write(c);
            } else {
                // encode remaining data to bytes
                byte[] bytes = new String(data, start + i, length - i).getBytes();
                os.write(bytes);
            }
        }
    }

    @Alias(name = "java_io_OutputStreamWriter_flushBuffer_V")
    public static void OutputStreamWriter_flush(OutputStreamWriter wr)
            throws NoSuchFieldException, IllegalAccessException, IOException {
        OutputStream os = (OutputStream) OutputStreamWriter.class.getField("lock").get(wr);
        os.flush();
    }*/

    private static int lockFieldOffset = 0;

    @Alias(names = "new_java_io_InputStreamReader_Ljava_io_InputStreamV")
    public static void new_java_io_InputStreamReader_Ljava_io_InputStreamV(
            InputStreamReader reader, InputStream stream) throws NoSuchFieldException, IllegalAccessException {
        Class clazz = reader.getClass();
        Field field = clazz.getField("lock");
        lockFieldOffset = getFieldOffset(field);
        field.set(reader, stream);
    }

    @Alias(names = "new_java_io_InputStreamReader_Ljava_io_InputStreamLjava_nio_charset_CharsetV")
    public static void new_java_io_InputStreamReader_Ljava_io_InputStreamLjava_nio_charset_CharsetV(
            InputStreamReader reader, InputStream stream, Charset cs) throws NoSuchFieldException, IllegalAccessException {
        new_java_io_InputStreamReader_Ljava_io_InputStreamV(reader, stream);
    }

    @Alias(names = "java_io_InputStreamReader_read_I")
    public static int java_io_InputStreamReader_read_I(InputStreamReader reader) throws IOException {
        InputStream stream = ptrTo(read32(getAddr(reader) + lockFieldOffset));
        return stream.read();
    }

    @Alias(names = "java_io_InputStreamReader_read_ACIII")
    public static int java_io_InputStreamReader_read_ACIII(InputStreamReader reader, char[] chars, int start, int length) throws IOException {
        InputStream stream = ptrTo(read32(getAddr(reader) + lockFieldOffset));
        for (int i = 0; i < length; i++) {
            int code = stream.read();
            if (code < 0) return i;
            chars[start + i] = (char) code;
        }
        return length;
    }

}
