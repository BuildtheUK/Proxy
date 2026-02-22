package org.btuk.proxy.player;

import org.btuk.proxy.Proxy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.btuk.proxy.core.player.Player;
import org.btuk.proxy.core.player.PlayerManager;

public class ProxyPlayerManager implements PlayerManager {

    private final Proxy proxy;

    public ProxyPlayerManager(Proxy proxy) {
        this.proxy = proxy;
    }

    @Override
    public List<Player> getPlayers() {
        return proxy.getServer().getAllPlayers().stream().map(ProxyPlayer::new).collect(Collectors.toCollection(ArrayList::new));
    }
}
