package net.bteuk.proxy.utils;

import net.bteuk.proxy.Proxy;

public class Constants {

    public static final String JOIN_MESSAGE = Proxy.getInstance().getConfig().getString("custom_messages.join");
    public static final String WELCOME_MESSAGE = Proxy.getInstance().getConfig().getString("custom_messages.welcome");
    public static final String RECONNECT_MESSAGE = Proxy.getInstance().getConfig().getString("custom_messages.reconnect");
    public static final String LEAVE_MESSAGE = Proxy.getInstance().getConfig().getString("custom_messages.leave");

    public static final String SERVER_SENDER = "server";

    public static final String DISCORD_SENDER = "discord";

}
