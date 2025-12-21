package net.bteuk.proxy;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.player.TabListEntry;
import com.velocitypowered.api.util.GameProfile;
import lombok.Getter;
import net.bteuk.network.lib.dto.AddTeamEvent;
import net.bteuk.network.lib.dto.TabPlayer;
import net.bteuk.network.lib.utils.ChatUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Keeps track of all users and their tab information.
 * Sends updates to the servers when things change.
 */
public class TabManager {

    private final ProxyServer server;

    @Getter
    private final Set<TabPlayer> tabPlayers = new HashSet<>();

    private static final Component HEADER = Component.text("BTE ", NamedTextColor.AQUA, TextDecoration.BOLD)
            .append(Component.text("UK", NamedTextColor.DARK_AQUA, TextDecoration.BOLD))
            .append(Component.newline());

    private static final Component FOOTER = Component.newline()
            .append(ChatUtils.line("Server Info: "))
            .append(Component.text("/help", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(ChatUtils.line("More Info: "))
            .append(Component.text("/discord", NamedTextColor.GRAY));

    public TabManager(ProxyServer server) {
        this.server = server;

        // Update ping every 30 seconds.
        Proxy.getInstance().getServer().getScheduler().buildTask(Proxy.getInstance(), this::updatePing)
                .repeat(30L, TimeUnit.SECONDS)
                .schedule();
    }

    /**
     * Send an add team event for all players.
     */
    public void sendAddTeam() {
        tabPlayers.forEach(this::sendAddTeam);
    }

    /**
     * Add the new user to the tablist.
     * Send the new player to all other users, excluding themselves.
     *
     * @param tabPlayer the {@link TabPlayer} to add
     */
    public void addPlayer(TabPlayer tabPlayer) {
        tabPlayers.add(tabPlayer);
        Proxy.getInstance().getUserManager().getUsers().forEach(user -> {
            if (!user.getUuid().equals(tabPlayer.getUuid()) && user.getPlayer() != null) {
                user.getPlayer().getTabList().addEntry(createTabPlayer(user, tabPlayer));
            }
        });
        // Send add team event to servers.
        sendAddTeam(tabPlayer);
    }

    /**
     * Remove a user from the tablist.
     * Remove the user from the tablist of all users.
     *
     * @param uuid of the {@link TabPlayer} to remove
     */
    public void removePlayer(String uuid) {
        TabPlayer tabPlayer = findTabPlayerByUuid(uuid);
        if (tabPlayer != null) {
            tabPlayers.remove(tabPlayer);
            Proxy.getInstance().getUserManager().getUsers().forEach(user -> {
                if (user.getPlayer() != null) {
                    // Find the entries that match the player name.
                    Collection<TabListEntry> tablist = user.getPlayer().getTabList().getEntries();
                    List<TabListEntry> entriesToRemove = tablist.stream()
                            .filter(tabListEntry -> tabListEntry.getProfile().getName().equals(tabPlayer.getName()) && tabListEntry.isListed()).toList();
                    // Remove the entries by UUID.
                    entriesToRemove.forEach(tabListEntry -> user.getPlayer().getTabList().removeEntry(tabListEntry.getProfile().getId()));
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
            user.getPlayer().getTabList().addEntries(tabListEntries);

            // Send header and footer.
            user.getPlayer().sendPlayerListHeaderAndFooter(HEADER, FOOTER);
        }
    }

    public void sendAddTeam(TabPlayer tabPlayer) {
        Proxy.getInstance().getChatHandler().handle(new AddTeamEvent(tabPlayer.getName(), tabPlayer.getPrimaryGroup()));
    }

    /**
     * Update a specific user in the tablist of another user.
     * This can be used specifically when you do a personal mute of a player.
     *
     * @param userToGetTablist the user to update the tablist for
     * @param userToUpdate the user to update in the tablist
     */
    public void updatePlayerInTablistOfPlayer(User userToGetTablist, User userToUpdate) {
        TabPlayer tabPlayer = findTabPlayerByUuid(userToUpdate.getUuid());
        Optional<TabListEntry> optionalTabEntry = findTabListEntryForPlayer(userToGetTablist.getPlayer().getTabList().getEntries(), userToUpdate.getName());
        optionalTabEntry.ifPresent(tabEntry -> {
            if (tabPlayer != null) {
                // Update the display name.
                tabEntry.setDisplayName(formattedName(userToGetTablist, tabPlayer));
            }
        });
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
            User user = Proxy.getInstance().getUserManager().getUserByUuid(String.valueOf(player.getUniqueId()));
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
        return (int) Proxy.getInstance().getServer().getAllPlayers().stream()
                .filter(player -> player.getUniqueId().toString().equals(uuid))
                .mapToLong(Player::getPing).findFirst().orElse(-1);
    }

    private TabPlayer findTabPlayerByUuid(String uuid) {
        return tabPlayers.stream().filter(tabPlayer -> tabPlayer.getUuid().equals(uuid)).findFirst().orElse(null);
    }

    private TabListEntry createTabPlayer(User user, TabPlayer tabPlayer) {
        // Find player instance of TabPlayer.
        Optional<Player> optionalPlayer = server.getAllPlayers().stream().filter(p -> p.getUniqueId().toString().equals(tabPlayer.getUuid())).findFirst();
        if (optionalPlayer.isPresent()) {
            Player player = optionalPlayer.get();
            return TabListEntry.builder()
                    .tabList(user.getPlayer().getTabList())
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
        User userToAdd = Proxy.getInstance().getUserManager().getUserByUuid(tabPlayer.getUuid());

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

    private void updateLatency(Player player, String name, int latency) {
        player.getTabList().getEntries().stream().filter(tabListEntry -> tabListEntry.getProfile().getName().equalsIgnoreCase(name) && tabListEntry.isListed()).findFirst().ifPresent(tabListEntry -> tabListEntry.setLatency(latency));
    }

    private void updateDisplayName(Player player, String name, Component displayName) {
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
