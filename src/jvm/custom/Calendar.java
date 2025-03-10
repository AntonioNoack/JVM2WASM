package jvm.custom;

import java.util.TimeZone;

public class Calendar {

    private Calendar() {
    }

    private static final Calendar INSTANCE = new Calendar();

    public static Calendar getInstance(TimeZone timeZone, Locale locale) {
        return INSTANCE;
    }

}
