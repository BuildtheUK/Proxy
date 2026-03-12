package org.btuk.proxy.core.discord;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.btuk.proxy.core.scheduler.ScheduledTask;
import org.btuk.proxy.core.scheduler.Scheduler;

public class Linked {

    public String uuid;
    public String token;

    public ScheduledTask task;

    public Linked(Scheduler scheduler, List<Linked> linking, String uuid, String token) {

        this.uuid = uuid;
        this.token = token;

        //Run a delayed task to remove this from the list.
        task = scheduler.createDelayedTask(() -> linking.remove(this), 5L, TimeUnit.MINUTES);
    }

    public void close() {
        task.cancel();
    }
}
