package org.btuk.proxy.core.utils;

import org.btuk.proxy.database.sql.GlobalSQL;

public class Moderation {

    private final GlobalSQL globalSQL;

    public Moderation(GlobalSQL globalSQL) {
        this.globalSQL = globalSQL;
    }

    public void mute(String uuid, long end_time, String reason) {
        long time = Time.currentTime();

        // If the player is already muted, end the old mute.
        if (isMuted(uuid)) {
            globalSQL.update("UPDATE moderation SET end_time=" + time + " WHERE uuid='" + uuid + "' AND end_time>" + time + " AND type='mute';");
        }
        globalSQL.update("INSERT INTO moderation(uuid,start_time,end_time,reason,type) VALUES('" + uuid + "'," + time + "," + end_time + ",'" + reason + "','mute');");
    }

    // If the player is currently muted, return true.
    public boolean isMuted(String uuid) {
        return (globalSQL.hasRow("SELECT uuid FROM moderation WHERE uuid='" + uuid + "' " +
            "AND end_time>" + Time.currentTime() + " AND type='mute';"));
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
