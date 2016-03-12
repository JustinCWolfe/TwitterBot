package com.twitterbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArgumentParsing
{
    public static ParsedArguments parseCommandLineArguments(String[] arguments)
    {
        if (arguments.length == 0) {
            StringBuilder usage = new StringBuilder();
            usage.append("Usage: <exe> <screen name>\n");
            usage.append("\t--initial\n");
            usage.append("\t--query=<screen name1, screen name2, etc>\n");
            usage.append("\t(NOT YET SUPPORTED) --follow=<screen name1, screen name2, etc>\n");
            usage.append("\t(NOT YET SUPPORTED) --unfollow=<screen name1, screen name2, etc>");
            System.out.println(usage.toString());
            return null;
        }
        String authenticatedScreenName = arguments[0];
        boolean runQueryForInitialData = false;
        Pattern queryPattern = Pattern.compile("--query=(.*)");
        Pattern followPattern = Pattern.compile("--follow=(.*)");
        Pattern unfollowPattern = Pattern.compile("--unfollow=(.*)");
        List<String> screenNamesToQueryForFollowers = new ArrayList<>();
        List<String> screenNamesToFollow = new ArrayList<>();
        List<String> screenNamesToUnfollow = new ArrayList<>();
        for (int argIndex = 1; argIndex < arguments.length; argIndex++) {
            String argument = arguments[argIndex];
            if (!StringHelper.isNullOrEmpty(argument)) {
                Matcher queryMatcher = queryPattern.matcher(argument);
                Matcher followMatcher = followPattern.matcher(argument);
                Matcher unfollowMatcher = unfollowPattern.matcher(argument);
                if (argument.equals("--initial")) {
                    runQueryForInitialData = true;
                } else if (queryMatcher.matches()) {
                    String allScreenNames = queryMatcher.group(1);
                    String[] screenNames = allScreenNames.split(",");
                    screenNamesToQueryForFollowers.addAll(Arrays.asList(screenNames));
                } else if (followMatcher.matches()) {
                    String allScreenNames = followMatcher.group(1);
                    String[] screenNames = allScreenNames.split(",");
                    screenNamesToFollow.addAll(Arrays.asList(screenNames));
                    Logging.logToConsole("This option is not yet supported");
                    return null;
                } else if (unfollowMatcher.matches()) {
                    String allScreenNames = unfollowMatcher.group(1);
                    String[] screenNames = allScreenNames.split(",");
                    screenNamesToUnfollow.addAll(Arrays.asList(screenNames));
                    Logging.logToConsole("This option is not yet supported");
                    return null;
                }
            }
        }
        return new ParsedArguments(authenticatedScreenName, runQueryForInitialData, screenNamesToQueryForFollowers,
                screenNamesToFollow, screenNamesToUnfollow);
    }

    static class ParsedArguments
    {
        final String authenticatedScreenName;

        final boolean runQueryForInitialData;

        final List<String> screenNamesToQueryForFollowers;

        final List<String> screenNamesToFollowFollowers;

        final List<String> screenNamesToUnfollowFollowers;

        ParsedArguments(String authenticatedScreenName, boolean runQueryForInitialData,
                List<String> screenNamesToQueryForFollowers, List<String> screenNamesToFollowFollowers,
                List<String> screenNamesToUnfollowFollowers)
        {
            this.authenticatedScreenName = authenticatedScreenName;
            this.runQueryForInitialData = runQueryForInitialData;
            this.screenNamesToQueryForFollowers = screenNamesToQueryForFollowers;
            this.screenNamesToFollowFollowers = screenNamesToFollowFollowers;
            this.screenNamesToUnfollowFollowers = screenNamesToUnfollowFollowers;
        }
    }
}

