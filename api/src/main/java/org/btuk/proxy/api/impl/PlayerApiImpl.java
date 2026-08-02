package org.btuk.proxy.api.impl;

import jakarta.ws.rs.core.Response;
import org.btuk.network.lib.dto.DirectMessage;
import org.btuk.network.lib.utils.ChatUtils;
import org.btuk.proxy.api.PlayerApi;
import org.btuk.proxy.api.model.Message;
import org.btuk.proxy.api.model.Player;
import org.btuk.proxy.core.chat.ChatManager;
import org.btuk.proxy.database.dto.PlayerDTO;
import org.btuk.proxy.database.sql.GlobalSQL;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.btuk.network.lib.enums.ChatChannels.GLOBAL;
import static org.btuk.proxy.core.utils.Constants.SERVER_SENDER;

public class PlayerApiImpl implements PlayerApi {

    private final GlobalSQL globalSQL;
    private final ChatManager chatManager;

    public PlayerApiImpl(GlobalSQL globalSQL, ChatManager chatManager) {
        this.globalSQL = globalSQL;this.chatManager = chatManager;
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

    @Override
    public Response getPlayerUsername(String uuid) {
        String username = globalSQL.getPlayerUsernameByUuid(uuid);
        if (uuid == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(username).build();
    }

    @Override
    public Response sendPlayerMessage(String playerID, Message message){

        try {
            String messagePlainText = message.getMessage();
            DirectMessage m = new DirectMessage(GLOBAL.getChannelName(), playerID.toString(), SERVER_SENDER, ChatUtils.success(messagePlainText), true);
            chatManager.sendDirectMessage(m);
            return Response.status(Response.Status.CREATED).build();
        }catch (Exception e)
        {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Failed to deliver message to player.")
                    .build();
        }


    }
}
