package jvm.custom;

public class SimpleDateFormat {
    private final String format;

    public SimpleDateFormat(String format) {
        this.format = format;
    }

    @Override
    public String toString() {
        return format;
    }
}
