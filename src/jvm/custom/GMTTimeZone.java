package jvm.custom;

import java.util.Date;
import java.util.TimeZone;

public class GMTTimeZone extends TimeZone {

    private GMTTimeZone() {
    }

    @Override
    public int getOffset(int era, int year, int month, int day, int dayOfWeek, int millis) {
        return 0;
    }

    @Override
    public void setRawOffset(int i) {

    }

    @Override
    public int getRawOffset() {
        return 0;
    }

    @Override
    public boolean useDaylightTime() {
        return false;
    }

    @Override
    public boolean inDaylightTime(Date date) {
        return false;
    }

    public static final GMTTimeZone INSTANCE = new GMTTimeZone();

}
