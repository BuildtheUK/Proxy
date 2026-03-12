package org.btuk.proxy.core.exceptions;

public class ServerNotFoundException extends Exception {
    public ServerNotFoundException(String server) {
        super(String.format("Server %s not found!", server));
    }
}
