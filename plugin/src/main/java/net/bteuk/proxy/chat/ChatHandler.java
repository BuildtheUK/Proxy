package net.bteuk.proxy.chat;

import net.bteuk.network.lib.dto.AbstractTransferObject;
import net.bteuk.network.lib.socket.OutputSocket;
import net.bteuk.proxy.Proxy;
import net.bteuk.proxy.config.Config;
import net.bteuk.proxy.exceptions.ServerNotFoundException;
import net.bteuk.proxy.config.ConfigSocket;
import net.bteuk.proxy.utils.Server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ChatHandler {

    private final Proxy instance;

    private final Map<String, OutputSocket> sockets;

    public ChatHandler(Proxy instance, Config config) {
        this.instance = instance;
        sockets = new HashMap<>();
        List<ConfigSocket> configSockets = config.getSockets("socket.output");
        configSockets.forEach(socket -> sockets.put(socket.getServer(), new OutputSocket(socket.getIP(), socket.getPort())));
    }

    /**
     * Handle a message.
     *
     * @param message the message to handle.
     */
    public void handle(AbstractTransferObject message) {
        // Send the direct message to all servers.
        sendProxyMessage(message);
    }

    /**
     * Handle a message, send it to a specific server.
     *
     * @param message the message to handle.
     * @param server the server to send the message to.
     *
     * @throws ServerNotFoundException if the server can not be found
     */
    public void handle(AbstractTransferObject message, String server) throws ServerNotFoundException {
        // Send the direct message to the specified server.
        sendProxyMessage(message, server);
    }

    /**
     * Send a message to all servers.
     *
     * @param message the {@link AbstractTransferObject} to send
     */
    private void sendProxyMessage(AbstractTransferObject message) {
        instance.getServerManager().getServers().forEach(server -> {
            OutputSocket socket = sockets.get(server.getName());
            if (socket == null) {
                instance.getLogger().error("Server {} exists but no Socket has been configured.", server.getName());
            } else {
                if (!socket.sendSocketMessage(message)) {
                    instance.getLogger().warn("Unable to send {} to server {}, it is probably offline.", message.getClass().getTypeName(), server.getName());
                }
            }
        });
    }

    private void sendProxyMessage(AbstractTransferObject message, String serverName) throws ServerNotFoundException {
        Optional<Server> optionalServer = instance.getServerManager().getServers().stream().filter(server -> server.getName().equals(serverName)).findFirst();
        if (optionalServer.isPresent()) {
            OutputSocket socket = sockets.get(serverName);
            if (socket == null) {
                throw new ServerNotFoundException(serverName);
            } else {
                if (!socket.sendSocketMessage(message)) {
                    instance.getLogger().warn("Unable to send {} to server {}, it is probably offline.", message.getClass().getTypeName(), optionalServer.get().getName());
                }
            }
        } else {
            throw new ServerNotFoundException(serverName);
        }
    }
}
