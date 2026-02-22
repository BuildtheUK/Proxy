package org.btuk.proxy.server;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Getter;
import org.btuk.proxy.player.ProxyPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.btuk.proxy.core.player.Player;
import org.btuk.proxy.core.server.Server;
import org.btuk.proxy.core.utils.Time;

public class ProxyServer implements Server {

    @Getter
    private final RegisteredServer server;

    private long lastPing = Time.currentTime();

    public ProxyServer(RegisteredServer server) {
        this.server = server;
    }

    @Override
    public List<Player> getPlayers() {
        return server.getPlayersConnected().stream().map(ProxyPlayer::new).collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public String getName() {
        return server.getServerInfo().getName();
    }

    @Override
    public boolean canPing() {
        try {
            server.ping().get();
            return true;
        } catch (InterruptedException | ExecutionException e) {
            return false;
        }
    }

    @Override
    public void setLastPing(long time) {
        lastPing = time;
    }

    @Override
    public long getLastPing() {
        return lastPing;
    }
}
