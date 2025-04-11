package jvm.custom;

@SuppressWarnings("unused")
public class Inflater {

    void end() {
    }

    void finish() {
    }

    boolean needsInput() {
        return true;
    }

    boolean finished() {
        return true;
    }

    void setInput(byte[] bytes, int i0, int i1) {
        // meaning of i0,i1 unchecked!
    }

    boolean needsDictionary() {
        return false;
    }

    int inflate(byte[] bytes, int i0, int i1) {
        return 0;
    }

}
