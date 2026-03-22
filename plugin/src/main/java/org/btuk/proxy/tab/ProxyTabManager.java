package org.btuk.proxy.tab;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import lombok.extern.java.Log;
import net.bteuk.network.lib.dto.TabPlayer;

import org.btuk.proxy.core.scheduler.Scheduler;
import org.btuk.proxy.core.tab.AbstractTabManager;
import org.btuk.proxy.player.ProxyPlayer;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.btuk.proxy.core.chat.ChatHandler;
import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.player.Player;
import org.btuk.proxy.core.user.CoreUserManager;
import org.btuk.proxy.core.user.User;

/**
 * Keeps track of all users and their tab information.
 * Sends updates to the servers when things change.
 */
public class ProxyTabManager extends AbstractTabManager {

    private final ProxyServer server;

    public ProxyTabManager(ProxyServer server, Scheduler scheduler, Config config, CoreUserManager coreUserManager, ChatHandler chatHandler) {
        super(config, coreUserManager, chatHandler, scheduler);
        this.server = server;
    }

    /**
     * Send the full tablist to a user.
     * This is used when a user connects to a server.
     * Adjust display names for muted players.
     */
    @Override
    public void sendTablist(User user) {
        // Player must exist.
        if (user.getPlayer() != null) {
            List<TabListEntry> tabListEntries = new ArrayList<>();
            tabPlayers.forEach(tabPlayer -> {
                TabListEntry entry = createTabPlayer(user, tabPlayer);
                if (entry != null) {
                    tabListEntries.add(entry);
                }
            });
            Player player = user.getPlayer();
            if (player instanceof ProxyPlayer proxyPlayer) {
                proxyPlayer.getTabList().addEntries(tabListEntries);
            }

            // Send header and footer.
            user.getPlayer().sendPlayerListHeaderAndFooter(HEADER, FOOTER);
        }
    }

    /**
     * Update a specific user in the tablist of another user.
     * This can be used specifically when you do a personal mute of a player.
     *
     * @param userToGetTablist the user to update the tablist for
     * @param userToUpdate     the user to update in the tablist
     */
    @Override
    public void updatePlayerInTablistOfPlayer(User userToGetTablist, User userToUpdate) {
        TabPlayer tabPlayer = findTabPlayerByUuid(userToUpdate.getUuid());
        Player player = userToGetTablist.getPlayer();
        if (player instanceof ProxyPlayer proxyPlayer) {
            Optional<TabListEntry> optionalTabEntry = findTabListEntryForPlayer(proxyPlayer.getTabList().getEntries(), userToUpdate.getName());
            optionalTabEntry.ifPresent(tabEntry -> {
                if (tabPlayer != null) {
                    // Update the display name.
                    tabEntry.setDisplayName(formattedName(userToGetTablist, tabPlayer));
                }
            });
        }
    }

    @Override
    protected void addPlayerToTabList(Player player, User user, TabPlayer tabPlayer) {
        if (player instanceof ProxyPlayer proxyPlayer) {
            proxyPlayer.getTabList().addEntry(createTabPlayer(user, tabPlayer));
        }
    }

    @Override
    protected void removePlayerFromTabList(Player player, TabPlayer tabPlayer) {
        if (player instanceof ProxyPlayer proxyPlayer) {
            Collection<TabListEntry> tablist = proxyPlayer.getTabList().getEntries();
            List<TabListEntry> entriesToRemove = tablist.stream()
                .filter(tabListEntry -> tabListEntry.getProfile().getName().equals(tabPlayer.getName()) && tabListEntry.isListed()).toList();
            // Remove the entries by UUID.
            entriesToRemove.forEach(tabListEntry -> proxyPlayer.getTabList().removeEntry(tabListEntry.getProfile().getId()));
        }
    }

    /**
     * Update the ping in tab for all players.
     */
    @Override
    protected void updatePing() {
        server.getAllPlayers().forEach(player -> {
            TabPlayer tabPlayer = findTabPlayerByUuid(player.getUniqueId().toString());
            int ping = (int) player.getPing();
            if (tabPlayer != null && ping > -1) {
                tabPlayer.setPing(ping);
            }
        });
        server.getAllPlayers().forEach(player -> updatePingForTabList(player.getTabList().getEntries()));
    }

    @Override
    protected void updatePlayerPing(String name, int ping) {
        server.getAllPlayers().forEach(player -> updateLatency(player, name, ping));
    }

    @Override
    protected void updatePlayerDisplayName(String name, TabPlayer tabPlayer) {
        server.getAllPlayers().forEach(player -> {
            User user = coreUserManager.getUserByUuid(String.valueOf(player.getUniqueId()));
            if (user != null) {
                updateDisplayName(player, name, formattedName(user, tabPlayer));
            }
        });
    }

    @Override
    protected int findPingForPlayer(String uuid) {
        return (int) server.getAllPlayers().stream()
            .filter(player -> player.getUniqueId().toString().equals(uuid))
            .mapToLong(com.velocitypowered.api.proxy.Player::getPing).findFirst().orElse(-1);
    }

    private TabListEntry createTabPlayer(User user, TabPlayer tabPlayer) {
        Optional<com.velocitypowered.api.proxy.Player> optionalPlayer = server.getAllPlayers().stream().filter(p -> p.getUniqueId().toString().equals(tabPlayer.getUuid())).findFirst();
        Optional<com.velocitypowered.api.proxy.Player> tablistPlayer = server.getAllPlayers().stream().filter(p -> p.getUniqueId().toString().equals(user.getUuid())).findFirst();
        if (optionalPlayer.isPresent() && tablistPlayer.isPresent()) {
            return TabListEntry.builder()
                .tabList(tablistPlayer.get().getTabList())
                .gameMode(1) // All players will be shown in creative
                .displayName(formattedName(user, tabPlayer))
                .profile(GameProfile.forOfflinePlayer(tabPlayer.getName()).withProperties(optionalPlayer.get().getGameProfileProperties()))
                .latency(tabPlayer.getPing())
                .listed(true)
                .build();
        } else {
            return null;
        }
    }

    private void updateLatency(com.velocitypowered.api.proxy.Player player, String name, int latency) {
        player.getTabList().getEntries().stream().filter(tabListEntry -> tabListEntry.getProfile().getName().equalsIgnoreCase(name) && tabListEntry.isListed()).findFirst().ifPresent(tabListEntry -> tabListEntry.setLatency(latency));
    }

    private void updateDisplayName(com.velocitypowered.api.proxy.Player player, String name, Component displayName) {
        player.getTabList().getEntries().stream().filter(tabListEntry -> tabListEntry.getProfile().getName().equalsIgnoreCase(name) && tabListEntry.isListed()).forEach(tabListEntry -> {
            tabListEntry.setDisplayName(displayName);
        });
    }

    private Optional<TabListEntry> findTabListEntryForPlayer(Collection<TabListEntry> tabEntries, String playerName) {
        return tabEntries.stream().filter(tabEntry -> tabEntry.getProfile().getName().equals(playerName) && tabEntry.isListed()).findFirst();
    }

    private void updatePingForTabList(Collection<TabListEntry> tabEntries) {
        tabEntries.stream().filter(TabListEntry::isListed).forEach(tabEntry -> {
            int ping = findPingForTabPlayer(tabEntry.getProfile().getName());
            if (ping > -1) {
                tabEntry.setLatency(ping);
            }
        });
    }
}
