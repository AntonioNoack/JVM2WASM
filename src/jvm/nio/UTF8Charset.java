package jvm.nio;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class UTF8Charset extends Charset {

    public static UTF8Charset INSTANCE = new UTF8Charset();

    private UTF8Charset() {
        super("UTF-8", null);
    }

    @Override
    public boolean contains(Charset charset) {
        return charset == this;
    }

    @Override
    public CharsetDecoder newDecoder() {
        return new UTF8CharsetDecoder(this);
    }

    @Override
    public CharsetEncoder newEncoder() {
        return new UTF8CharsetEncoder(this);
    }
}
