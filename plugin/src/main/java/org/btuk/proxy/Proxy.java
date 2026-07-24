package org.btuk.proxy;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Getter;
import net.bteuk.network.lib.socket.InputSocket;

import org.btuk.proxy.chat.ProxyChatHandler;

import org.btuk.proxy.listener.CommandListener;
import org.btuk.proxy.listener.ServerConnectListener;
import org.btuk.proxy.player.ProxyPlayerManager;
import org.btuk.proxy.scheduler.ProxyScheduler;
import org.btuk.proxy.server.ProxyCoreServerManager;
import org.btuk.proxy.core.socket.ProxySocketHandler;

import org.btuk.proxy.tab.ProxyTabManager;
import org.slf4j.Logger;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Consumer;

import org.btuk.proxy.core.ProxyController;

@Plugin(id = "proxy", name = "Proxy", version = "1.12.0-SNAPSHOT",
        url = "https://github.com/BTEUK/Proxy", description = "Proxy plugin, managed chat, discord and server related actions.", authors = {"ELgamer"})
public class Proxy {

    @Getter
    private final ProxyServer server;
    @Getter
    private final Logger logger;

    @Getter
    private static Proxy instance;
    private InputSocket inputSocket;

    private File dataFolder;

    private HashMap<UUID, String> lastServer;

    private ProxyController proxyController;
    
    private String defaultServer;

    @Inject
    public Proxy(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;

        instance = this;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) throws IOException {

        this.proxyController = new ProxyController(getDataFolder());

        this.defaultServer = proxyController.getConfig().getString("default_server");

        ProxyScheduler scheduler = new ProxyScheduler(this);

        ProxyPlayerManager playerManager = new ProxyPlayerManager(this);
        ProxyCoreServerManager serverManager = new ProxyCoreServerManager(proxyController.getGlobalSQL(), this);
        ProxyChatHandler chatHandler = new ProxyChatHandler(serverManager, proxyController.getConfig());
        ProxyTabManager tabManager = new ProxyTabManager(getServer(), scheduler, proxyController.getConfig(), proxyController.getCoreUserManager(), chatHandler);

        // Start socket.
        Consumer<ProxySocketHandler> socketInitializer;
        int inputSocketPort = proxyController.getConfig().getInt("socket.input.port");
        if (inputSocketPort == 0) {
            logger.error("Socket port is not set in config or is set to 0. Please set a valid port!");
            return;
        } else {
            // Create the socket initialiser.
            socketInitializer = socketHandler -> {
                inputSocket = new InputSocket(inputSocketPort);
                inputSocket.start(socketHandler);
            };
        }

        proxyController.start(chatHandler, scheduler, serverManager, playerManager, tabManager, socketInitializer);

        lastServer = new HashMap<>();

        this.dataFolder = getDataFolder();

        loadLastServer();

        // Register listeners.
        new CommandListener(this);
        new ServerConnectListener(this, lastServer);

        logger.info("Loaded Proxy");

    }

    @Subscribe
    public void onProxyShutDown(ProxyShutdownEvent event) {
        //Store the last server players are connected to.
        updateLastServer();

        if (inputSocket != null) {
            inputSocket.close();
        }
        
        if (proxyController != null) {
            proxyController.stop();
        }
    }

    @Subscribe
    public void choose(PlayerChooseInitialServerEvent e) {

        Player player = e.getPlayer();


        String prev = getLastServer(player.getUniqueId());
        RegisteredServer server;
        //Not null check
        if (prev != null) {
            //Get the RegisteredServer
            server = getServer(prev);
            //Not null check
            if (server != null) {
                //Check if server is online.
                if (proxyController.getGlobalSQL().hasRow("SELECT name FROM server_data WHERE name='" + server.getServerInfo().getName() + "' AND online=1;")) {
                    e.setInitialServer(server);
                    return;
                }
            }
        }

        //Try to set the default server.
        if (defaultServer != null) {

            RegisteredServer registeredServer = getServer(defaultServer);

            if (registeredServer != null) {

                //Set the default server.
                //Check if server is online.
                if (proxyController.getGlobalSQL().hasRow("SELECT name FROM server_data WHERE name='" + registeredServer.getServerInfo().getName() + "' AND online=1;")) {
                    e.setInitialServer(registeredServer);
                    return;
                }
            }
        }

        RegisteredServer random_server = getRandomOnlineServer();

        //Check if any server exists.
        if (random_server == null) {
            return;
        }

        //Set the server.
        e.setInitialServer(random_server);

    }

    private RegisteredServer getRandomOnlineServer() {

        Collection<RegisteredServer> servers = getServer().getAllServers();

        for (RegisteredServer server : servers) {
            //Check if server is online.
            if (proxyController.getGlobalSQL().hasRow("SELECT name FROM server_data WHERE name='" + server.getServerInfo().getName() + "' AND online=1;")) {
                return server;
            }
        }

        //Return null if no servers can be found.
        return null;

    }

    public static RegisteredServer getServer(String name) {
        for (RegisteredServer server : instance.getServer().getAllServers()) {
            if (server.getServerInfo().getName().equalsIgnoreCase(name)) {
                return server;
            }
        }
        return null;
    }

    private String getLastServer(UUID uuid) {
        return lastServer.get(uuid);
    }

    //Store the last server data in the properties file when the server closes.
    private void updateLastServer() {

        try (OutputStream output = new FileOutputStream(dataFolder + "/last_server.properties")) {

            Properties prop = new Properties();

            //Store all entries of the array.
            for (Map.Entry<UUID, String> entry : lastServer.entrySet()) {
                prop.setProperty(entry.getKey().toString(), entry.getValue());
            }

            prop.store(output, null);

        } catch (IOException io) {
            io.printStackTrace();
        }
    }

    //Sets the hashmap with entries from the properties file on server load.
    private void loadLastServer() {

        try (InputStream input = new FileInputStream(dataFolder + "/last_server.properties")) {

            Properties prop = new Properties();

            // load a properties file
            prop.load(input);

            prop.forEach((uuid, server) -> lastServer.put(UUID.fromString((String) uuid), (String) server));
            logger.info("Loaded last_server.properties with " + lastServer.size() + " entries.");

        } catch (IOException ignored) {
            logger.info("last_server.properties does not exist, if this is the first time loading the plugin this is normal behaviour.");
        }
    }

    public File getDataFolder() {
        File dataFolder = this.dataFolder;
        if (dataFolder == null) {
            String path = "plugins/proxy/";
            try {
                dataFolder = new File(path);
                dataFolder.mkdir();
                return dataFolder;
            } catch (Exception e) {
                return null;
            }
        } else {
            return dataFolder;
        }
    }
}
