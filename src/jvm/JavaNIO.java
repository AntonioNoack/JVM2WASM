package jvm;

import annotations.Alias;
import annotations.NoThrow;
import jvm.nio.NioFileSystemProvider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.file.spi.FileSystemProvider;

public class JavaNIO {

    private static Charset cs;

    @Alias(name = "java_nio_charset_Charset_defaultCharset_Ljava_nio_charset_Charset")
    public static Charset Charset_defaultCharset() {
        if (cs == null) forName(null);
        return cs;
    }

    private static FileSystemProvider provider = null;

    @Alias(name = "java_nio_file_FileSystemsXDefaultFileSystemHolder_getDefaultProvider_Ljava_nio_file_spi_FileSystemProvider")
    public static FileSystemProvider FileSystemsXDefaultFileSystemHolder_getDefaultProvider() {
        if (provider == null) provider = new NioFileSystemProvider();
        return provider;
    }

    @Alias(name = "java_nio_ByteOrder_nativeOrder_Ljava_nio_ByteOrder")
    public static ByteOrder ByteOrder_nativeOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }

    @Alias(name = "java_nio_Bits_byteOrder_Ljava_nio_ByteOrder")
    public static ByteOrder Bits_byteOrder() {
        return ByteOrder_nativeOrder();
    }

    @Alias(name = "java_nio_ByteBuffer_allocateDirect_ILjava_nio_ByteBuffer")
    public static ByteBuffer ByteBuffer_allocateDirect(int size) {
        return ByteBuffer.allocate(size);
    }

    @NoThrow
    @Alias(name = "java_nio_charset_Charset_atBugLevel_Ljava_lang_StringZ")
    public static boolean Charset_atBugLevel(String version) {
        return false;
    }

    @NoThrow
    @Alias(name = "java_io_File_toPath_Ljava_nio_file_Path")
    public static Object java_io_File_toPath_Ljava_nio_file_Path(Object file) {
        return file;
    }

    @Alias(name = "java_nio_charset_Charset_forName_Ljava_lang_StringLjava_nio_charset_Charset")
    public static Charset forName(String name) {
        // todo implement this properly
        if (cs == null) cs = new Charset("UTF-8", null) {
            @Override
            public boolean contains(Charset charset) {
                return charset == this;
            }

            @Override
            public CharsetDecoder newDecoder() {
                // float averageCharsPerByte, float maxCharsPerByte
                return new CharsetDecoder(this, 1f, 1f) {
                    @Override
                    protected CoderResult decodeLoop(ByteBuffer byteBuffer, CharBuffer charBuffer) {
                        while (byteBuffer.hasRemaining()) {
                            if (!charBuffer.hasRemaining()) {
                                return CoderResult.OVERFLOW;
                            }
                            byte b = byteBuffer.get();
                            charBuffer.put((char) b);
                        }
                        return CoderResult.UNDERFLOW;
                    }
                };
            }

            @Override
            public CharsetEncoder newEncoder() {
                // float averageBytesPerChars, float maxBytesPerChar
                return new CharsetEncoder(this, 1f, 6f) {
                    @Override
                    public boolean isLegalReplacement(byte[] bytes) {
                        return true; // idc, would crash otherwise...
                    }

                    @Override
                    protected CoderResult encodeLoop(CharBuffer charBuffer, ByteBuffer byteBuffer) {
                        while (charBuffer.hasRemaining()) {
                            if (!byteBuffer.hasRemaining()) {
                                return CoderResult.OVERFLOW;
                            }
                            char b = charBuffer.get();
                            byteBuffer.put((byte) b);
                        }
                        return CoderResult.UNDERFLOW;
                    }
                };
            }
        };
        return cs;
    }

}
