package org.btuk.proxy.core.tab;

import net.bteuk.network.lib.dto.TabPlayer;

import java.util.Optional;

import org.btuk.proxy.core.user.User;

public interface TabManager {

    void updatePlayerInTablistOfPlayer(User user, User userToUpdate);

    void addPlayer(TabPlayer tabPlayer);

    void removePlayer(String uuid);

    void updatePlayer(TabPlayer tabPlayer);

    void updatePlayerByUuid(String uuid);

    void sendAddTeam();

    void sendTablist(User user);

    Optional<TabPlayer> getTabPlayer(String uuid);
}
