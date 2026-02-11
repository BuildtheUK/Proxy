package net.bteuk.proxy.utils;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Getter;
import lombok.Setter;

/**
 * Object for a Minecraft server.
 */
@Getter
public class Server {

    private final RegisteredServer registeredServer;

    private final String name;

    @Setter
    private long lastPing;

    public Server(RegisteredServer registeredServer) {
        this.registeredServer = registeredServer;
        this.name = registeredServer.getServerInfo().getName();
        lastPing = Time.currentTime();
    }
}
