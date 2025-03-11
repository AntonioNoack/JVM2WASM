package jvm.nio;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;

public class UTF8CharsetDecoder extends CharsetDecoder {

    public UTF8CharsetDecoder(Charset charset) {
        super(charset, 1f, 1f);
    }

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
}
