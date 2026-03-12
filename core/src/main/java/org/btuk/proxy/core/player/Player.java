package org.btuk.proxy.core.player;

import net.kyori.adventure.text.Component;

import java.util.UUID;

import org.btuk.proxy.core.server.Server;

public interface Player {
    void connectToServer(Server server);

    UUID getUniqueId();

    boolean hasPermission(String permission);

    String getUsername();

    void sendPlayerListHeaderAndFooter(Component header, Component footer);
}
