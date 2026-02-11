package net.bteuk.proxy.utils;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public class Time {

    //Gets current time in milliseconds.
    public static long currentTime() {

        return System.currentTimeMillis();
    }

    //Converts milliseconds to date.
    public static String getDate(long time) {

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        formatter.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        Date date = new Date(time);
        return formatter.format(date);
    }

    //Converts milliseconds to datetime.
    public static String getDateTime(long time) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        formatter.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        Date date = new Date(time);
        return formatter.format(date);
    }

}
