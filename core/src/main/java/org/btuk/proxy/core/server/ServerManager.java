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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Log
public class ServerManager {

    private final CoreServerManager coreServerManager;
    private final Scheduler scheduler;
    private final GlobalSQL globalSQL;

    private final ChatHandler chatHandler;

    private final TabManager tabManager;

    private final CoreUserManager coreUserManager;

    private final UserManager userManager;

    private final ExecutorService threadExecutor = Executors.newSingleThreadExecutor();

    public ServerManager(CoreServerManager coreServerManager, Scheduler scheduler, GlobalSQL globalSQL, ChatHandler chatHandler, TabManager tabManager, CoreUserManager coreUserManager, UserManager userManager) {
        this.coreServerManager = coreServerManager;
        this.scheduler = scheduler;
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
        String serverName = serverStartup.getServerName();
        // It is possible the server was already set to online, this probably means it crashed.
        // Even if the server is not currently tracked, there might be 'ghost' players.
        cleanupServer(serverName);

        try {
            Server server = coreServerManager.createServer(serverName);
            threadExecutor.submit(() -> addServerIfOnline(server));
        } catch (RuntimeException e) {
            log.warning("Unable to add server " + serverName + ", it can not be found.");
        }
    }

    public void removeServer(ServerShutdown serverShutdown) {
        cleanupServer(serverShutdown.getServerName());
    }

    private void addServerIfOnline(Server server) {
        addServerIfOnline(server, 0);
    }

    private void addServerIfOnline(Server server, int attempt) {
        // Skip if the server is already added.
        if (coreServerManager.getServers().stream().anyMatch(s -> s.getName().equals(server.getName()))) {
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
            if (attempt < 30) {
                scheduler.createDelayedTask(() -> threadExecutor.submit(() -> addServerIfOnline(server, attempt + 1)), 1, TimeUnit.SECONDS);
            } else {
                log.warning(String.format("Server " + server.getName() + " is not online."));
            }
        }
    }

    private void pingServers() {
        coreServerManager.getServers().forEach(server -> threadExecutor.submit(() -> updatePing(server)));
        // If any server has a ping of more than 60 seconds, set the server to offline and remove all online players that were connected to the server.
        // This probably means the server crashed.
        List<Server> offlineServers = coreServerManager.getServers().stream().filter(server -> server.getLastPing() < Time.currentTime() - 1000 * 60).toList();
        offlineServers.forEach(this::removeServerDueToTimeout);
    }

    private void cleanupServer(String serverName) {
        // Set the server offline in the database.
        globalSQL.update("UPDATE server_data SET online=0 WHERE name='" + serverName + "';");

        // Remove all users connected to this server,
        // and also send a message to all other online servers to remove these users from their list.
        List<User> offlineServerUsers = coreUserManager.getUsersOnServer(serverName);
        offlineServerUsers.forEach(user -> {
                OnlineUserRemove onlineUserRemove = new OnlineUserRemove(user.getUuid());
                chatHandler.handle(onlineUserRemove);
                userManager.disconnectUser(user);
            }
        );

        // Remove server from the list if it exists.
        coreServerManager.getServer(serverName).ifPresent(coreServerManager::removeServer);
    }

    private void removeServerDueToTimeout(Server server) {
        cleanupServer(server.getName());
    }

    private void updatePing(Server server) {
        if (server.canPing()) {
            server.setLastPing(Time.currentTime());
        }
    }
}
