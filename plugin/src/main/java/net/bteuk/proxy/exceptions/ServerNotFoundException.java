package net.bteuk.proxy.exceptions;

public class ServerNotFoundException extends Exception {
    public ServerNotFoundException(String server) {
        super(String.format("Server %s not found!", server));
    }
}
