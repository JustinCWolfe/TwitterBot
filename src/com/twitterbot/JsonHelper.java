package com.twitterbot;

import java.io.IOException;
import java.io.StringReader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;

public class JsonHelper
{
    public static final Gson GSON;

    static {
        GsonBuilder gsonBuilder = new GsonBuilder();
        GSON = gsonBuilder.create();
    }

    public static <T> String objectToJson(T instance)
    {
        return GSON.toJson(instance);
    }

    public static <T> T objectFromJson(String json, Class<T> classOfT)
    {
        String trimmedJson = json.trim();
        try (JsonReader reader = new JsonReader(new StringReader(trimmedJson))) {
            reader.setLenient(true);
            return GSON.fromJson(reader, classOfT);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
