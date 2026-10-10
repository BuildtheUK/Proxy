package org.btuk.proxy.core.tab;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.btuk.network.lib.dto.AddTeamEvent;
import org.btuk.network.lib.dto.TabPlayer;
import org.btuk.network.lib.utils.ChatUtils;
import org.btuk.proxy.core.chat.ChatHandler;
import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.player.Player;
import org.btuk.proxy.core.scheduler.Scheduler;
import org.btuk.proxy.core.user.CoreUserManager;
import org.btuk.proxy.core.user.User;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public abstract class AbstractTabManager implements TabManager {

    private final Config config;

    protected final CoreUserManager coreUserManager;

    private final ChatHandler chatHandler;

    @Getter
    protected final Set<TabPlayer> tabPlayers = new HashSet<>();

    protected final Component HEADER;
    protected final Component FOOTER;

    public AbstractTabManager(Config config, CoreUserManager coreUserManager, ChatHandler chatHandler, Scheduler scheduler) {
        this.config = config;
        this.coreUserManager = coreUserManager;
        this.chatHandler = chatHandler;
        this.HEADER = header();
        this.FOOTER = footer();

        // Update ping every 30 seconds.
        scheduler.createRepeatingTask(this::updatePing, 0L, 30L, TimeUnit.SECONDS);
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
                addPlayerToTabList(player, user, tabPlayer);
            }
        });
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
                    removePlayerFromTabList(player, tabPlayer);
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

    @Override
    public Optional<TabPlayer> getTabPlayer(String uuid) {
        return Optional.ofNullable(findTabPlayerByUuid(uuid));
    }

    protected abstract void addPlayerToTabList(Player player, User user, TabPlayer tabPlayer);

    protected abstract void removePlayerFromTabList(Player player, TabPlayer tabPlayer);

    protected abstract void updatePlayerPing(String name, int ping);

    protected abstract void updatePlayerDisplayName(String name, TabPlayer tabPlayer);

    protected abstract int findPingForPlayer(String uuid);

    protected abstract void updatePing();

    protected int findPingForTabPlayer(String name) {
        return tabPlayers.stream().filter(tabPlayer -> tabPlayer.getName().equals(name)).mapToInt(TabPlayer::getPing).findFirst().orElse(-1);
    }

    protected @Nullable TabPlayer findTabPlayerByUuid(String uuid) {
        return tabPlayers.stream().filter(tabPlayer -> tabPlayer.getUuid().equals(uuid)).findFirst().orElse(null);
    }

    protected Component formattedName(User user, TabPlayer tabPlayer) {

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

    @Override
    public void sendAddTeam(TabPlayer tabPlayer) {
        chatHandler.handle(new AddTeamEvent(tabPlayer.getName(), tabPlayer.getPrimaryGroup()));
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

    private Component header() {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        String header = config.getString("tab.header");
        return miniMessage.deserialize(header);
    }

    private Component footer() {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        String footer = config.getString("tab.footer");
        return miniMessage.deserialize(footer);
    }
}
