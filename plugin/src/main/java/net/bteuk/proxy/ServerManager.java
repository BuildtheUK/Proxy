package net.bteuk.proxy;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Getter;
import net.bteuk.network.lib.dto.OnlineUserRemove;
import net.bteuk.network.lib.dto.ServerShutdown;
import net.bteuk.network.lib.dto.ServerStartup;
import net.bteuk.proxy.utils.Server;
import net.bteuk.proxy.utils.Time;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Minecraft servers.
 */
public class ServerManager {

    private final Proxy proxy;

    @Getter
    private final List<Server> servers;

    private final ExecutorService threadExecutor;

    public ServerManager(Proxy proxy) {
        this.proxy = proxy;
        servers = Collections.synchronizedList(new ArrayList<>());
        threadExecutor = Executors.newSingleThreadExecutor();

        // Ping all servers every 10 seconds.
        Proxy.getInstance().getServer().getScheduler().buildTask(Proxy.getInstance(), this::pingServers)
                .repeat(10L, TimeUnit.SECONDS)
                .schedule();
    }

    public void addServer(ServerStartup serverStartup) {
        // It is possible the server is already set to online, this probably means it crashed,
        // first clear all players that are 'connected' to this server and remove them.
        Optional<Server> optionalServer = servers.stream().filter(server -> server.getName().equals(serverStartup.getServerName())).findFirst();
        optionalServer.ifPresent(this::removeServerDueToTimeout);

        RegisteredServer registeredServer = Proxy.getServer(serverStartup.getServerName());
        threadExecutor.submit(() -> addServerIfOnline(registeredServer));
    }

    public void removeServer(ServerShutdown serverShutdown) {
        Optional<Server> optionalServer = servers.stream().filter(server -> server.getName().equals(serverShutdown.getServerName())).findFirst();
        optionalServer.ifPresent(servers::remove);

        // Set the server offline in the database.
        proxy.getGlobalSQL().update("UPDATE server_data SET online=0 WHERE name='" + serverShutdown.getServerName() + "';");
    }

    /**
     * Add all online servers, since it is possible servers are already available on Proxy start.
     */
    public void initOnlineServers() {
        proxy.getServer().getAllServers().forEach(registeredServer -> threadExecutor.submit(() -> addServerIfOnline(registeredServer)));
    }

    /**
     * Removes all server on proxy shutdown.
     */
    public void shutdown() {
        // Set the servers offline in the database.
        proxy.getGlobalSQL().update("UPDATE server_data SET online=0;");
        servers.clear();
    }

    private void pingServers() {
        servers.forEach(server -> threadExecutor.submit(() -> updatePing(server)));
        // If any server has a ping of more than 120 seconds, set the server to offline and remove all online players that were connected to the server.
        // This probably means the server crashed.
        List<Server> offlineServers = servers.stream().filter(server -> server.getLastPing() < Time.currentTime() - 1000 * 120).toList();
        offlineServers.forEach(this::removeServerDueToTimeout);
    }

    private void addServerIfOnline(RegisteredServer registeredServer) {
        // Skip if the server is already added.
        if (servers.stream().anyMatch(server -> server.getRegisteredServer().equals(registeredServer))) {
            return;
        }
        try {
            registeredServer.ping().get();
            servers.add(new Server(registeredServer));

            // Set the server online in the database.
            proxy.getGlobalSQL().update("UPDATE server_data SET online=1 WHERE name='" + registeredServer.getServerInfo().getName() + "';");

            // Send all online users to the server as reply.
            proxy.getUserManager().handleOnlineUsersRequest();
        } catch (Exception e) {
            // The server is not online.
            proxy.getLogger().warn(String.format("Server %s is not online, exception %s.", registeredServer.getServerInfo().getName(), e.getMessage()));
        }
    }

    private void updatePing(Server server) {
        try {
            server.getRegisteredServer().ping().get();
            server.setLastPing(Time.currentTime());
        } catch (Exception e) {
            // The server is not online.
            proxy.getLogger().warn(String.format("Server %s is not online.", server.getName()));
        }
    }

    private void removeServerDueToTimeout(Server server) {
        // Set the server offline in the database.
        proxy.getGlobalSQL().update("UPDATE server_data SET online=0 WHERE name='" + server.getName() + "';");

        // Remove all users connected to this server,
        // and also send a message to all other online servers to remove these users from their list.
        List<User> offlineServerUsers = proxy.getUserManager().getUsers().stream().filter(user -> user.getServer().equals(server.getName())).toList();
        offlineServerUsers.forEach(user -> {
                    OnlineUserRemove onlineUserRemove = new OnlineUserRemove(user.getUuid());
                    proxy.getChatHandler().handle(onlineUserRemove);
                    proxy.getUserManager().disconnectUser(user);
                }
        );

        // Remove server from list.
        servers.remove(server);
    }
}
