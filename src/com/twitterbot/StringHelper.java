package com.twitterbot;

public class StringHelper
{
    public static final String EMPTY = "";

    public static boolean isNullOrEmpty(String input)
    {
        return (input == null || EMPTY.equals(input));
    }
}

