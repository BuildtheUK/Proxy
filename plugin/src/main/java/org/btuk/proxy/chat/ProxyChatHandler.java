package org.btuk.proxy.chat;

import lombok.extern.java.Log;
import net.bteuk.network.lib.dto.AbstractTransferObject;
import net.bteuk.network.lib.socket.OutputSocket;
import org.btuk.proxy.core.exceptions.ServerNotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.btuk.proxy.core.chat.ChatHandler;
import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.config.ConfigSocket;
import org.btuk.proxy.core.server.Server;
import org.btuk.proxy.core.server.CoreServerManager;

@Log
public class ProxyChatHandler implements ChatHandler {

    private final CoreServerManager coreServerManager;

    private final Map<String, OutputSocket> sockets;

    public ProxyChatHandler(CoreServerManager coreServerManager, Config config) {
        this.coreServerManager = coreServerManager;
        sockets = new HashMap<>();
        List<ConfigSocket> configSockets = config.getSockets("socket.output");
        configSockets.forEach(socket -> sockets.put(socket.getServer(), new OutputSocket(socket.getIP(), socket.getPort())));
    }

    /**
     * Handle a message.
     *
     * @param message the message to handle.
     */
    @Override
    public void handle(AbstractTransferObject message) {
        // Send the direct message to all servers.
        sendProxyMessage(message);
    }

    /**
     * Handle a message, send it to a specific server.
     *
     * @param message the message to handle.
     * @param server  the server to send the message to.
     * @throws ServerNotFoundException if the server can not be found
     */
    @Override
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
        coreServerManager.getServers().forEach(server -> {
            OutputSocket socket = sockets.get(server.getName());
            if (socket == null) {
                log.severe("Server " + server.getName() + " exists but no Socket has been configured.");
            } else {
                if (!socket.sendSocketMessage(message)) {
                    log.warning("Unable to send " + message.getClass().getTypeName() + " to server " + server.getName() + ", it is probably offline.");
                }
            }
        });
    }

    private void sendProxyMessage(AbstractTransferObject message, String serverName) throws ServerNotFoundException {
        Optional<Server> optionalServer = coreServerManager.getServers().stream().filter(server -> server.getName().equals(serverName)).findFirst();
        if (optionalServer.isPresent()) {
            OutputSocket socket = sockets.get(serverName);
            if (socket == null) {
                throw new ServerNotFoundException(serverName);
            } else {
                if (!socket.sendSocketMessage(message)) {
                    log.warning("Unable to send " + message.getClass().getTypeName() + " to server " + optionalServer.get().getName() + ", it is probably offline.");
                }
            }
        } else {
            throw new ServerNotFoundException(serverName);
        }
    }
}
