package net.bteuk.proxy.utils;

import net.bteuk.proxy.Proxy;
import net.bteuk.proxy.User;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This class handles all the statistics gathered by the server.
 * The reason for gathering this information is, so we can tell what the behaviour of a player is like
 * and tailor the experience of the server to better accommodate their style of play.
 */
public final class Analytics {

    public static void enableAnalytics(Proxy instance) {
        instance.getServer().getScheduler().buildTask(instance, Analytics::saveAll)
                .repeat(1L, TimeUnit.MINUTES)
                .schedule();
    }

    // Log the player count, which is the number of online users.
    public static void logPlayerCount(List<User> users) {
        Proxy.getInstance().getGlobalSQL().update("INSERT INTO player_count(log_time,players) VALUES(" + Time.currentTime() + "," + users.stream().filter(User::isOnline).count() + ");");
    }

    public static void addMessage(String uuid, String date) {
        //If date doesn't exist, create it.
        if (Proxy.getInstance().getGlobalSQL().hasRow("SELECT uuid FROM statistics WHERE uuid='" + uuid + "' AND on_date='" + date + "';")) {
            Proxy.getInstance().getGlobalSQL().update("UPDATE statistics SET messages=messages+1 WHERE uuid='" + uuid + "' AND on_date='" + date + "';");
        } else {
            Proxy.getInstance().getGlobalSQL().update("INSERT INTO statistics(uuid,on_date,messages) VALUES('" + uuid + "','" + date + "',1);");
        }
    }

    //Saves the online-time of player from previous save till now.
    public static void save(User user, String date, long time) {
        if (user.isOnline() && !user.isAfk()) {
            //Get time difference from previous save and set previous save to current time.
            long time_diff = time - user.last_time_log;
            user.last_time_log = time;

            //Add time difference to active session.
            user.active_time += time_diff;

            //Add time to database, if date doesn't exist, create it.
            if (Proxy.getInstance().getGlobalSQL().hasRow("SELECT uuid FROM statistics WHERE uuid='" + user.getUuid() + "' AND on_date='" + date + "';")) {
                Proxy.getInstance().getGlobalSQL().update("UPDATE statistics SET playtime=playtime+" + time_diff + " WHERE uuid='" + user.getUuid() + "' AND on_date='" + date + "';");
            } else {
                Proxy.getInstance().getGlobalSQL().update("INSERT INTO statistics(uuid,on_date,playtime) VALUES('" + user.getUuid() + "','" + date + "'," + time_diff + ");");
            }
        }
    }

    //Saves the online-time of all online players from the previous ave till now.
    public static void saveAll() {

        //Get current time.
        long time = Time.currentTime();

        //Get current date.
        String date = Time.getDate(time);

        //Iterate through online users.
        //If player is afk, skip.
        for (User user : Proxy.getInstance().getUserManager().getUsers()) {
            save(user, date, time);
        }
    }
}
