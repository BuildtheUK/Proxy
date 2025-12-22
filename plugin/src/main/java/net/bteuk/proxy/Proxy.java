package net.bteuk.proxy;

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
import net.bteuk.network.lib.dto.OnlineUserRemove;
import net.bteuk.network.lib.socket.InputSocket;
import net.bteuk.network.lib.socket.SocketHandler;
import net.bteuk.proxy.chat.ChatHandler;
import net.bteuk.proxy.chat.ChatManager;
import net.bteuk.proxy.config.Config;
import net.bteuk.proxy.database.DatabaseInit;
import net.bteuk.proxy.eventing.listeners.CommandListener;
import net.bteuk.proxy.eventing.listeners.ServerConnectListener;
import net.bteuk.proxy.socket.ProxySocketHandler;
import net.bteuk.proxy.database.sql.GlobalSQL;
import net.bteuk.proxy.database.sql.PlotSQL;
import net.bteuk.proxy.database.sql.RegionSQL;
import net.bteuk.proxy.utils.Analytics;
import net.bteuk.proxy.utils.Linked;
import net.bteuk.proxy.utils.ReviewStatus;
import org.slf4j.Logger;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static java.awt.Color.RED;
import static net.bteuk.proxy.utils.Analytics.enableAnalytics;
import static net.bteuk.proxy.utils.Constants.LEAVE_MESSAGE;

@Plugin(id = "proxy", name = "Proxy", version = "1.9.4",
        url = "https://github.com/BTEUK/Proxy", description = "Proxy plugin, managed chat, discord and server related actions.", authors = {"ELgamer"})
public class Proxy {

    @Getter
    private final ProxyServer server;
    @Getter
    private final Logger logger;

    @Getter
    private static Proxy instance;
    private InputSocket inputSocket;

    @Getter
    private Config config;

    private File dataFolder;

    @Getter
    private Discord discord;

    @Getter
    private ArrayList<Linked> linking;

    @Getter
    private GlobalSQL globalSQL;
    @Getter
    private PlotSQL plotSQL;
    @Getter
    private RegionSQL regionSQL;

    private HashMap<UUID, String> lastServer;

    @Getter
    private UserManager userManager;

    @Getter
    private ChatManager chatManager;

    @Getter
    private TabManager tabManager;

    @Getter
    private ChatHandler chatHandler;

    @Getter
    private ServerManager serverManager;

    @Inject
    public Proxy(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;

        instance = this;

        try {
            config = new Config();
        } catch (IOException e) {
            getLogger().warn("An error occurred while loading the config", e);
        }
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        
        // Init logging to ensure java util logging works.
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        // Set up MySQL
        try {

            DatabaseInit init = new DatabaseInit();

            String host = Proxy.getInstance().getConfig().getString("host");
            int port = Proxy.getInstance().getConfig().getInt("port");
            String username = Proxy.getInstance().getConfig().getString("username");
            String password = Proxy.getInstance().getConfig().getString("password");

            // Global Database
            String globalDatabase = config.getString("database.global");
            DataSource globalDataSource = init.mysqlSetup(globalDatabase, host, port, username, password);
            globalSQL = new GlobalSQL(globalDataSource);

            // Region Database
            String regionDatabase = config.getString("database.region");
            DataSource regionDataSource = init.mysqlSetup(regionDatabase, host, port, username, password);
            regionSQL = new RegionSQL(regionDataSource);

            // Plot Database
            String plotDatabase = config.getString("database.plot");
            DataSource plotDataSource = init.mysqlSetup(plotDatabase, host, port, username, password);
            plotSQL = new PlotSQL(plotDataSource);

            // Init schemas and update if necessary.
            init.initializeSchemas(globalDataSource, plotDataSource, regionDataSource);

        } catch (SQLException | RuntimeException e) {
            logger.error("Failed to connect to the database, please check that you have set the config values correctly.", e);
            logger.error("Disabling Proxy");
            return;
        }

        userManager = new UserManager(server);

        chatManager = new ChatManager(userManager);

        tabManager = new TabManager(server, config);

        serverManager = new ServerManager(this);

        chatHandler = new ChatHandler(this, config);

        discord = new Discord();

        linking = new ArrayList<>();

        lastServer = new HashMap<>();

        int inputSocketPort = Proxy.getInstance().getConfig().getInt("socket.input.port");

        // Start socket.
        if (inputSocketPort == 0) {
            logger.error("Socket port is not set in config or is set to 0. Please set a valid port!");
        } else {
            // Create the socket handler.
            SocketHandler handler = new ProxySocketHandler(chatManager);

            // Create the input socket.
            inputSocket = new InputSocket(inputSocketPort);
            inputSocket.start(handler);
        }

        this.dataFolder = getDataFolder();

        loadLastServer();

        //Setup review status message.
        new ReviewStatus();

        // Register listeners.
        new CommandListener(this);
        new ServerConnectListener(this, lastServer);

        enableAnalytics(this);

        serverManager.initOnlineServers();

        logger.info("Loaded Proxy");

    }

    @Subscribe
    public void onProxyShutDown(ProxyShutdownEvent event) {

        AtomicInteger users = new AtomicInteger(userManager.getUsers().size());

        //Store the last server players are connected to.
        updateLastServer();

        if (inputSocket != null) {
            inputSocket.close();
        }

        // Get start time.
        long startTime = System.currentTimeMillis();
        long currentTime = System.currentTimeMillis();

        // Show disconnect message for all players in discord.
        for (User user : userManager.getUsers()) {
            if (user.isOnline()) {
                discord.sendConnectEmbed(LEAVE_MESSAGE, user.getName(), user.getUuid(), user.getPlayerSkin(), RED, (reply) -> users.decrementAndGet());
            }
        }

        // Stop if it takes longer than 15 seconds.
        while (users.get() > 0 && (currentTime - startTime) < 15000) {
            // Get the time for a pontetial timeout.
            currentTime = System.currentTimeMillis();
        }

        if (!userManager.getUsers().isEmpty()) {
            getLogger().info("Sent disconnect message to online users!");
        }

        //Update statistics
        Analytics.saveAll();

        // Remove the user instance.
        userManager.removeAllUsers();

        // Tell the server to remove all online users.
        userManager.getOnlineUsers().forEach(user -> Proxy.getInstance().getChatHandler().handle(new OnlineUserRemove(user.getUuid())));

        // Clear JDA listeners
        if (discord.getJda() != null) {
            //Unregister listners.
            discord.getJda().getEventManager().getRegisteredListeners().forEach(listener -> discord.getJda().getEventManager().unregister(listener));
        }

        // try to shut down jda gracefully
        if (discord.getJda() != null) {
            try {
                discord.getJda().shutdownNow();
                discord.setJda(null);
            } catch (NoClassDefFoundError ignored) {
            }
        }

        // Set all servers as offline.
        serverManager.shutdown();
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
                if (globalSQL.hasRow("SELECT name FROM server_data WHERE name='" + server.getServerInfo().getName() + "' AND online=1;")) {
                    e.setInitialServer(server);
                    return;
                }
            }
        }

        //Try default server.
        String default_server = config.getString("default_server");

        //Try to set the default server.
        if (default_server != null) {

            RegisteredServer registeredServer = getServer(default_server);

            if (registeredServer != null) {

                //Set the default server.
                //Check if server is online.
                if (globalSQL.hasRow("SELECT name FROM server_data WHERE name='" + registeredServer.getServerInfo().getName() + "' AND online=1;")) {
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
            if (globalSQL.hasRow("SELECT name FROM server_data WHERE name='" + server.getServerInfo().getName() + "' AND online=1;")) {
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
