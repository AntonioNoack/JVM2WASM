package test;

public class InlineIfTest {
    int narg() {
        return 3;
    }

    Object arg(int idx) {
        return "";
    }

    public Object test(int var1) {
        return var1 <= this.narg() ? this.arg(var1) : arg(17);
    }
}
