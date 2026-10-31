package org.btuk.proxy.server;

import com.velocitypowered.api.proxy.server.RegisteredServer;
import lombok.Getter;
import org.btuk.proxy.player.ProxyPlayer;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.btuk.proxy.core.player.Player;
import org.btuk.proxy.core.server.Server;
import org.btuk.proxy.core.utils.Time;

public class ProxyServer implements Server {

    @Getter
    private final RegisteredServer server;

    private volatile long lastPing = Time.currentTime();

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
            server.ping().get(3, TimeUnit.SECONDS);
            return true;
        } catch (TimeoutException | ExecutionException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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

    @Override
    public boolean equals(Object other) {
        return other instanceof ProxyServer && ((ProxyServer) other).server.equals(server);
    }

    @Override
    public int hashCode() {
        return Objects.hash(server);
    }
}
