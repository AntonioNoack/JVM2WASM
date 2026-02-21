package jvm;

import annotations.Alias;
import annotations.NoThrow;
import jvm.nio.UTF8Charset;
import jvm.nio.NioFileSystemProvider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.file.spi.FileSystemProvider;

import static jvm.JavaLang.Float_floatToRawIntBits_FI;

public class JavaNIO {

    @Alias(names = "java_nio_charset_Charset_defaultCharset_Ljava_nio_charset_Charset")
    public static Charset Charset_defaultCharset() {
        return Charset_forName("UTF-8");
    }

    @Alias(names = "java_nio_file_FileSystemsXDefaultFileSystemHolder_getDefaultProvider_Ljava_nio_file_spi_FileSystemProvider")
    public static FileSystemProvider FileSystemsXDefaultFileSystemHolder_getDefaultProvider() {
        return NioFileSystemProvider.INSTANCE;
    }

    @Alias(names = "java_nio_ByteOrder_nativeOrder_Ljava_nio_ByteOrder")
    public static ByteOrder ByteOrder_nativeOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Alias(names = "java_nio_Bits_byteOrder_Ljava_nio_ByteOrder")
    public static ByteOrder Bits_byteOrder() {
        return ByteOrder_nativeOrder();
    }

    @Alias(names = "java_nio_ByteBuffer_allocateDirect_ILjava_nio_ByteBuffer")
    public static ByteBuffer ByteBuffer_allocateDirect(int size) {
        return ByteBuffer.allocate(size);
    }

    @Alias(names = "java_nio_HeapByteBuffer_isDirect_Z")
    public static boolean ByteBuffer_isDirect_Z(ByteBuffer self) {
        return true;
    }

    @NoThrow
    @Alias(names = "java_nio_charset_Charset_atBugLevel_Ljava_lang_StringZ")
    public static boolean Charset_atBugLevel(String version) {
        return false;
    }

    @NoThrow
    @Alias(names = "java_nio_FloatBuffer_equals_FFZ")
    public static boolean java_nio_FloatBuffer_equals_FFZ(float a, float b) {
        return Float_floatToRawIntBits_FI(a) == Float_floatToRawIntBits_FI(b);
    }

    @Alias(names = "java_nio_charset_Charset_forName_Ljava_lang_StringLjava_nio_charset_Charset")
    public static Charset Charset_forName(String name) {
        // todo implement this properly
        return UTF8Charset.INSTANCE;
    }

}
