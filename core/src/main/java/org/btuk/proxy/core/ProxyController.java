package org.btuk.proxy.core;

import lombok.Getter;
import lombok.extern.java.Log;
import net.bteuk.network.lib.dto.OnlineUserRemove;
import org.btuk.proxy.database.DatabaseInit;
import org.btuk.proxy.database.sql.GlobalSQL;
import org.btuk.proxy.database.sql.PlotSQL;
import org.btuk.proxy.database.sql.RegionSQL;
import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.btuk.proxy.core.chat.ChatHandler;
import org.btuk.proxy.core.chat.ChatManager;
import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.discord.Discord;
import org.btuk.proxy.core.discord.ReviewStatus;
import org.btuk.proxy.core.player.PlayerManager;
import org.btuk.proxy.core.scheduler.Scheduler;
import org.btuk.proxy.core.server.CoreServerManager;
import org.btuk.proxy.core.server.ServerManager;
import org.btuk.proxy.core.socket.ProxySocketHandler;
import org.btuk.proxy.core.tab.TabManager;
import org.btuk.proxy.core.user.CoreUserManager;
import org.btuk.proxy.core.user.UserManager;
import org.btuk.proxy.core.utils.Analytics;
import org.btuk.proxy.core.utils.Constants;
import org.btuk.proxy.core.utils.Moderation;

import static java.awt.Color.RED;
import static org.btuk.proxy.core.utils.Constants.LEAVE_MESSAGE;

/**
 * Controller of the core proxy functionality; can be enabled by the proxy or external plugins that want to use the proxy functions.
 */
@Log
public class ProxyController {

    private boolean enabled = false;

    private boolean started = false;

    @Getter
    private final Config config;

    @Getter
    private GlobalSQL globalSQL;
    private RegionSQL regionSQL;
    private PlotSQL plotSQL;

    @Getter
    private final CoreUserManager coreUserManager;

    private ChatHandler chatHandler;

    private CoreServerManager coreServerManager;

    private Analytics analytics;

    private Discord discord;

    private UserManager userManager;

    public ProxyController(File dataFolder) {
        // Init logging to ensure java util logging works.
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();

        try {
            config = new Config(dataFolder);
            Constants.init(config);
        } catch (IOException e) {
            log.warning("An error occurred while loading the config: " + e.getMessage());
            log.severe("Proxy cannot start without a valid config");
            throw new RuntimeException("Proxy cannot start without a valid config");
        }

        setupDatabase();
        this.coreUserManager = new CoreUserManager();
        this.enabled = true;
    }


    public void start(ChatHandler chatHandler, Scheduler scheduler, CoreServerManager coreServerManager, PlayerManager playerManager, TabManager tabManager, Consumer<ProxySocketHandler> socketInitializer) {

        if (!enabled) {
            log.severe("Proxy is not enabled, see previous logs for errors.");
            return;
        }

        this.chatHandler = chatHandler;
        this.coreServerManager = coreServerManager;

        this.analytics = new Analytics(coreUserManager, globalSQL, scheduler);
        Moderation moderation = new Moderation(globalSQL);

        ChatManager chatManager = new ChatManager(chatHandler, coreUserManager, analytics, globalSQL, moderation);

        this.discord = new Discord(config, globalSQL, chatHandler, scheduler, chatManager, coreUserManager, tabManager, plotSQL);

        this.userManager = new UserManager(coreUserManager, chatHandler, tabManager, globalSQL, plotSQL, regionSQL, coreServerManager, scheduler, chatManager, playerManager, analytics, discord);

        ServerManager serverManager = new ServerManager(coreServerManager, scheduler, globalSQL, chatHandler, tabManager, coreUserManager, userManager);

        // Set up the review status message.
        new ReviewStatus(config, globalSQL, plotSQL, regionSQL, discord, scheduler);

        serverManager.initOnlineServers();

        socketInitializer.accept(new ProxySocketHandler(chatManager, discord, userManager, serverManager, tabManager));

        started = true;
    }

    public void stop() {
        if (started) {
            // Show the disconnect message for all players in discord.
            if (discord != null) {
                // Get start time.
                long startTime = System.currentTimeMillis();
                long currentTime = System.currentTimeMillis();
                AtomicInteger users = new AtomicInteger((int) coreUserManager.countOnlineUsers());
                coreUserManager.runForEachOnline(user -> {
                    if (user.isOnline()) {
                        discord.sendConnectEmbed(LEAVE_MESSAGE, user.getName(), user.getUuid(), user.getPlayerSkin(), RED, (reply) -> users.decrementAndGet());
                    }
                });
                // Stop if it takes longer than 15 seconds.
                while (users.get() > 0 && (currentTime - startTime) < 15000) {
                    // Get the time for a pontetial timeout.
                    currentTime = System.currentTimeMillis();
                }

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
            }

            if (analytics != null) {
                analytics.shutdown();
            }

            // Tell the servers to remove all online users.
            if (chatHandler != null) {
                coreUserManager.getOnlineUsers().forEach(user -> chatHandler.handle(new OnlineUserRemove(user.getUuid())));
            }

            if (userManager != null) {
                userManager.removeAllUsers();
            }

            if (coreServerManager != null) {
                coreServerManager.shutdown();
            }
        }
    }

    private void setupDatabase() {
        // Set up MySQL
        try {

            DatabaseInit init = new DatabaseInit();

            String host = config.getString("host");
            int port = config.getInt("port");
            String username = config.getString("username");
            String password = config.getString("password");

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
            log.severe("Failed to connect to the database, please check that you have set the config values correctly: " + e.getMessage());
            log.severe("Disabling Proxy");
        }
    }
}
