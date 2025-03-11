package jvm.nio;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

public class UTF8CharsetEncoder extends CharsetEncoder {

    public UTF8CharsetEncoder(Charset charset) {
        super(charset, 1f, 6f);
    }

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
}
