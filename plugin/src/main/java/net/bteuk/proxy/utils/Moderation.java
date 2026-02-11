package net.bteuk.proxy.utils;

import net.bteuk.proxy.Proxy;

public class Moderation {
    //Get reason why player is muted.
    public static String getMutedReason(String uuid) {
        return (Proxy.getInstance().getGlobalSQL().getString("SELECT reason FROM moderation WHERE uuid='" + uuid + "' AND end_time>" + Time.currentTime() + " AND type='mute';"));
    }
    //Get duration of mute.
    public static String getMuteDuration(String uuid) {
        long time = Proxy.getInstance().getGlobalSQL().getLong("SELECT end_time FROM moderation WHERE uuid='" + uuid + "' AND end_time>" + Time.currentTime() + " AND type='mute';");
        return Time.getDateTime(time);
    }

}
