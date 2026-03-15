package org.btuk.proxy.core.utils;

import org.btuk.proxy.core.config.Config;

public class Constants {

    public static String JOIN_MESSAGE;
    public static String WELCOME_MESSAGE;
    public static String RECONNECT_MESSAGE;
    public static String LEAVE_MESSAGE;

    public static final String SERVER_SENDER = "server";

    public static final String DISCORD_SENDER = "discord";

    public static void init(Config config) {
        JOIN_MESSAGE = config.getString("custom_messages.join");
        WELCOME_MESSAGE = config.getString("custom_messages.welcome");
        RECONNECT_MESSAGE = config.getString("custom_messages.reconnect");
        LEAVE_MESSAGE = config.getString("custom_messages.leave");
    }
}
