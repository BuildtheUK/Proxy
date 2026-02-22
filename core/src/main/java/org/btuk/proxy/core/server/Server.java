package org.btuk.proxy.core.server;

import java.util.List;

import org.btuk.proxy.core.player.Player;

public interface Server {

    List<Player> getPlayers();

    String getName();

    boolean canPing();

    void setLastPing(long time);

    long getLastPing();
}
