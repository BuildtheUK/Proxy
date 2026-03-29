package org.btuk.proxy.server;

import lombok.Getter;
import lombok.extern.java.Log;
import org.btuk.proxy.Proxy;
import org.btuk.proxy.database.sql.GlobalSQL;

import org.btuk.proxy.core.server.CoreServerManager;
import org.btuk.proxy.core.server.Server;


import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manages the Minecraft servers.
 */
@Log
public class ProxyCoreServerManager implements CoreServerManager {

    private final GlobalSQL globalSQL;

    private final Proxy proxy;

    @Getter
    private final Set<Server> servers;

    public ProxyCoreServerManager(GlobalSQL globalSQL, Proxy proxy) {
        this.globalSQL = globalSQL;
        this.proxy = proxy;
        servers = Collections.synchronizedSet(new HashSet<>());
    }

    /**
     * Removes all server on proxy shutdown.
     */
    public void shutdown() {
        // Set the servers offline in the database.
        globalSQL.update("UPDATE server_data SET online=0;");
        servers.clear();
    }

    @Override
    public Server createServer(String name) {
        return new ProxyServer(proxy.getServer().getServer(name).orElseThrow(() -> new RuntimeException("Server " + name + " not found!")));
    }

    @Override
    public Optional<Server> getServer(String name) {
        return servers.stream().filter(server -> server.getName().equals(name)).findFirst();
    }

    @Override
    public List<Server> getOnlineServers() {
        return proxy.getServer().getAllServers().stream().map(ProxyServer::new).collect(Collectors.toList());
    }

    @Override
    public void addServer(Server server) {
        servers.add(server);
    }

    @Override
    public void removeServer(Server server) {
        servers.remove(server);
    }
}
