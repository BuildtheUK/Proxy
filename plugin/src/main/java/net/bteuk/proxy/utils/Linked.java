package net.bteuk.proxy.utils;

import com.velocitypowered.api.scheduler.ScheduledTask;
import net.bteuk.proxy.Proxy;

import java.util.concurrent.TimeUnit;

public class Linked {

    public String uuid;
    public String token;

    public ScheduledTask task;

    public Linked(String uuid, String token) {

        this.uuid = uuid;
        this.token = token;

        //Run a delayed task to remove this from the list.
        task = Proxy.getInstance().getServer().getScheduler().buildTask(Proxy.getInstance(), () -> {

                    Proxy.getInstance().getLinking().remove(this);

                })
                .delay(5L, TimeUnit.MINUTES)
                .schedule();
    }

    public void close() {
        task.cancel();
    }
}
