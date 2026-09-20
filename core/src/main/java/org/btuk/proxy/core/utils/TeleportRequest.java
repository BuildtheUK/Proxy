package org.btuk.proxy.core.utils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.btuk.proxy.core.scheduler.ScheduledTask;
import org.btuk.proxy.core.scheduler.Scheduler;
import org.btuk.proxy.core.scheduler.TaskStatus;
import org.btuk.proxy.core.user.User;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Represents a teleport request from a player to another player.
 */
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class TeleportRequest {

    private final Scheduler scheduler;

    /**
     * ID of the request.
     */
    @Getter
    @EqualsAndHashCode.Include
    private final UUID id;

    /**
     * Requester.
     */
    @Getter
    private final User requester;

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

    public TeleportRequest(Scheduler scheduler, User user, User requester) {
        this.scheduler = scheduler;
        this.id = UUID.randomUUID();
        this.user = user;
        this.requester = requester;
        this.timeoutTask = scheduler.createDelayedTask(() -> user.removeTeleportRequest(this.id, this.requester, true),5, TimeUnit.MINUTES);
    }

    public void cancel() {
        if (timeoutTask != null && timeoutTask.getStatus() == TaskStatus.SCHEDULED) {
            timeoutTask.cancel();
        }
    }

    public void acceptRequest() {
        cancel();
        user.removeTeleportRequest(this.id, this.requester, false);
    }

    public void denyRequest() {
        this.denied = true;
        // Create a new task to remove the request from the user's list after 5 minutes.
        cancel();
        this.timeoutTask = scheduler.createDelayedTask(() -> user.removeTeleportRequest(this.id, this.requester, false), 5, TimeUnit.MINUTES);
    }
}
