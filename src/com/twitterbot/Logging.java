package com.twitterbot;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Logging
{
    private static final String TIMESTAMP_FORMAT = "[%s] ";

    static void logToConsole(String message)
    {
        String timestamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
        System.out.println(String.format(TIMESTAMP_FORMAT, timestamp) + message);
    }
}

