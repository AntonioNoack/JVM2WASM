package test;

public enum EnumTest {
    A, B;

    int test() {
        if (this == B) {
            return name().hashCode();
        } else {
            return ordinal() * 2 - 1;
        }
    }
}
