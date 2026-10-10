package org.btuk.proxy.core.utils;

import lombok.Getter;

import org.btuk.proxy.core.server.CoreServerManager;
import org.btuk.proxy.core.server.Server;
import org.btuk.proxy.core.user.User;

import lombok.extern.java.Log;

import org.btuk.proxy.core.user.UserManager;
import org.btuk.proxy.core.exceptions.ServerNotFoundException;
import org.btuk.proxy.core.scheduler.ScheduledTask;
import org.btuk.proxy.core.scheduler.Scheduler;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Class to handle the switching of servers.
 * An instance is created when a user switches server.
 */
@Log
public class SwitchServer {

    private final UserManager userManager;

    private final CoreServerManager serverManager;

    private final User user;

    @Getter
    private final String fromServer;

    @Getter
    private final String toServer;

    private final long switchTime;

    private final ScheduledTask switchTask;

    public SwitchServer(UserManager userManager, CoreServerManager serverManager, Scheduler scheduler, User user, String fromServer, String toServer) {
        this.userManager = userManager;
        this.serverManager = serverManager;
        this.user = user;
        this.fromServer = fromServer;
        this.toServer = toServer;
        this.switchTime = org.btuk.proxy.core.utils.Time.currentTime();

        switchTask = scheduler.createDelayedTask(this::onTimeout, 10L, TimeUnit.SECONDS);

        // Switch the player to the server.
        try {
            switchServer(user, toServer);
        } catch (ServerNotFoundException e) {
            log.warning("User " + user.getName() + " attempted to switch to server " + toServer + ", but that server does not exist.");
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
            userManager.disconnectUser(user);
            user.setSwitchServer(null);
        }
    }

    private void switchServer(User user, String serverName) throws ServerNotFoundException {
        Optional<Server> optionalServer = serverManager.getServer(serverName);
        if (optionalServer.isPresent() && user.getPlayer() != null) {
            user.getPlayer().connectToServer(optionalServer.get());
        } else {
            throw new ServerNotFoundException(serverName);
        }
    }
}
