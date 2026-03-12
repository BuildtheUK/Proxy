package org.btuk.proxy.player;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.player.TabList;
import org.btuk.proxy.server.ProxyServer;
import net.kyori.adventure.text.Component;

import java.util.UUID;

import org.btuk.proxy.core.server.Server;

public class ProxyPlayer implements org.btuk.proxy.core.player.Player {

    private final Player player;

    public ProxyPlayer(Player player) {
        this.player = player;
    }

    @Override
    public void connectToServer(Server server) {
        if (server instanceof ProxyServer proxyServer)
            player.createConnectionRequest(proxyServer.getServer()).fireAndForget();
    }

    @Override
    public UUID getUniqueId() {
        return player.getUniqueId();
    }

    @Override
    public boolean hasPermission(String permission) {
        return player.hasPermission(permission);
    }

    @Override
    public String getUsername() {
        return player.getUsername();
    }

    @Override
    public void sendPlayerListHeaderAndFooter(Component header, Component footer) {
        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    public TabList getTabList() {
        return player.getTabList();
    }
}
