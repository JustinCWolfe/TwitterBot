package com.twitterbot;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.twitterbot.AuthenticationHelper.ApplicationOnlyAuthResponse;

public class HttpHelper
{
    private static final int WEB_SERVICE_BYTES_TO_READ = 4096;

    private static final String HTTP_GET_REQUEST_METHOD = "GET";

    private static final String HTTP_POST_REQUEST_METHOD = "POST";

    private static final String HTTP_DELETE_REQUEST_METHOD = "DELETE";

    private static final String HTTP_PUT_REQUEST_METHOD = "PUT";

    public static final Charset HTTP_REQUEST_CHARSET = StandardCharsets.UTF_8;

    private static final String AUTH_HTTP_REQUEST_CONTENT_TYPE = String.format(
            "application/x-www-form-urlencoded;charset=%s", HTTP_REQUEST_CHARSET.displayName());

    private static final String HTTP_REQUEST_CONTENT_TYPE = String.format("application/json; charset=%s",
            HTTP_REQUEST_CHARSET.displayName());

    public static RequestDetails createPostDetails()
    {
        return createPostDetails(null);
    }

    public static RequestDetails createAuthRequest(String authorizationValue)
    {
        return new RequestDetails().withMethod(HTTP_POST_REQUEST_METHOD)
                .withProperty("Authorization", authorizationValue)
                .withProperty("Content-Type", AUTH_HTTP_REQUEST_CONTENT_TYPE);
    }

    public static RequestDetails createPostDetails(ApplicationOnlyAuthResponse authResponse)
    {
        return new RequestDetails().withMethod(HTTP_POST_REQUEST_METHOD)
                .withProperty("Authorization", authResponse.getAccessTokenHeader())
                .withProperty("Content-Type", HTTP_REQUEST_CONTENT_TYPE);
    }

    public static RequestDetails createGetDetails(ApplicationOnlyAuthResponse authResponse)
    {
        return  new RequestDetails().withMethod(HTTP_GET_REQUEST_METHOD)
                .withProperty("Authorization", authResponse.getAccessTokenHeader())
                .withProperty("Content-Type", HTTP_REQUEST_CONTENT_TYPE)
                .withDoOutput(false);
    }

    public static RequestDetails createDeleteDetails(String authorizationValue)
    {
        return new RequestDetails().withMethod(HTTP_DELETE_REQUEST_METHOD)
                .withProperty("Authorization", authorizationValue)
                .withProperty("Content-Type", HTTP_REQUEST_CONTENT_TYPE)
                .withDoOutput(false);
    }

    public static RequestDetails createPutDetails(String authorizationValue)
    {
        return  new RequestDetails().withMethod(HTTP_PUT_REQUEST_METHOD)
                .withProperty("Authorization", authorizationValue)
                .withProperty("Content-Type", HTTP_REQUEST_CONTENT_TYPE);
    }

    public static HttpURLConnection createUrlConnection(String spec)
    {
        return createUrlConnection(spec, createPostDetails(null));
    }

    public static HttpURLConnection createUrlConnection(String spec, RequestDetails requestDetails)
    {
        HttpURLConnection urlConnection = null;
        try {
            URL url = new URL(spec);
            urlConnection = (HttpURLConnection) url.openConnection();
            requestDetails.configureUrlConnection(urlConnection);
        } catch (Exception e) {
            System.out.println(e);
        }
        return urlConnection;
    }

    public static <T> void sendRequestToUrlConnection(HttpURLConnection urlConnection, T request)
    {
        try {
            String requestJson = JsonHelper.objectToJson(request);
            byte[] requestBytes = requestJson.getBytes(HTTP_REQUEST_CHARSET.displayName());
            sendRequestToUrlConnection(urlConnection, requestBytes);
        } catch (UnsupportedEncodingException e) {
            System.out.println(e);
        }
    }

    public static <T> void sendRequestToUrlConnection(HttpURLConnection urlConnection, byte[] data)
    {
        try (OutputStream writer = new BufferedOutputStream(urlConnection.getOutputStream())) {
            writer.write(data);
            writer.flush();
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    public static int getResponseCodeFromUrlConnection(HttpURLConnection urlConnection)
    {
        return validateAndGetResponseCode(urlConnection);
    }

    public static String getResponseStringFromUrlConnection(HttpURLConnection urlConnection)
    {
        validateAndGetResponseCode(urlConnection);
        String response = null;
        try {
            byte[] responseBytes = getResponseBytesFromUrlConnection(urlConnection);
            response = new String(responseBytes, HTTP_REQUEST_CHARSET.displayName());
        } catch (UnsupportedEncodingException e) {
            // This should not happen since we are using a standard character set.
            throw new RuntimeException(e);
        }
        return response;
    }

    public static <T> T getResponseFromUrlConnection(HttpURLConnection urlConnection, Class<T> clazz)
    {
        return JsonHelper.objectFromJson(getResponseStringFromUrlConnection(urlConnection), clazz);
    }

    public static byte[] getResponseBytesFromUrlConnection(HttpURLConnection urlConnection)
    {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream reader = new BufferedInputStream(urlConnection.getInputStream())) {
            byte[] buffer = new byte[WEB_SERVICE_BYTES_TO_READ];
            int bytesRead = 0;
            while (true) {
                bytesRead = reader.read(buffer);
                if (bytesRead == -1) {
                    break;
                }
                baos.write(buffer, 0, bytesRead);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return baos.toByteArray();
    }

    public static String getResponseJsonFromUrlConnection(HttpURLConnection urlConnection)
    {
        String response = null;
        try {
            byte[] responseBytes = getResponseBytesFromUrlConnection(urlConnection);
            response = new String(responseBytes, HTTP_REQUEST_CHARSET.displayName());
        } catch (Exception e) {
            System.out.println(e);
        }
        return response;
    }

    private static int validateAndGetResponseCode(HttpURLConnection urlConnection)
    {
        try {
            return urlConnection.getResponseCode();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class RequestDetails
    {
        private boolean doInput = true;

        private boolean doOutput = true;

        private String requestMethod = HTTP_POST_REQUEST_METHOD;

        private final Map<String, String> requestProperties = new HashMap<>();

        public RequestDetails withMethod(String requestMethod)
        {
            this.requestMethod = requestMethod;
            return this;
        }

        public RequestDetails withDoInput(boolean doInput)
        {
            this.doInput = doInput;
            return this;
        }

        public RequestDetails withDoOutput(boolean doOutput)
        {
            this.doOutput = doOutput;
            return this;
        }

        public RequestDetails withProperty(String name, String value)
        {
            requestProperties.put(name, value);
            return this;
        }

        public void configureUrlConnection(HttpURLConnection urlConnection)
        {
            try {
                urlConnection.setDoInput(doInput);
                urlConnection.setDoOutput(doOutput);
                urlConnection.setRequestMethod(requestMethod);
                for (Map.Entry<String, String> propertyEntry : requestProperties.entrySet()) {
                    urlConnection.setRequestProperty(propertyEntry.getKey(), propertyEntry.getValue());
                }
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }
}

