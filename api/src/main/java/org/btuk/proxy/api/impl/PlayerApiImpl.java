package org.btuk.proxy.api.impl;

import jakarta.ws.rs.core.Response;
import org.btuk.proxy.api.PlayerApi;
import org.btuk.proxy.api.model.Player;
import org.btuk.proxy.database.dto.PlayerDTO;
import org.btuk.proxy.database.sql.GlobalSQL;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PlayerApiImpl implements PlayerApi {

    private final GlobalSQL globalSQL;

    public PlayerApiImpl(GlobalSQL globalSQL) {
        this.globalSQL = globalSQL;
    }

    @Override
    public Response getOnlinePlayers() {
        List<PlayerDTO> onlinePlayers = globalSQL.getOnlinePlayers();
        List<Player> players = onlinePlayers.stream()
                .map(dto -> {
                    Player p = new Player();
                    p.setUuid(UUID.fromString(dto.uuid()));
                    p.setName(dto.name());
                    return p;
                })
                .collect(Collectors.toList());
        return Response.ok(players).build();
    }

    @Override
    public Response getPlayerUuid(String name) {
        String uuid = globalSQL.getPlayerUuidByName(name);
        if (uuid == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(UUID.fromString(uuid)).build();
    }
}
