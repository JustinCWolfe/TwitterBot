package com.twitterbot;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.RandomStringUtils;

import com.google.gson.annotations.SerializedName;

public class AuthenticationHelper
{
    public enum TokenType
    {
        BEARER("Bearer %s");

        private final String headerFormat;

        private TokenType(String headerFormat)
        {
            this.headerFormat = headerFormat;
        }

        public static TokenType getTokenTypeFromJsonValue(String jsonValue)
        {
            return Enum.valueOf(TokenType.class, jsonValue.toUpperCase());
        }
    }

    public static class AuthUser
    {
        private static final String PROPERTIES_FILENAME = "authuser.properties";

        private static final String SCREEN_NAME_PROPERTY = "screen.name";

        private static final String ACCESS_TOKEN_PROPERTY = "access.token";

        private static final String ACCESS_TOKEN_SECRET_PROPERTY = "access.token.secret";

        private static final String CONSUMER_KEY_PROPERTY = "consumer.key";

        private static final String CONSUMER_SECRET_PROPERTY = "consumer.secret";
        
        private static final AuthUser INSTANCE = new AuthUser();

        public static AuthUser getInstance()
        {
            return INSTANCE;
        }

        public final String screenName;

        public final String accessToken;

        public final String accessTokenSecret;

        public final String consumerKey;

        public final String consumerSecret;

        private AuthUser()
        {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILENAME)) {
                Properties props = new Properties();
                props.load(in);
                this.screenName = props.getProperty(SCREEN_NAME_PROPERTY);
                this.accessToken = props.getProperty(ACCESS_TOKEN_PROPERTY);
                this.accessTokenSecret = props.getProperty(ACCESS_TOKEN_SECRET_PROPERTY);
                this.consumerKey = props.getProperty(CONSUMER_KEY_PROPERTY);
                this.consumerSecret = props.getProperty(CONSUMER_SECRET_PROPERTY);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final String APP_ONLY_AUTHORIZATION_HEADER_FORMAT = "Basic %s";

    private static final byte[] APP_ONLY_BODY = "grant_type=client_credentials"
            .getBytes(HttpHelper.HTTP_REQUEST_CHARSET);

    public static final String AUTH_URL = "https://api.twitter.com/oauth2/token";

    private static final String OATH_AUTHORIZATION_HEADER_FORMAT = "OAuth "
            + "oauth_consumer_key=\"%s\", "
            + "oauth_nonce=\"%s\", "
            + "oauth_signature=\"%s\", "
            + "oauth_signature_method=\"HMAC-SHA1\", "
            + "oauth_timestamp=\"%s\", "
            + "oauth_token=\"%s\", "
            + "oauth_version=\"1.0\"";

    private static final String BEARER_TOKEN_CREDENTIAL_FORMAT = "%s:%s";

    private static final AtomicReference<String> OATH_SIGNATURE = new AtomicReference<>();

    private static String getBase64EBearerToken() throws Exception
    {
        AuthUser authUser = AuthUser.getInstance();
        String encodedConsumerKey = URLEncoder.encode(authUser.consumerKey,
                HttpHelper.HTTP_REQUEST_CHARSET.displayName());
        String encodedConsumerSecret = URLEncoder.encode(authUser.consumerSecret,
                HttpHelper.HTTP_REQUEST_CHARSET.displayName());
        String bearerTokenCredentials = String.format(BEARER_TOKEN_CREDENTIAL_FORMAT, encodedConsumerKey,
                encodedConsumerSecret);

        byte[] bearerTokenCredentialsBytes = bearerTokenCredentials.getBytes(HttpHelper.HTTP_REQUEST_CHARSET);
        byte[] bearerTokenCredentialsEncodedBytes = Base64.getEncoder().encode(bearerTokenCredentialsBytes);
        return new String(bearerTokenCredentialsEncodedBytes);
    }

    public static String getApplicationOnlyAuthorizationHeader() throws Exception
    {
        String base64BearerTokenCredentials = getBase64EBearerToken();
        return String.format(APP_ONLY_AUTHORIZATION_HEADER_FORMAT, base64BearerTokenCredentials);
    }

    public static byte[] getApplicationOnlyBody()
    {
        return APP_ONLY_BODY;
    }

    private static String getNonce()
    {
        return RandomStringUtils.randomAlphanumeric(32);
    }

    public static String getOathAuthorizationHeader() throws Exception
    {
        AuthUser authUser = AuthUser.getInstance();
        OATH_SIGNATURE.compareAndSet(null,
                HmacSignatureHelper.calculateRFC2104HMAC(authUser.consumerKey, authUser.consumerSecret));
        String nonce = getNonce();
        String timestamp = Long.toString(System.currentTimeMillis());
        String base64BearerTokenCredentials = getBase64EBearerToken();
        return String.format(OATH_AUTHORIZATION_HEADER_FORMAT, authUser.consumerKey, nonce, OATH_SIGNATURE.get(),
                timestamp, base64BearerTokenCredentials);
    }

    public static ApplicationOnlyAuthResponse callAuthorizationService(String authHeader)
    {
        HttpURLConnection urlConnection = HttpHelper.createUrlConnection(AuthenticationHelper.AUTH_URL,
                HttpHelper.createAuthRequest(authHeader));
        try {
            HttpHelper.sendRequestToUrlConnection(urlConnection, AuthenticationHelper.getApplicationOnlyBody());
            String responseJson = HttpHelper.getResponseStringFromUrlConnection(urlConnection);
            return JsonHelper.objectFromJson(responseJson, ApplicationOnlyAuthResponse.class);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    public static class ApplicationOnlyAuthResponse
    {
        @SerializedName("token_type")
        public String tokenTypeStr;

        @SerializedName("access_token")
        public String accessToken;

        private TokenType tokenType;

        public TokenType getTokenType()
        {
            if (tokenType == null) {
                tokenType = TokenType.getTokenTypeFromJsonValue(tokenTypeStr);
            }
            return tokenType;
        }

        public String getAccessTokenHeader()
        {
            return String.format(tokenType.headerFormat, accessToken);
        }
    }
}

