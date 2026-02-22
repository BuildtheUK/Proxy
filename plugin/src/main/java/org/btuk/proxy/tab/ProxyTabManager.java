package org.btuk.proxy.tab;

import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import lombok.Getter;
import net.bteuk.network.lib.dto.AddTeamEvent;
import net.bteuk.network.lib.dto.TabPlayer;
import net.bteuk.network.lib.utils.ChatUtils;
import org.btuk.proxy.Proxy;
import org.btuk.proxy.player.ProxyPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.btuk.proxy.core.chat.ChatHandler;
import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.player.Player;
import org.btuk.proxy.core.tab.TabManager;
import org.btuk.proxy.core.user.CoreUserManager;
import org.btuk.proxy.core.user.User;

/**
 * Keeps track of all users and their tab information.
 * Sends updates to the servers when things change.
 */
public class ProxyTabManager implements TabManager {

    private final ProxyServer server;

    private final Config config;

    private final CoreUserManager coreUserManager;

    private final ChatHandler chatHandler;

    @Getter
    private final Set<TabPlayer> tabPlayers = new HashSet<>();

    private final Component HEADER;
    private final Component FOOTER;

    public ProxyTabManager(ProxyServer server, Config config, CoreUserManager coreUserManager, ChatHandler chatHandler) {
        this.server = server;
        this.config = config;
        this.coreUserManager = coreUserManager;
        this.chatHandler = chatHandler;
        this.HEADER = header();
        this.FOOTER = footer();

        // Update ping every 30 seconds.
        Proxy.getInstance().getServer().getScheduler().buildTask(Proxy.getInstance(), this::updatePing)
            .repeat(30L, TimeUnit.SECONDS)
            .schedule();
    }

    public Component header() {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        String header = config.getString("tab.header");
        return miniMessage.deserialize(header);
    }

    public Component footer() {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        String footer = config.getString("tab.footer");
        return miniMessage.deserialize(footer);
    }

    /**
     * Send an add team event for all players.
     */
    @Override
    public void sendAddTeam() {
        tabPlayers.forEach(this::sendAddTeam);
    }

    /**
     * Add the new user to the tablist.
     * Send the new player to all other users, excluding themselves.
     *
     * @param tabPlayer the {@link TabPlayer} to add
     */
    @Override
    public void addPlayer(TabPlayer tabPlayer) {
        tabPlayers.add(tabPlayer);
        coreUserManager.runForEach(user -> {
            if (!user.getUuid().equals(tabPlayer.getUuid()) && user.getPlayer() != null) {
                Player player = user.getPlayer();
                if (player instanceof ProxyPlayer proxyPlayer) {
                    proxyPlayer.getTabList().addEntry(createTabPlayer(user, tabPlayer));
                }
            }
        });
        // Send the add team event to servers.
        sendAddTeam(tabPlayer);
    }

    /**
     * Remove a user from the tablist.
     * Remove the user from the tablist of all users.
     *
     * @param uuid of the {@link TabPlayer} to remove
     */
    @Override
    public void removePlayer(String uuid) {
        TabPlayer tabPlayer = findTabPlayerByUuid(uuid);
        if (tabPlayer != null) {
            tabPlayers.remove(tabPlayer);
            coreUserManager.runForEach(user -> {
                if (user.getPlayer() != null) {
                    // Find the entries that match the player name.
                    Player player = user.getPlayer();
                    if (player instanceof ProxyPlayer proxyPlayer) {
                        Collection<TabListEntry> tablist = proxyPlayer.getTabList().getEntries();
                        List<TabListEntry> entriesToRemove = tablist.stream()
                            .filter(tabListEntry -> tabListEntry.getProfile().getName().equals(tabPlayer.getName()) && tabListEntry.isListed()).toList();
                        // Remove the entries by UUID.
                        entriesToRemove.forEach(tabListEntry -> proxyPlayer.getTabList().removeEntry(tabListEntry.getProfile().getId()));
                    }
                }
            });
        }
    }

    /**
     * Update a player in the tablist for all players.
     * This is used when the players displayname changes.
     * Triggers can be change in role, afk, mute.
     *
     * @param tabPlayer the tabplayer to update
     */
    @Override
    public void updatePlayer(TabPlayer tabPlayer) {
        // Find the tab player by uuid.
        TabPlayer currentTabPlayer = findTabPlayerByUuid(tabPlayer.getUuid());
        if (currentTabPlayer != null) {
            // Update the display name and ping.
            int ping = findPingForPlayer(tabPlayer.getUuid());
            if (ping > -1) {
                currentTabPlayer.setPing(ping);
                updatePlayerPing(currentTabPlayer.getName(), ping);
            }
            // If the primary role has changed update the players team.
            // This must happen before the tab has updated, else the sorting won't update.
            if (!tabPlayer.getPrimaryGroup().equals(currentTabPlayer.getPrimaryGroup())) {
                currentTabPlayer.setPrimaryGroup(tabPlayer.getPrimaryGroup());
                currentTabPlayer.setPrefix(tabPlayer.getPrefix());
                sendAddTeam(tabPlayer);
            }
            updatePlayerDisplayName(currentTabPlayer.getName(), tabPlayer);
        }
    }

    @Override
    public void updatePlayerByUuid(String uuid) {
        // Find the tab player by uuid.
        TabPlayer currentTabPlayer = findTabPlayerByUuid(uuid);
        if (currentTabPlayer != null) {
            updatePlayer(currentTabPlayer);
        }
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

    @Override
    public Optional<TabPlayer> getTabPlayer(String uuid) {
        return Optional.ofNullable(findTabPlayerByUuid(uuid));
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

    private void sendAddTeam(TabPlayer tabPlayer) {
        chatHandler.handle(new AddTeamEvent(tabPlayer.getName(), tabPlayer.getPrimaryGroup()));
    }

    /**
     * Update the ping in tab for all players.
     */
    private void updatePing() {
        server.getAllPlayers().forEach(player -> {
            TabPlayer tabPlayer = findTabPlayerByUuid(player.getUniqueId().toString());
            int ping = (int) player.getPing();
            if (tabPlayer != null && ping > -1) {
                tabPlayer.setPing(ping);
            }
        });
        server.getAllPlayers().forEach(player -> updatePingForTabList(player.getTabList().getEntries()));
    }

    private void updatePlayerPing(String name, int ping) {
        server.getAllPlayers().forEach(player -> updateLatency(player, name, ping));
    }

    private void updatePlayerDisplayName(String name, TabPlayer tabPlayer) {
        server.getAllPlayers().forEach(player -> {
            User user = coreUserManager.getUserByUuid(String.valueOf(player.getUniqueId()));
            if (user != null) {
                updateDisplayName(player, name, formattedName(user, tabPlayer));
            }
        });
    }

    private void updatePingForTabList(Collection<TabListEntry> tabEntries) {
        tabEntries.stream().filter(TabListEntry::isListed).forEach(tabEntry -> {
            int ping = findPingForTabPlayer(tabEntry.getProfile().getName());
            if (ping > -1) {
                tabEntry.setLatency(ping);
            }
        });
    }

    private Optional<TabListEntry> findTabListEntryForPlayer(Collection<TabListEntry> tabEntries, String playerName) {
        return tabEntries.stream().filter(tabEntry -> tabEntry.getProfile().getName().equals(playerName) && tabEntry.isListed()).findFirst();
    }

    private int findPingForTabPlayer(String name) {
        return tabPlayers.stream().filter(tabPlayer -> tabPlayer.getName().equals(name)).mapToInt(TabPlayer::getPing).findFirst().orElse(-1);
    }

    private int findPingForPlayer(String uuid) {
        return (int) server.getAllPlayers().stream()
            .filter(player -> player.getUniqueId().toString().equals(uuid))
            .mapToLong(com.velocitypowered.api.proxy.Player::getPing).findFirst().orElse(-1);
    }

    private @Nullable TabPlayer findTabPlayerByUuid(String uuid) {
        return tabPlayers.stream().filter(tabPlayer -> tabPlayer.getUuid().equals(uuid)).findFirst().orElse(null);
    }

    private TabListEntry createTabPlayer(User user, TabPlayer tabPlayer) {
        // Find player instance of TabPlayer.
        Optional<com.velocitypowered.api.proxy.Player> optionalPlayer = server.getAllPlayers().stream().filter(p -> p.getUniqueId().toString().equals(tabPlayer.getUuid())).findFirst();
        if (optionalPlayer.isPresent()) {
            com.velocitypowered.api.proxy.Player player = optionalPlayer.get();
            return TabListEntry.builder()
                .tabList(player.getTabList())
                .gameMode(1) // All players will be shown in creative
                .displayName(formattedName(user, tabPlayer))
                .profile(GameProfile.forOfflinePlayer(tabPlayer.getName()).withProperties(player.getGameProfileProperties()))
                .latency(tabPlayer.getPing())
                .listed(true)
                .build();
        } else {
            return null;
        }
    }


    private Component formattedName(User user, TabPlayer tabPlayer) {

        // Find the user.
        User userToAdd = coreUserManager.getUserByUuid(tabPlayer.getUuid());

        // Optional style that will overwrite any custom style.
        Style.Builder statusStyle = Style.style();

        Component name = ChatUtils.line(tabPlayer.getName());
        if (userToAdd != null) {
            if (userToAdd.isMuted()) {
                statusStyle.color(NamedTextColor.DARK_RED);
            } else if (user.isMuted(userToAdd)) {
                statusStyle.color(NamedTextColor.RED);
            }
            if (userToAdd.isAfk()) {
                statusStyle.decorate(TextDecoration.ITALIC);
                statusStyle.color(NamedTextColor.WHITE);
            }
            if (userToAdd.isFocusEnabled()) {
                statusStyle.decorate(TextDecoration.STRIKETHROUGH);
                statusStyle.color(NamedTextColor.WHITE);
            }
            name = userToAdd.getDisplayName();
        }

        // Apply the status overlay to the entire tree if exists.
        Style style = statusStyle.build();
        if (!style.isEmpty()) {
            name = applyStyleToTree(name, style);
        }

        // Add the prefix.
        if (tabPlayer.getPrefix() != null) {
            name = tabPlayer.getPrefix()
                .append(Component.space())
                .append(name);
        }

        return name;
    }

    private void updateLatency(com.velocitypowered.api.proxy.Player player, String name, int latency) {
        player.getTabList().getEntries().stream().filter(tabListEntry -> tabListEntry.getProfile().getName().equalsIgnoreCase(name) && tabListEntry.isListed()).findFirst().ifPresent(tabListEntry -> tabListEntry.setLatency(latency));
    }

    private void updateDisplayName(com.velocitypowered.api.proxy.Player player, String name, Component displayName) {
        player.getTabList().getEntries().stream().filter(tabListEntry -> tabListEntry.getProfile().getName().equalsIgnoreCase(name) && tabListEntry.isListed()).findFirst().ifPresent(tabListEntry -> tabListEntry.setDisplayName(displayName));
    }

    /**
     * Recursively applies the given style to every node in the component tree,
     * overriding child styles to ensure uniform propagation.
     */
    private static Component applyStyleToTree(Component component, Style styleToApply) {
        Component updated = component.style(styleToApply);

        if (!component.children().isEmpty()) {
            List<Component> updatedChildren = component.children().stream()
                .map(child -> applyStyleToTree(child, styleToApply))
                .toList();
            updated = updated.children(updatedChildren);
        }

        return updated;
    }
}
