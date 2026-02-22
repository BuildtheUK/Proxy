package org.btuk.proxy.core.utils;

import lombok.Getter;
import org.btuk.proxy.core.scheduler.ScheduledTask;import org.btuk.proxy.core.user.User;

import java.util.UUID;

/**
 * Represents a teleport request from a player to another player.
 */
public class TeleportRequest {

    /**
     * ID of the request.
     */
    @Getter
    private final UUID id;

    /**
     * UUID of the requester.
     */
    @Getter
    private final UUID requester;

    private final User user;

    /**
     * Scheduled task to automatically remove the request after 5 minutes.
     */
    private ScheduledTask timeoutTask;

    /**
     * Indicates that the teleport request is denied.
     */
    @Getter
    private boolean denied = false;

    public TeleportRequest(User user, UUID requester) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.requester = requester;
        //this.timeoutTask = Proxy.getInstance().getServer().getScheduler().buildTask(Proxy.getInstance(), () -> user.removeTeleportRequest(this.id)).delay(5, TimeUnit.MINUTES).schedule();
    }

    public void cancel() {
        if (timeoutTask != null) {
            timeoutTask.cancel();
        }
    }

    public void denyRequest() {
        this.denied = true;
        // Create a new task to remove the request from the user's list after 1 minute.
        cancel();
        //this.timeoutTask = Proxy.getInstance().getServer().getScheduler().buildTask(Proxy.getInstance(), () -> user.removeTeleportRequest(this.id)).delay(1, TimeUnit.MINUTES).schedule();
    }
}
