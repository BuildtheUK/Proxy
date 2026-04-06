package org.btuk.proxy.core.server;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface CoreServerManager {

    Server createServer(String name);

    Optional<Server> getServer(String name);

    Set<Server> getServers();

    Set<Server> getOnlineServers();

    void addServer(Server server);

    void removeServer(Server server);

    void shutdown();
}
