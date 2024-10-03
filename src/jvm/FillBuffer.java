package jvm;

/**
 * provides a char[] for creating strings in JavaScript/WASM
 * */
public class FillBuffer {
    private static char[] buffer;
    public static char[] getBuffer(){
        if (buffer == null) buffer = new char[1024];
        return buffer;
    }
}
