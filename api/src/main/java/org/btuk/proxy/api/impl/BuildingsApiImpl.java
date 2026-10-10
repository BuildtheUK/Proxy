package org.btuk.proxy.api.impl;

import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.btuk.proxy.api.BuildingsApi;
import org.btuk.proxy.api.model.Building;
import org.btuk.proxy.api.model.BuildingCount;
import org.btuk.proxy.api.model.BuildingGridResponse;
import org.btuk.proxy.api.model.GridCell;
import org.btuk.proxy.database.dto.BuildingDTO;
import org.btuk.proxy.database.dto.GridCellDTO;
import org.btuk.proxy.database.sql.GlobalSQL;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/buildings")
public class BuildingsApiImpl implements BuildingsApi {

    private final GlobalSQL globalSQL;

    @Inject
    public BuildingsApiImpl(GlobalSQL globalSQL) {
        this.globalSQL = globalSQL;
    }

    @Override
    public Response getBuildingsByArea(Double minLat, Double maxLat, Double minLon, Double maxLon, UUID playerUuid) {
        String playerUuidStr = playerUuid != null ? playerUuid.toString() : null;
        List<BuildingDTO> buildingDTOs = globalSQL.getBuildingsByArea(minLat, maxLat, minLon, maxLon, playerUuidStr);

        List<Building> buildings = buildingDTOs.stream()
                .map(this::mapBuilding)
                .collect(Collectors.toList());
        return Response.ok(buildings).build();
    }

    @Override
    public Response getBuildingCount(List<UUID> playerUuid, Double minLat, Double maxLat, Double minLon, Double maxLon, Boolean isPublic, Boolean playerBuilt) {
        List<String> playerUuidStrs = null;
        if (playerUuid != null && !playerUuid.isEmpty()) {
            playerUuidStrs = playerUuid.stream()
                    .map(UUID::toString)
                    .collect(Collectors.toList());
        }

        int count = globalSQL.getBuildingCount(playerUuidStrs, minLat, maxLat, minLon, maxLon, isPublic, playerBuilt);

        BuildingCount buildingCount = new BuildingCount();
        buildingCount.setCount(count);

        return Response.ok(buildingCount).build();
    }

    @Override
    public Response getBuildingGridCount(Double minLat, Double maxLat, Double minLon, Double maxLon, Double stepLat, Double stepLon, UUID playerUuid) {
        if (stepLat == null || stepLat <= 0 || stepLon == null || stepLon <= 0) {
            return Response.status(Response.Status.BAD_REQUEST).entity("stepLat and stepLon must be greater than 0").build();
        }

        String playerUuidStr = (playerUuid != null) ? playerUuid.toString() : null;

        List<GridCellDTO> dbGridCounts = globalSQL.getBuildingGridCounts(minLat, maxLat, minLon, maxLon, stepLat, stepLon, playerUuidStr);

        List<GridCell> cells = new ArrayList<>();

        for (GridCellDTO dbCell : dbGridCounts) {
            GridCell cell = new GridCell();
            cell.setLat(dbCell.lat());
            cell.setLon(dbCell.lon());
            cell.setMinLat(dbCell.minLat());
            cell.setMaxLat(dbCell.maxLat());
            cell.setMinLon(dbCell.minLon());
            cell.setMaxLon(dbCell.maxLon());
            cell.setRow(dbCell.row());
            cell.setCol(dbCell.col());
            cell.setCount(dbCell.count());
            cells.add(cell);
        }

        BuildingGridResponse response = new BuildingGridResponse();
        response.setCells(cells);

        return Response.ok(response).build();
    }

    private Building mapBuilding(BuildingDTO dto) {
        Building building = new Building();
        building.setBuildingId(dto.buildingId());
        building.setPlayerName(dto.playerName());
        building.setPlayerId(dto.playerId() != null ? UUID.fromString(dto.playerId()) : null);
        building.setIsPublic(dto.isPublic());
        building.setPlayerBuilt(dto.playerBuilt());
        if (dto.timeAdded() != null) {
            building.setTimeAdded(Date.from(dto.timeAdded().toInstant(ZoneOffset.UTC)));
        }
        building.setLat(dto.lat());
        building.setLon(dto.lon());
        return building;
    }
}
