package com.twitterbot;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.annotations.SerializedName;
import com.twitterbot.AuthenticationHelper.ApplicationOnlyAuthResponse;

public class ApiHelper
{
    private static final int APP_AUTH_REQUESTS_PER_15_MINUTE_WINDOW = 30;

    private static final int USER_AUTH_REQUESTS_PER_15_MINUTE_WINDOW = 15;

    private static final BlockingQueue<Integer> APP_AUTH_RATE_LIMIT_QUEUE = new LinkedBlockingQueue<>(
            APP_AUTH_REQUESTS_PER_15_MINUTE_WINDOW);

    private static final BlockingQueue<Integer> USER_AUTH_RATE_LIMIT_QUEUE = new LinkedBlockingQueue<>(
            USER_AUTH_REQUESTS_PER_15_MINUTE_WINDOW);

    private static final int MILLISECONDS_UNTIL_RESTART = 15 * 60 * 1000;

    static {
        initializeAppAuthRateLimitQueue();
        initializeUserAuthRateLimitQueue();
    }

    public enum UserQueryType
    {
        FOLLOWERS(ApiConfig.getInstance().twitterApiUrl + "followers/list.json", "screen_name=%s&count=%s&cursor=%s",
                "%s-followers.txt"), FRIENDS(ApiConfig.getInstance().twitterApiUrl + "friends/list.json",
                        "screen_name=%s&count=%s&cursor=%s", "%s-friends.txt");

        private final String url;

        private final String queryFormat;

        private final String filenameFormat;

        private UserQueryType(String url, String queryFormat, String filenameFormat)
        {
            this.url = url;
            this.queryFormat = queryFormat;
            this.filenameFormat = filenameFormat;
        }
    }

    public enum FriendActionType
    {
        FOLLOW(ApiConfig.getInstance().twitterApiUrl + "friendships/create.json",
                "screen_name=%s&follow=true"), UNFOLLOW(ApiConfig.getInstance().twitterApiUrl + "friendships/list.json",
                        "screen_name=%s&count=%s&cursor=%s");

        private final String url;

        private final String queryFormat;

        private FriendActionType(String url, String queryFormat)
        {
            this.url = url;
            this.queryFormat = queryFormat;
        }
    }

    public static class ApiConfig
    {
        private static final String PROPERTIES_FILENAME = "config.properties";

        private static final String TWITTER_API_URL_PROPERTY = "twitter.api.url";

        private static final String USER_QUERY_COUNT_PROPERTY = "user.query.count";

        private static final String DATA_DIRECTORY_PROPERTY = "data.directory";

        private static final ApiConfig INSTANCE = new ApiConfig();

        public static ApiConfig getInstance()
        {
            return INSTANCE;
        }

        public final String twitterApiUrl;

        public final int userQueryCount;

        public final String dataDirectory;

        private ApiConfig()
        {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILENAME)) {
                Properties props = new Properties();
                props.load(in);
                this.twitterApiUrl = props.getProperty(TWITTER_API_URL_PROPERTY);
                this.userQueryCount = Integer.parseInt(props.getProperty(USER_QUERY_COUNT_PROPERTY));
                this.dataDirectory = props.getProperty(DATA_DIRECTORY_PROPERTY);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static List<User> getFromFile(String screenName, UserQueryType userQueryType) throws IOException
    {
        List<User> users = new ArrayList<>();
        String fileName = String.format(userQueryType.filenameFormat, screenName);
        for (String line : Files.readAllLines(Paths.get(ApiConfig.getInstance().dataDirectory, fileName),
                HttpHelper.HTTP_REQUEST_CHARSET)) {
            if (!StringHelper.isNullOrEmpty(line)) {
                users.add(JsonHelper.objectFromJson(line, User.class));
            }
        }
        return users;
    }

    private static void initializeAppAuthRateLimitQueue()
    {
        for (int i = 1; i <= APP_AUTH_REQUESTS_PER_15_MINUTE_WINDOW; i++) {
            try {
                APP_AUTH_RATE_LIMIT_QUEUE.put(i);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void initializeUserAuthRateLimitQueue()
    {
        for (int i = 1; i <= USER_AUTH_REQUESTS_PER_15_MINUTE_WINDOW; i++) {
            try {
                USER_AUTH_RATE_LIMIT_QUEUE.put(i);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static List<User> queryAndSaveUsers(ApplicationOnlyAuthResponse authResponse, String screenName,
            UserQueryType userQueryType) throws IOException
    {
        HttpURLConnection urlConnection = null;
        List<User> users = new ArrayList<>();
        try {
            long cursor = -1;
            boolean endOfUsers = false;
            do {
                try {
                    System.out.println(screenName + " - getting token");
                    int currentRequestNumber = APP_AUTH_RATE_LIMIT_QUEUE.take();
                    System.out
                            .println(String.format("%s - current request number %d", screenName, currentRequestNumber));
                    System.out.println(screenName + " - running query");
                    UserQueryResponse result = (userQueryType == UserQueryType.FOLLOWERS)
                            ? getFollowers(authResponse, screenName, cursor)
                            : getFriends(authResponse, screenName, cursor);
                    if (result != null) {
                        cursor = result.nextCursor;
                        users.addAll(result.users);
                    }
                    // If the request was the last one in the rate limit queue, wait 
                    // for the current rate limit window to end and then reset the 
                    // rate limit queue before proceeding.
                    if (currentRequestNumber == APP_AUTH_REQUESTS_PER_15_MINUTE_WINDOW) {
                        Thread.sleep(MILLISECONDS_UNTIL_RESTART);
                        initializeAppAuthRateLimitQueue();
                    }
                    endOfUsers = result == null || result.nextCursor == 0;
                } catch (InterruptedException e) {
                    return null;
                }
            } while (!endOfUsers);
            List<String> usersJson = new ArrayList<>();
            for (User user : users) {
                usersJson.add(JsonHelper.objectToJson(user));
            }
            String fileName = String.format(userQueryType.filenameFormat, screenName);
            Files.write(Paths.get(ApiConfig.getInstance().dataDirectory, fileName), usersJson,
                    HttpHelper.HTTP_REQUEST_CHARSET);
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return users;
    }
    
    private static UserQueryResponse getFollowers(ApplicationOnlyAuthResponse authResponse, String screenName,
            long cursor)
    {
        UserQueryResponse result = null;
        HttpURLConnection urlConnection = null;
        try {
            String query = String.format(UserQueryType.FOLLOWERS.queryFormat, screenName,
                    ApiConfig.getInstance().userQueryCount, cursor);
            String url = String.format("%s?%s", UserQueryType.FOLLOWERS.url, query);
            urlConnection = HttpHelper.createUrlConnection(url, HttpHelper.createGetDetails(authResponse));
            String responseJson = HttpHelper.getResponseStringFromUrlConnection(urlConnection);
            if (!StringHelper.isNullOrEmpty(responseJson)) {
                result = JsonHelper.objectFromJson(responseJson, UserQueryResponse.class);
            }
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return result;
    }

    private static UserQueryResponse getFriends(ApplicationOnlyAuthResponse authResponse, String screenName, long cursor)
    {
        UserQueryResponse result = null;
        HttpURLConnection urlConnection = null;
        try {
            String query = String.format(UserQueryType.FRIENDS.queryFormat, screenName,
                    ApiConfig.getInstance().userQueryCount, cursor);
            String url = String.format("%s?%s", UserQueryType.FRIENDS.url, query);
            urlConnection = HttpHelper.createUrlConnection(url, HttpHelper.createGetDetails(authResponse));
            String responseJson = HttpHelper.getResponseStringFromUrlConnection(urlConnection);
            if (!StringHelper.isNullOrEmpty(responseJson)) {
                result = JsonHelper.objectFromJson(responseJson, UserQueryResponse.class);
            }
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return result;
    }

    public static void changeFriendStatus(ApplicationOnlyAuthResponse authResponse, String screenName,
            FriendActionType friendActionType) throws IOException
    {
        HttpURLConnection urlConnection = null;
        try {
            try {
                String actionAndScreenName = String.format("%s %s", friendActionType.toString(), screenName);
                System.out.println(actionAndScreenName + " - getting token");
                int currentRequestNumber = USER_AUTH_RATE_LIMIT_QUEUE.take();
                System.out.println(actionAndScreenName + " - current request number: " + currentRequestNumber);
                System.out.println(actionAndScreenName + " - changing friend status");
                UserQueryResponse result = (friendActionType == FriendActionType.FOLLOW)
                        ? follow(authResponse, screenName)
                        : unfollow(authResponse, screenName);
                // If the request was the last one in the rate limit queue, wait 
                // for the current rate limit window to end and then reset the 
                // rate limit queue before proceeding.
                if (currentRequestNumber == USER_AUTH_REQUESTS_PER_15_MINUTE_WINDOW) {
                    Thread.sleep(MILLISECONDS_UNTIL_RESTART);
                    initializeUserAuthRateLimitQueue();
                }
            } catch (InterruptedException e) {
            }
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }

    private static UserQueryResponse follow(ApplicationOnlyAuthResponse authResponse, String screenName)
    {
        UserQueryResponse result = null;
        HttpURLConnection urlConnection = null;
        try {
            String query = String.format(FriendActionType.FOLLOW.queryFormat, screenName);
            String url = String.format("%s?%s", FriendActionType.FOLLOW.url, query);
            urlConnection = HttpHelper.createUrlConnection(url, HttpHelper.createPostDetails(authResponse));
            String responseJson = HttpHelper.getResponseStringFromUrlConnection(urlConnection);
            if (!StringHelper.isNullOrEmpty(responseJson)) {
                result = JsonHelper.objectFromJson(responseJson, UserQueryResponse.class);
            }
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return result;
    }

    private static UserQueryResponse unfollow(ApplicationOnlyAuthResponse authResponse, String screenName)
    {
        UserQueryResponse result = null;
        HttpURLConnection urlConnection = null;
        try {
            String query = String.format(FriendActionType.UNFOLLOW.queryFormat, screenName);
            String url = String.format("%s?%s", FriendActionType.UNFOLLOW.url, query);
            urlConnection = HttpHelper.createUrlConnection(url, HttpHelper.createPostDetails(authResponse));
            String responseJson = HttpHelper.getResponseStringFromUrlConnection(urlConnection);
            if (!StringHelper.isNullOrEmpty(responseJson)) {
                result = JsonHelper.objectFromJson(responseJson, UserQueryResponse.class);
            }
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
        return result;
    }

    public static class User
    {
        @SerializedName("id")
        public long id;

        @SerializedName("name")
        public String name;

        @SerializedName("screen_name")
        public String screenName;

        @SerializedName("location")
        public String location;
    }

    private static class UserQueryResponse
    {
        @SerializedName("previous_cursor")
        public long previousCursor;

        @SerializedName("next_cursor")
        public long nextCursor;

        @SerializedName("users")
        public List<User> users;
    }
}

