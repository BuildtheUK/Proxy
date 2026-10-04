package org.btuk.proxy.api.server;

import lombok.extern.java.Log;
import org.btuk.proxy.api.impl.BuildingsApiImpl;
import org.btuk.proxy.api.impl.PlayerApiImpl;
import org.btuk.proxy.api.impl.StatsApiImpl;
import org.btuk.proxy.api.impl.StatusApiImpl;
import org.btuk.proxy.api.stripe.StripeApi;
import org.btuk.proxy.core.chat.ChatManager;
import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.role.RoleManager;
import org.btuk.proxy.core.service.MinecraftUserResolver;
import org.btuk.proxy.database.sql.GlobalSQL;
import org.btuk.proxy.database.sql.PlotSQL;
import org.btuk.proxy.database.sql.StripeSQL;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.server.ResourceConfig;

import java.net.URI;

@Log
public class ProxyApi {

    private HttpServer server;
    private final boolean enabled;
    private final int port;
    private final GlobalSQL globalSQL;
    private final ChatManager chatManager;
    private final PlotSQL plotSQL;
    private final StripeSQL stripeSQL;
    private final RoleManager roleManager;
    private final MinecraftUserResolver userResolver;
    private final Config config;

    public ProxyApi(boolean enabled, int port, GlobalSQL globalSQL, ChatManager chatManager, PlotSQL plotSQL) {
        this(enabled, port, globalSQL, chatManager, plotSQL, null, null, null, null);
    }

    public ProxyApi(boolean enabled, int port, GlobalSQL globalSQL, ChatManager chatManager, PlotSQL plotSQL,
                    StripeSQL stripeSQL, RoleManager roleManager, MinecraftUserResolver userResolver, Config config) {
        this.enabled = enabled;
        this.port = port;
        this.globalSQL = globalSQL;
        this.chatManager = chatManager;
        this.plotSQL = plotSQL;
        this.stripeSQL = stripeSQL;
        this.roleManager = roleManager;
        this.userResolver = userResolver;
        this.config = config;
    }

    public void start() {
        if (!enabled) {
            return;
        }

        int apiPort = port;
        if (apiPort == 0) {
            log.warning("API port is not set or 0, defaulting to 8080");
            apiPort = 8080;
        }

        String baseUri = "http://0.0.0.0:" + apiPort + "/api/";

        ResourceConfig rc = new ResourceConfig();

        // 1. Disable WADL warning
        rc.property("jersey.config.server.wadl.disableWadl", true);

        // 2. Bind SQL, ChatManager, Stripe, and Role dependencies for injection
        rc.register(new AbstractBinder() {
            @Override
            protected void configure() {
                if (globalSQL != null) bind(globalSQL).to(GlobalSQL.class);
                if (chatManager != null) bind(chatManager).to(ChatManager.class);
                if (plotSQL != null) bind(plotSQL).to(PlotSQL.class);
                if (stripeSQL != null) bind(stripeSQL).to(StripeSQL.class);
                if (roleManager != null) bind(roleManager).to(RoleManager.class);
                if (userResolver != null) bind(userResolver).to(MinecraftUserResolver.class);
                if (config != null) bind(config).to(Config.class);
            }
        });

        rc.register(StatusApiImpl.class);
        rc.register(PlayerApiImpl.class);
        rc.register(BuildingsApiImpl.class);
        rc.register(StatsApiImpl.class);
        rc.register(StripeApi.class);

        try {
            server = GrizzlyHttpServerFactory.createHttpServer(URI.create(baseUri), rc);
            log.info("API server started at " + baseUri);
        } catch (Exception e) {
            log.severe("Failed to start API server: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null && server.isStarted()) {
            server.shutdownNow();
            log.info("API server stopped");
        }
    }
}
