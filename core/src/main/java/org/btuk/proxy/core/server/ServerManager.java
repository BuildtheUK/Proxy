package org.btuk.proxy.core.server;

import lombok.extern.java.Log;
import org.btuk.network.lib.dto.OnlineUserRemove;
import org.btuk.network.lib.dto.OnlineUsersReply;
import org.btuk.network.lib.dto.ServerShutdown;
import org.btuk.network.lib.dto.ServerStartup;
import org.btuk.proxy.core.chat.ChatHandler;
import org.btuk.proxy.core.scheduler.Scheduler;
import org.btuk.proxy.core.tab.TabManager;
import org.btuk.proxy.core.user.CoreUserManager;
import org.btuk.proxy.core.user.User;
import org.btuk.proxy.core.user.UserManager;
import org.btuk.proxy.core.utils.Time;
import org.btuk.proxy.database.sql.GlobalSQL;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Log
public class ServerManager {

    private final CoreServerManager coreServerManager;

    private final GlobalSQL globalSQL;

    private final ChatHandler chatHandler;

    private final TabManager tabManager;

    private final CoreUserManager coreUserManager;

    private final UserManager userManager;

    private final ExecutorService threadExecutor = Executors.newSingleThreadExecutor();

    public ServerManager(CoreServerManager coreServerManager, Scheduler scheduler, GlobalSQL globalSQL, ChatHandler chatHandler, TabManager tabManager, CoreUserManager coreUserManager, UserManager userManager) {
        this.coreServerManager = coreServerManager;
        this.globalSQL = globalSQL;
        this.chatHandler = chatHandler;
        this.tabManager = tabManager;
        this.coreUserManager = coreUserManager;
        this.userManager = userManager;

        // Ping all servers every 10 seconds.
        scheduler.createRepeatingTask(this::pingServers, 0L, 10L, TimeUnit.SECONDS);
    }

    /**
     * Add all online servers, since it is possible servers are already available on Proxy start.
     */
    public void initOnlineServers() {
        coreServerManager.getOnlineServers().forEach(server -> threadExecutor.submit(() -> addServerIfOnline(server)));
    }

    public void addServer(ServerStartup serverStartup) {
        // It is possible the server is already set to online, this probably means it crashed,
        // first clear all players that are 'connected' to this server and remove them.
        Optional<Server> optionalServer = coreServerManager.getServers().stream().filter(server -> server.getName().equals(serverStartup.getServerName())).findFirst();
        optionalServer.ifPresent(this::removeServerDueToTimeout);

        try {
            Server server = coreServerManager.createServer(serverStartup.getServerName());
            threadExecutor.submit(() -> addServerIfOnline(server));
        } catch (RuntimeException e) {
            log.warning("Unable to add server " + serverStartup.getServerName() + ", it can not be found.");
        }
    }

    public void removeServer(ServerShutdown serverShutdown) {
        Optional<Server> optionalServer = coreServerManager.getServers().stream().filter(server -> server.getName().equals(serverShutdown.getServerName())).findFirst();
        optionalServer.ifPresent(coreServerManager::removeServer);

        // Set the server offline in the database.
        globalSQL.update("UPDATE server_data SET online=0 WHERE name='" + serverShutdown.getServerName() + "';");
    }

    private void addServerIfOnline(Server server) {
        // Skip if the server is already added.
        if (coreServerManager.getServers().stream().anyMatch(server::equals)) {
            return;
        }
        if (server.canPing()) {
            coreServerManager.addServer(server);

            // Set the server online in the database.
            globalSQL.update("UPDATE server_data SET online=1 WHERE name='" + server.getName() + "';");

            // Send all online users to the server as a reply.
            chatHandler.handle(new OnlineUsersReply(coreUserManager.getOnlineUsers()));
            tabManager.sendAddTeam();
        } else {
            // The server is not online.
            log.warning(String.format("Server " + server.getName() + " is not online."));
        }
    }

    private void pingServers() {
        coreServerManager.getServers().forEach(server -> threadExecutor.submit(() -> updatePing(server)));
        // If any server has a ping of more than 120 seconds, set the server to offline and remove all online players that were connected to the server.
        // This probably means the server crashed.
        List<Server> offlineServers = coreServerManager.getServers().stream().filter(server -> server.getLastPing() < Time.currentTime() - 1000 * 120).toList();
        offlineServers.forEach(this::removeServerDueToTimeout);
    }

    private void removeServerDueToTimeout(Server server) {
        // Set the server offline in the database.
        globalSQL.update("UPDATE server_data SET online=0 WHERE name='" + server.getName() + "';");

        // Remove all users connected to this server,
        // and also send a message to all other online servers to remove these users from their list.
        List<User> offlineServerUsers = coreUserManager.getUsersOnServer(server.getName());
        offlineServerUsers.forEach(user -> {
                OnlineUserRemove onlineUserRemove = new OnlineUserRemove(user.getUuid());
                chatHandler.handle(onlineUserRemove);
                userManager.disconnectUser(user);
            }
        );

        // Remove server from the list.
        coreServerManager.removeServer(server);
    }

    private void updatePing(Server server) {
        if (server.canPing()) {
            server.setLastPing(Time.currentTime());
        }
    }
}
