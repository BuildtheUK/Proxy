package net.bteuk.proxy.eventing.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.bteuk.proxy.Proxy;
import net.bteuk.proxy.User;
import net.bteuk.proxy.exceptions.ServerNotFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    public static void switchServer(User user, String serverName) throws ServerNotFoundException {

        Optional<RegisteredServer> optionalServer = Proxy.getInstance().getServer().getServer(serverName);

        if (optionalServer.isPresent()) {
            user.getPlayer().createConnectionRequest(optionalServer.get());
        } else {
            throw new ServerNotFoundException(serverName);
        }
    }

    private void setLastServer(UUID uuid, String serverName) {
        lastServer.put(uuid, serverName);
    }
}
