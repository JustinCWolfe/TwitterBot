package com.twitterbot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.twitterbot.ApiHelper.FriendActionType;
import com.twitterbot.ApiHelper.User;
import com.twitterbot.ApiHelper.UserQueryType;
import com.twitterbot.ArgumentParsing.ParsedArguments;
import com.twitterbot.AuthenticationHelper.ApplicationOnlyAuthResponse;
import com.twitterbot.AuthenticationHelper.AuthUser;
import com.twitterbot.AuthenticationHelper.TokenType;

public class Bot
{
    private static final String APP_DESCRIPTION = "TwitterBot";

    private static final int THREAD_COUNT = 25;

    private static final ExecutorService USER_SERVICE = Executors.newFixedThreadPool(THREAD_COUNT);

    private static final ExecutorService CHANGE_FRIEND_SERVICE = Executors.newFixedThreadPool(THREAD_COUNT);

    public static void main(String[] arguments) throws Exception
    {
        ParsedArguments parsedArguments = ArgumentParsing.parseCommandLineArguments(arguments);
        if (parsedArguments == null) {
            return;
        }
        Logging.logToConsole("Starting " + APP_DESCRIPTION);
        AuthUser authUser = AuthUser.getInstance();
        Logging.logToConsole("Authentication user screen name: " + authUser.screenName);
        String authHeader = AuthenticationHelper.getApplicationOnlyAuthorizationHeader();
        Logging.logToConsole("Auth header: " + authHeader);
        ApplicationOnlyAuthResponse authResponse = AuthenticationHelper.callAuthorizationService(authHeader);
        Logging.logToConsole(
                String.format("Token type: %s\nAccess token: %s", authResponse.tokenTypeStr, authResponse.accessToken));
        if (authResponse.getTokenType() == TokenType.BEARER) {
            if (parsedArguments.runQueryForInitialData) {
                queryUsers(authResponse, authUser.screenName, UserQueryType.FOLLOWERS);
                queryUsers(authResponse, authUser.screenName, UserQueryType.FRIENDS);
            } else {
                for (String screenName : parsedArguments.screenNamesToQueryForFollowers) {
                    queryUsers(authResponse, screenName, UserQueryType.FOLLOWERS);
                }
                for (String screenName : parsedArguments.screenNamesToFollowFollowers) {
                    List<String> followers = getUsersFromFile(screenName, UserQueryType.FOLLOWERS);
                    for (String follower : followers) {
                        changeFriendStatus(authResponse, follower, FriendActionType.FOLLOW);
                    }
                }
                for (String screenName : parsedArguments.screenNamesToUnfollowFollowers) {
                    List<String> followers = getUsersFromFile(screenName, UserQueryType.FOLLOWERS);
                    for (String follower : followers) {
                        changeFriendStatus(authResponse, follower, FriendActionType.UNFOLLOW);
                    }
                }
            }
        }
        USER_SERVICE.shutdown();
        CHANGE_FRIEND_SERVICE.shutdown();
        USER_SERVICE.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        CHANGE_FRIEND_SERVICE.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        Logging.logToConsole("Finished " + APP_DESCRIPTION);
    }

    private static void queryUsers(ApplicationOnlyAuthResponse authResponse, String screenName, UserQueryType userType)
    {
        USER_SERVICE.submit(() -> {
            Logging.logToConsole(String.format("Get %s for: %s", userType.toString(), screenName));
            try {
                ApiHelper.queryAndSaveUsers(authResponse, screenName, userType);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static void changeFriendStatus(ApplicationOnlyAuthResponse authResponse, String screenName, FriendActionType friendActionType)
    {
        CHANGE_FRIEND_SERVICE.submit(() -> {
            Logging.logToConsole(String.format("%s: %s", friendActionType.toString(), screenName));
            try {
                ApiHelper.changeFriendStatus(authResponse, screenName, friendActionType);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static List<String> getUsersFromFile(String screenName, UserQueryType userQueryType) throws IOException
    {
        Logging.logToConsole("Get friends from file for: " + screenName);
        List<String> friendScreenNames = new ArrayList<>();
        for (User user : ApiHelper.getFromFile(screenName, userQueryType)) {
            friendScreenNames.add(user.screenName);
        }
        return friendScreenNames;
    }
}

