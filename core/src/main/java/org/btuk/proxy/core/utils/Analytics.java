package org.btuk.proxy.core.utils;

import org.btuk.proxy.database.sql.GlobalSQL;

import org.btuk.proxy.core.user.CoreUserManager;
import org.btuk.proxy.core.user.User;
import org.btuk.proxy.core.scheduler.ScheduledTask;
import org.btuk.proxy.core.scheduler.Scheduler;

import java.util.concurrent.TimeUnit;

/**
 * This class handles all the statistics gathered by the server.
 * The reason for gathering this information is so we can tell what the behaviour of a player is like
 * and tailor the experience of the server to better accommodate their style of play.
 */
public final class Analytics {

    private final CoreUserManager userManager;

    private final GlobalSQL globalSQL;

    private final ScheduledTask saveTask;

    public Analytics(CoreUserManager userManager, GlobalSQL globalSQL, Scheduler scheduler) {
        this.userManager = userManager;
        this.globalSQL = globalSQL;
        saveTask = scheduler.createRepeatingTask(this::saveAll, 0L, 1L, TimeUnit.MINUTES);
    }

    // Log the player count, which is the number of online users.
    public void logPlayerCount() {
        globalSQL.update("INSERT INTO player_count(log_time,players) VALUES(" + Time.currentTime() + "," + userManager.countOnlineUsers() + ");");
    }

    public void addMessage(String uuid, String date) {
        //If the date doesn't exist, create it.
        if (globalSQL.hasRow("SELECT uuid FROM statistics WHERE uuid='" + uuid + "' AND on_date='" + date + "';")) {
            globalSQL.update("UPDATE statistics SET messages=messages+1 WHERE uuid='" + uuid + "' AND on_date='" + date + "';");
        } else {
            globalSQL.update("INSERT INTO statistics(uuid,on_date,messages) VALUES('" + uuid + "','" + date + "',1);");
        }
    }

    //Saves the online-time of player from previous save till now.
    public void save(User user, String date, long time) {
        if (user.isOnline() && !user.isAfk()) {
            //Get time difference from the previous save and set the previous save to the current time.
            long time_diff = time - user.last_time_log;
            user.last_time_log = time;

            //Add time difference to the active session.
            user.active_time += time_diff;

            //Add time to the database, if date doesn't exist, create it.
            if (globalSQL.hasRow("SELECT uuid FROM statistics WHERE uuid='" + user.getUuid() + "' AND on_date='" + date + "';")) {
                globalSQL.update("UPDATE statistics SET playtime=playtime+" + time_diff + " WHERE uuid='" + user.getUuid() + "' AND on_date='" + date + "';");
            } else {
                globalSQL.update("INSERT INTO statistics(uuid,on_date,playtime) VALUES('" + user.getUuid() + "','" + date + "'," + time_diff + ");");
            }
        }
    }

    //Saves the online-time of all online players from the previous ave till now.
    public void saveAll() {
        //Get current time.
        long time = org.btuk.proxy.core.utils.Time.currentTime();

        //Get the current date.
        String date = org.btuk.proxy.core.utils.Time.getDate(time);

        //Iterate through online users.
        //If the player is afk, skip.
        userManager.runForEach(user -> save(user, date, time));
    }

    public void shutdown() {
        saveTask.cancel();
        saveAll();
    }
}
