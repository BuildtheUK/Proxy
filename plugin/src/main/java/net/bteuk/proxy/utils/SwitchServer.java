package net.bteuk.proxy.utils;

import com.velocitypowered.api.scheduler.ScheduledTask;
import lombok.Getter;
import net.bteuk.proxy.Proxy;
import net.bteuk.proxy.User;
import net.bteuk.proxy.eventing.listeners.ServerConnectListener;
import net.bteuk.proxy.exceptions.ServerNotFoundException;

import java.util.concurrent.TimeUnit;

/**
 * Class to handle the switching of servers.
 * An instance is created when a user switches server.
 */
public class SwitchServer {

    private final User user;

    private final String fromServer;

    @Getter
    private final String toServer;

    private final long switchTime;

    private final ScheduledTask switchTask;

    public SwitchServer(User user, String fromServer, String toServer) {
        this.user = user;
        this.fromServer = fromServer;
        this.toServer = toServer;
        this.switchTime = Time.currentTime();

        switchTask = Proxy.getInstance().getServer().getScheduler().buildTask(Proxy.getInstance(), this::onTimeout)
                .delay(10L, TimeUnit.SECONDS)
                .schedule();

        // Switch the player to the server.
        try {
            ServerConnectListener.switchServer(user, toServer);
        } catch (ServerNotFoundException e) {
            // TODO: Send message to current server letting it know the server switch failed.
        }
    }

    public void cancelTimeout() {
        if (switchTask != null) {
            switchTask.cancel();
            user.setSwitchServer(null);
        }
    }

    private void onTimeout() {
        if (user != null) {
            Proxy.getInstance().getUserManager().disconnectUser(user);
            user.setSwitchServer(null);
        }
    }
}
