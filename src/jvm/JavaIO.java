package jvm;

import annotations.Alias;
import annotations.NoThrow;
import annotations.PureJavaScript;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import static jvm.JVMShared.readPtrAtOffset;
import static jvm.JVMShared.writePtrAtOffset;
import static jvm.ThrowJS.throwJs;
import static utils.StaticFieldOffsets.OFFSET_READER_LOCK;

public class JavaIO {

    @Alias(names = "new_java_io_InputStreamReader_Ljava_io_InputStreamV")
    public static void new_java_io_InputStreamReader_Ljava_io_InputStreamV(
            InputStreamReader reader, InputStream stream) {
        setInputStream(reader, stream);
    }

    @Alias(names = "new_java_io_InputStreamReader_Ljava_io_InputStreamLjava_nio_charset_CharsetV")
    public static void new_java_io_InputStreamReader_Ljava_io_InputStreamLjava_nio_charset_CharsetV(
            InputStreamReader reader, InputStream stream, Charset cs) {
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
    @PureJavaScript(code = "return arg0.lock;")
    private static InputStream getInputStream(InputStreamReader reader) {
        return readPtrAtOffset(reader, OFFSET_READER_LOCK);
    }

    @NoThrow
    @PureJavaScript(code = "arg0.lock = arg1;")
    private static void setInputStream(InputStreamReader reader, InputStream value) {
        writePtrAtOffset(reader, OFFSET_READER_LOCK, value);
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

    @Alias(names = "java_lang_ClassLoader_checkCreateClassLoader_Ljava_lang_Void")
    public static Object java_lang_ClassLoader_checkCreateClassLoader_Ljava_lang_Void() {
        // nothing to do
        return null;
    }

    @Alias(names = "static_java_io_BufferedInputStream_V")
    public static void static_java_io_BufferedInputStream_V() {
        // why is this forbidden??? only useless stuff is initialized, where we don't need the classes
    }

}
