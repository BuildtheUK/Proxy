package org.btuk.proxy.scheduler;

import org.btuk.proxy.core.scheduler.ScheduledTask;
import org.btuk.proxy.core.scheduler.TaskStatus;

public class ProxyScheduledTask implements ScheduledTask {

    private final com.velocitypowered.api.scheduler.ScheduledTask task;

    public ProxyScheduledTask(com.velocitypowered.api.scheduler.ScheduledTask task) {
        this.task = task;
    }


    @Override
    public void cancel() {
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public TaskStatus getStatus() {
        return TaskStatus.valueOf(task.status().name());
    }
}
