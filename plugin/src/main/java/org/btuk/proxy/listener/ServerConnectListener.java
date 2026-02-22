package org.btuk.proxy.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;

import org.btuk.proxy.Proxy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * This event is sent when the player connects to a server.
 * It is used to store the last connected server per player.
 */
public class ServerConnectListener {

    private final Map<UUID, String> lastServer;

    public ServerConnectListener(Proxy proxy, HashMap<UUID, String> lastServer) {
        this.lastServer = lastServer;

        // Register event.
        proxy.getServer().getEventManager().register(proxy, this);
        proxy.getLogger().info("Registered ServerConnectedEvent");
    }


    @Subscribe
    public void change(ServerConnectedEvent e) {
        //Store server as last server.
        setLastServer(e.getPlayer().getUniqueId(), e.getServer().getServerInfo().getName());
    }

    private void setLastServer(UUID uuid, String serverName) {
        lastServer.put(uuid, serverName);
    }
}
