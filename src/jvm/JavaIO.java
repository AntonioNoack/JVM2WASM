package jvm;

import annotations.Alias;
import annotations.NoThrow;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.Charset;

import static jvm.JVM32.throwJs;
import static jvm.JVMShared.read32;
import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;
import static jvm.JavaReflect.getFieldOffset;

public class JavaIO {

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
        return getInputStream(reader).read();
    }

    @Alias(names = "java_io_InputStreamReader_read_ACIII")
    public static int java_io_InputStreamReader_read_ACIII(InputStreamReader reader, char[] chars, int start, int length) throws IOException {
        InputStream stream = getInputStream(reader);
        for (int i = 0; i < length; i++) {
            int code = stream.read();
            if (code < 0) {
                // if i == 0, we reached the end
                return i == 0 ? -1 : i;
            }
            chars[start + i] = (char) code;
        }
        return length;
    }

    @Alias(names = "java_io_InputStreamReader_close_V")
    public static void java_io_InputStreamReader_close_V(InputStreamReader reader) throws IOException {
        getInputStream(reader).close();
    }

    @NoThrow
    private static InputStream getInputStream(InputStreamReader reader) {
        return ptrTo(read32(getAddr(reader) + lockFieldOffset));
    }

    @Alias(names = "java_io_InputStreamReader_ready_Z")
    public static boolean java_io_InputStreamReader_ready_Z(InputStreamReader reader) throws IOException {
        return getInputStream(reader).available() > 0;
    }

    @Alias(names = "java_io_BufferedInputStream_close_V")
    public static void java_io_BufferedInputStream_close_V(BufferedInputStream stream)
            throws IOException, NoSuchFieldException, IllegalAccessException {
        InputStream src = (InputStream) stream.getClass().getField("in").get(stream);
        src.close();
    }

    @Alias(names = "java_io_FileDescriptor_closeAll_Ljava_io_CloseableV")
    public static void java_io_FileDescriptor_closeAll_Ljava_io_CloseableV(Object fileDesc, Object closable) {
        throwJs();
    }

    @Alias(names = "java_io_RandomAccessFile_close_V")
    public static void java_io_RandomAccessFile_close_V(Object instance) {
    }

    @Alias(names = "java_lang_ClassLoader_checkCreateClassLoader_Ljava_lang_Void")
    public static Object java_lang_ClassLoader_checkCreateClassLoader_Ljava_lang_Void() {
        // nothing to do
        return null;
    }

}
