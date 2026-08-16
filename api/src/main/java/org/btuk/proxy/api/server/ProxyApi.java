package org.btuk.proxy.api.server;

import lombok.extern.java.Log;
import org.btuk.proxy.api.impl.BuildingsApiImpl;
import org.btuk.proxy.api.impl.PlayerApiImpl;
import org.btuk.proxy.api.impl.StatusApiImpl;
import org.btuk.proxy.core.chat.ChatManager;
import org.btuk.proxy.database.sql.GlobalSQL;

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


    public ProxyApi(boolean enabled, int port, GlobalSQL globalSQL, ChatManager chatManager) {
        this.enabled = enabled;
        this.port = port;
        this.globalSQL = globalSQL;
        this.chatManager = chatManager;
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

        // 2. Bind SQL and ChatManager dependencies for injection
        rc.register(new AbstractBinder() {
            @Override
            protected void configure() {
                bind(globalSQL).to(GlobalSQL.class);
                bind(chatManager).to(ChatManager.class);
            }
        });

        // 3. Register the implementation CLASSES (or scan the package)
        rc.register(StatusApiImpl.class);
        rc.register(PlayerApiImpl.class);
        rc.register(BuildingsApiImpl.class);

//        ResourceConfig rc = new ResourceConfig()
//                .property("jersey.config.server.wadl.disableWadl", true)
//                .packages("org.btuk.proxy.api.impl");

//        ResourceConfig rc = new ResourceConfig()
//            .register(new StatusApiImpl())
//            .register(new PlayerApiImpl(globalSQL,chatManager))
//            .register(new BuildingsApiImpl(globalSQL));

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
