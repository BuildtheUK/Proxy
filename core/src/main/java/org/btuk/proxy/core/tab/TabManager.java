package org.btuk.proxy.core.tab;

import org.btuk.network.lib.dto.TabPlayer;
import org.btuk.proxy.core.user.User;

import java.util.Optional;

public interface TabManager {

    void updatePlayerInTablistOfPlayer(User user, User userToUpdate);

    void addPlayer(TabPlayer tabPlayer);

    void removePlayer(String uuid);

    void updatePlayer(TabPlayer tabPlayer);

    void updatePlayerByUuid(String uuid);

    void sendAddTeam();

    void sendAddTeam(TabPlayer tabPlayer);

    void sendTablist(User user);

    Optional<TabPlayer> getTabPlayer(String uuid);
}
