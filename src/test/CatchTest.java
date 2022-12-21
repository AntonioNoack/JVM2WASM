package test;

public class CatchTest {

    static void catcher0() {
        try {
            catcher();
        } finally {
            catcher2();
        }
    }

    static void nothing(){}

    static void catcher() {
        try {
            throw new IllegalArgumentException();
        } finally {
            nothing();
        }
    }

    static AssertionError y;
    static void class_enclosingMethodInfo(boolean x) {
        try {
            if (x)
                throw y;
        } catch (Throwable ignored) {

        }
    }

    static void catcher2() {
        try {
            throw new IllegalArgumentException();
        } catch (Exception e) {
            // e.printStackTrace();
        }
    }

    static void catcher3() {
        try {
            throw new IllegalArgumentException();
        } catch (Throwable e) {
            // e.printStackTrace();
        }
    }
}
