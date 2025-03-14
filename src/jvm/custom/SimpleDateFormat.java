package jvm.custom;

import java.util.Date;

public class SimpleDateFormat {
    private final String format;

    public SimpleDateFormat(String format) {
        this.format = format;
    }

    @Override
    public String toString() {
        return format;
    }

    public String format(Date date) {
        return Long.toString(date.getTime());
    }
}
