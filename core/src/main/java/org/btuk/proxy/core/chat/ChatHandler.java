package org.btuk.proxy.core.chat;

import org.btuk.network.lib.dto.AbstractTransferObject;

import org.btuk.proxy.core.exceptions.ServerNotFoundException;

public interface ChatHandler {

    /**
     * Handle a message.
     *
     * @param message the message to handle.
     */
    void handle(AbstractTransferObject message);

    /**
     * Handle a message, send it to a specific server.
     *
     * @param message the message to handle.
     * @param server  the server to send the message to.
     * @throws ServerNotFoundException if the server cannot be found
     */
    void handle(AbstractTransferObject message, String server) throws ServerNotFoundException;
}
