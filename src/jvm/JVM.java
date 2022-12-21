package jvm;

import static jvm.JVM32.objectOverhead;
import static jvm.JVM32.read32;
import static jvm.JavaLang.getAddr;
import static jvm.JavaLang.ptrTo;

public class JVM {
    public static boolean byteStrings = ptrTo(read32(getAddr("") + objectOverhead)) instanceof byte[];
}
