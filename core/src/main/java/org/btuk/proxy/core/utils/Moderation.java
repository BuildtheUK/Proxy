package org.btuk.proxy.core.utils;

import org.btuk.proxy.database.sql.GlobalSQL;

public class Moderation {

    private final GlobalSQL globalSQL;

    public Moderation(GlobalSQL globalSQL) {
        this.globalSQL = globalSQL;
    }

    //Get the reason why the player is muted.
    public String getMutedReason(String uuid) {
        return (globalSQL.getString("SELECT reason FROM moderation WHERE uuid='" + uuid + "' AND end_time>" + Time.currentTime() + " AND type='mute';"));
    }
    //Get duration of mute.
    public String getMuteDuration(String uuid) {
        long time = globalSQL.getLong("SELECT end_time FROM moderation WHERE uuid='" + uuid + "' AND end_time>" + Time.currentTime() + " AND type='mute';");
        return Time.getDateTime(time);
    }
}
