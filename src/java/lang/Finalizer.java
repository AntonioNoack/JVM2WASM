package java.lang;

public class Finalizer {
    public static void call(Object instance) {
        try {
            callUnsafe(instance);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void callUnsafe(Object instance) throws Throwable {
        instance.finalize();
    }
}
