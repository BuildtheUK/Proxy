package org.btuk.proxy.core.server;

import java.util.List;
import java.util.Optional;

public interface CoreServerManager {

    Server createServer(String name);

    Optional<Server> getServer(String name);

    List<Server> getServers();

    List<Server> getOnlineServers();

    void addServer(Server server);

    void removeServer(Server server);

    void shutdown();
}
