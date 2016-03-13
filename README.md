# TwitterBot
Multithreaded bot that interacts with the Twitter API.

## Description

TwitterBot does the following:

1. Obtain lists of followers for passed-in @screen_names.
2. Obtain lists of friends for passed-in @screen_names.
3. Follow all users for passed-in @screen_names (TODO).
4. Unfollow all users for passed-in @screen_names (TODO).

Note that the bot respects the [Twitter API rate limits] (https://dev.twitter.com/rest/public/rate-limiting).

## Configuration

In the project resources directory, there are 2 properties files that control how the TwitterBot runs.  

1. example.authuser.properties.  Copy this file to authuser.properties.  Edit authuser.properties and enter information for your Twitter API account (the credentials for the Twitter account for which the TwitterBot will be running).  
  * See [example.authuser.properties](../master/resources/example.authuser.properties). 

2. example.config.properties.  Copy this file to config.properties.  Edit config.properties and enter information to control information such as the Twitter API url, the paging size for Twitter API queries and the data directory where files containing the results of the TwitterBot's queries will be written. 
  * See [example.config.properties](../master/resources/example.config.properties). 

## Usage

You can either run the TwitterBot from within your editor or build a jar for it and run from the command line.  Note that if you plan to run from within your editor, you will need to specify the parameters for the execution configuration.

Usage: \<exe\>  
    --initial  
    --query=screen name1, screen name2, etc  
    --follow=screen name1, screen name2, etc (NOT YET SUPPORTED)  
    --unfollow=screen name1, screen name2, etc (NOT YET SUPPORTED)  

\<exe\> 
 * Name of the TwitterBot executable.

\-\-initial
  * This is a special mode that will query and save to file the followers and friends of the authentication user.
 
\-\-query=\<screen name1, screen name2, etc\>
  * This will query and save to file the followers of each of the passed-in screen names.  One thread will be used for each screen name.  
 
\-\-follow=\<screen name1, screen name2, etc\>
  * This will follow all users of the passed-in screen names.  One thread will be used for each screen name.
  * Note that this is not yet supported.

\-\-unfollow=\<screen name1, screen name2, etc\>
  * This will unfollow all users of the passed-in screen names.  One thread will be used for each screen name.
  * Note that this is not yet supported.
 
## License

[MIT License](../blob/master/LICENSE)

