package org.btuk.proxy.api.impl;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.btuk.proxy.api.model.PlayerBaseStats;
import org.btuk.proxy.api.model.TotalBaseStats;
import org.btuk.proxy.database.sql.GlobalSQL;
import org.btuk.proxy.database.sql.PlotSQL;

@Path("/stats")
@Produces(MediaType.APPLICATION_JSON)
public class StatsApiImpl {

    private final GlobalSQL globalSQL;
    private final PlotSQL plotSQL;

    public StatsApiImpl(@Context GlobalSQL globalSQL, @Context PlotSQL plotSQL) {
        this.globalSQL = globalSQL;
        this.plotSQL = plotSQL;
    }

    @GET
    @Path("/total")
    public Response getTotalStats() {
        try {
            // Fetch total stats from your database layer
            org.btuk.proxy.database.dto.TotalBaseStats stats = globalSQL.getTotalBaseStats();
            if (stats == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(stats).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GET
    @Path("/player")
    public Response getPlayerStats(@QueryParam("uuid") String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Player UUID is required")
                    .build();
        }

        try {
            // Fetch individual player stats from your database layer
            org.btuk.proxy.database.dto.PlayerBaseStats stats = globalSQL.getPlayerBaseStats(uuid);
            if (stats == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            int reviews = plotSQL.getPlayerTotalReviews(uuid);
            PlayerBaseStats combinedStats = new PlayerBaseStats();

            combinedStats.buildings(stats.buildings());
            combinedStats.tplls(stats.tplls());
            combinedStats.messagesSent(stats.messagesSent());
            combinedStats.reviewsCompleted(reviews);
            combinedStats.timePlayed(stats.timePlayed());

            return Response.ok(combinedStats).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }
}