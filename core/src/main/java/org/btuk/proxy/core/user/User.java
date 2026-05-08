package org.btuk.proxy.core.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.TeleportEvent;
import net.bteuk.network.lib.dto.UserConnectReply;
import net.bteuk.network.lib.dto.UserConnectRequest;
import net.bteuk.network.lib.enums.ChatChannels;
import net.bteuk.network.lib.enums.TeleportRequestType;
import net.bteuk.network.lib.utils.ChatUtils;

import org.btuk.proxy.core.exceptions.ServerNotFoundException;
import org.btuk.proxy.core.utils.TeleportRequest;

import org.btuk.proxy.core.chat.automod.AutoMod;
import org.btuk.proxy.core.chat.automod.AutoModFlag;
import org.btuk.proxy.core.chat.automod.AutoModFlagRule;
import org.btuk.proxy.core.chat.automod.AutoModMatch;
import org.btuk.proxy.core.chat.automod.AutoModRule;
import org.btuk.proxy.database.dto.AutoModFlagDTO;
import org.btuk.proxy.database.sql.GlobalSQL;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.btuk.proxy.core.chat.ChatHandler;
import org.btuk.proxy.core.player.Player;
import org.btuk.proxy.core.scheduler.ScheduledTask;
import org.btuk.proxy.core.scheduler.Scheduler;
import org.btuk.proxy.core.scheduler.TaskStatus;
import org.btuk.proxy.core.tab.TabManager;
import org.btuk.proxy.core.utils.Analytics;
import org.btuk.proxy.core.utils.SwitchServer;
import org.btuk.proxy.core.utils.Time;

import static org.btuk.proxy.core.utils.Constants.SERVER_SENDER;

/**
 * User object, stored specific information about the user.
 */
@Log
public class User {

    @Getter
    private boolean online = true;

    /**
     * Indicator for new users, so the database object is created when fetching information.
     */
    @Setter
    private boolean newUser = false;

    @Getter
    private final String uuid;

    /**
     * The Proxy {@link Player}, this may be null if the user is not currently online.
     */
    @Getter
    @Setter
    private Player player;

    @Getter
    private final String name;

    @Getter
    private Component displayName;

    @Getter
    private final String playerSkin;

    @Getter
    @Setter
    private String server;

    @Getter
    @Setter
    private String primaryRole;

    /**
     * List of muted users for this session
     */
    private final Set<User> mutedUsers = new HashSet<>();

    /**
     * List of channels the user can read
     */
    @Getter
    private final Set<String> channels = new HashSet<>();

    @Getter
    private boolean afk = false;

    private ScheduledTask disconnectTask;

    /**
     * Utility reference to the database.
     */
    private final GlobalSQL globalSQL;

    //Information for online-time logging.
    //Records when the player online-time was last logged.
    public long last_time_log = Time.currentTime();
    //Total active time in current session.
    public long active_time = 0L;

    @Getter
    @Setter
    private SwitchServer switchServer = null;

    @Getter
    private boolean focusEnabled;

    @Getter
    @Setter
    private boolean blockNextDisconnect = false;

    @Getter
    @Setter
    private int previousPlotSubmissionCount = 0;

    @Getter
    @Setter
    private int previousPlotVerificationCount = 0;

    // Used to store the id of the last user a player messaged or was messaged by.
    @Getter
    @Setter
    private String lastMessagedUserID = null;

    @Getter
    @Setter
    private long lastPing;

    @Getter
    private final List<AutoModFlag> autoModFlags = new ArrayList<>();

    private List<TeleportRequest> teleportRequests = new ArrayList<>();

    private final ChatHandler chatHandler;
    private final TabManager tabManager;
    private final Analytics analytics;
    private final Scheduler scheduler;

    public User(UserConnectRequest request, GlobalSQL globalSQL, ChatHandler chatHandler, TabManager tabManager, Analytics analytics, Scheduler scheduler, AutoMod autoMod) {
        this.uuid = request.getUuid();
        this.name = request.getName();
        this.playerSkin = request.getPlayerSkin();
        this.channels.addAll(request.getChannels());

        this.globalSQL = globalSQL;
        this.chatHandler = chatHandler;
        this.tabManager = tabManager;
        this.analytics = analytics;
        this.scheduler = scheduler;

        this.lastPing = Time.currentTime();

        setDisplayName();

        loadAutoModFlags(autoMod.getRules());
    }

    public void setDisplayName() {
        String displayName = globalSQL.getString("SELECT display_name FROM player_data WHERE uuid='" + uuid + "';");
        if (displayName != null) {
            this.displayName = GsonComponentSerializer.gson().deserialize(displayName);
        } else {
            this.displayName = ChatUtils.line(this.name);
        }
    }

    public Component updateDisplayName(Component newDisplayName) {
        // Assert whether the display name is valid.
        if (PlainTextComponentSerializer.plainText().serialize(newDisplayName).length() > 16) {
            chatHandler.handle(new DirectMessage(ChatChannels.GLOBAL.getChannelName(), this.uuid, SERVER_SENDER, ChatUtils.error("Your nickname must not exceed 16 characters."), false));
            return null;
        }
        // Strip any formatting.
        newDisplayName = stripDecorations(newDisplayName);

        // If there is no colour, explicitly set it to white.
        newDisplayName = newDisplayName.colorIfAbsent(NamedTextColor.WHITE);

        String displayName = GsonComponentSerializer.gson().serialize(newDisplayName);
        globalSQL.update("UPDATE player_data SET display_name=? WHERE uuid=?;", displayName, uuid);
        this.displayName = newDisplayName;

        // Update TAB.
        tabManager.updatePlayerByUuid(uuid);
        chatHandler.handle(new DirectMessage(ChatChannels.GLOBAL.getChannelName(), this.uuid, SERVER_SENDER, ChatUtils.success("Set nickname to ").append(newDisplayName), false));
        return newDisplayName;
    }

    /**
     * The user has disconnected from the network.
     * Store their user for 5 minutes before removing it.
     * This allows their local settings to remain stored in case they reconnect.
     */
    public void disconnect(Runnable runnable) {
        long time = Time.currentTime();

        //Set last_online time in playerdata.
        globalSQL.update("UPDATE player_data SET last_online=" + time + " WHERE UUID='" + uuid + "';");

        analytics.save(this, Time.getDate(time), time);
        online = false;
        // Run a delayed task to remove the user.
        disconnectTask = scheduler.createDelayedTask(runnable, 5L, TimeUnit.MINUTES);
    }

    /**
     * The user has reconnected to the network.
     * This is fired because their user instance was still stored.
     * Cancel the disconnect task that was scheduled.
     */
    public void reconnect() {
        last_time_log = Time.currentTime();
        online = true;
        // Can't be afk on reconnect.
        afk = false;
        if (disconnectTask != null && disconnectTask.getStatus() == TaskStatus.SCHEDULED) {
            disconnectTask.cancel();
        }
        disconnectTask = null;
    }

    /**
     * Delete the user instance.
     */
    public void delete() {
        // If the disconnectTask is running cancel.
        if (disconnectTask != null && disconnectTask.getStatus() == TaskStatus.SCHEDULED) {
            disconnectTask.cancel();
        }
    }

    public void mute(User user) {
        mutedUsers.add(user);
    }

    public void unmute(User user) {
        mutedUsers.remove(user);
    }

    /**
     * Check if the user is muted for this user.
     *
     * @param user the user to check
     * @return boolean if the user is muted by this user
     */
    public boolean isMuted(User user) {
        return mutedUsers.contains(user);
    }

    /**
     * Check whether this user is globally muted.
     *
     * @return boolean if the user is muted
     */
    public boolean isMuted() {
        return (globalSQL.hasRow("SELECT uuid FROM moderation WHERE uuid='" + uuid + "' AND end_time>" + Time.currentTime() + " AND type='mute';"));
    }

    /**
     * Create a {@link UserConnectReply} for the user.
     * If the User object in the database is missing, create it.
     *
     * @return the {@link UserConnectReply}
     */
    public UserConnectReply createUserConnectReply() {

        // Create database object if not exists.
        if (newUser) {
            if (!globalSQL.createUser(uuid, name, playerSkin)) {
                // We don't want to send a reply to the server since this could cause issues.
                // The user won't be able to do anything, so this is not a perfect solution.
                throw new RuntimeException("Failed to create user " + uuid + " in database.");
            }
            newUser = false;
        }

        // TODO: Add a potential join event.

        return new UserConnectReply(
            this.uuid,
            isNavigatorEnabled(),
            isTeleportEnabled(),
            isNightvisionEnabled(),
            getChatChannel(),
            isTipsEnabled(),
            getOfflineMessages(),
            this.focusEnabled,
            this.displayName
        );
    }

    public void setAfk(boolean afk) {
        if (afk) {
            long time = Time.currentTime();
            //Update playtime, and pause it.
            analytics.save(this, Time.getDate(time), time);
        } else {
            //Reset last logged time.
            last_time_log = Time.currentTime();
        }
        this.afk = afk;
    }

    public void setFocusEnabled(boolean focusEnabled) {
        this.focusEnabled = focusEnabled;
        // Update Tab for this player.
        tabManager.updatePlayerByUuid(uuid);
    }

    public void clearJoinEvent() {
        globalSQL.update("DELETE FROM join_events WHERE uuid='" + uuid + "';");
    }

    public void setNavigatorEnabled(boolean enabled) {
        globalSQL.update("UPDATE player_data SET navigator=" + enabled + " WHERE uuid='" + uuid + "';");
    }

    private boolean isNavigatorEnabled() {
        return globalSQL.getBoolean("SELECT navigator FROM player_data WHERE uuid='" + uuid + "';");
    }

    public void setTeleportEnabled(boolean enabled) {
        globalSQL.update("UPDATE player_data SET teleport_enabled=" + enabled + " WHERE uuid='" + uuid + "';");
    }

    private boolean isTeleportEnabled() {
        return globalSQL.getBoolean("SELECT teleport_enabled FROM player_data WHERE uuid='" + uuid + "';");
    }

    public void setNightvisionEnabled(boolean enabled) {
        globalSQL.update("UPDATE player_data SET nightvision_enabled=" + enabled + " WHERE uuid='" + uuid + "';");
    }

    private boolean isNightvisionEnabled() {
        return globalSQL.getBoolean("SELECT nightvision_enabled FROM player_data WHERE uuid='" + uuid + "';");
    }

    public void setChatChannel(String channel) {
        globalSQL.update("UPDATE player_data SET chat_channel='" + channel + "' WHERE uuid='" + uuid + "';");
    }

    public void setName(String name) {
        // Check if the name is not in use with another user, else correct that.
        String uuidForName = globalSQL.getString("SELECT uuid FROM player_data WHERE name='" + name + "';");
        if (uuidForName != null && !uuid.equals(uuidForName)) {
            // Another user has this username, fix that.
            // Update the new name asynchronously.
            updateNameAsync(uuidForName);
            globalSQL.update("UPDATE player_data SET name='" + name + "' WHERE uuid='" + uuid + "';");
        } else if (uuidForName == null && !newUser) {
            // No user exists with this name, set the name.
            globalSQL.update("UPDATE player_data SET name='" + name + "' WHERE uuid='" + uuid + "';");
        }
    }

    public Component teleportRequest(User target) {
        Optional<TeleportRequest> optionalRequest = teleportRequests.stream().filter(request -> request.getTarget().equals(target)).findFirst();
        if (optionalRequest.isPresent()) {
            TeleportRequest teleportRequest = optionalRequest.get();
            if (teleportRequest.isDenied()) {
                return ChatUtils.error("%s has denied your previous teleport request, please wait before requesting again.", target.getName());
            } else {
                return ChatUtils.error("You have already requested to teleport to %s", target.getName());
            }
        } else if (target.isMuted(this)) {
            return ChatUtils.error("%s currently has you muted, unable to send request.", target.getName());
        } else if (target.isFocusEnabled()) {
            return ChatUtils.error("%s is currently in focus mode, unable to send request.", target.getName());
        } else if (isMuted()) {
            return ChatUtils.error("You are currently muted, unable to send request.");
        }
        teleportRequests.add(new TeleportRequest(scheduler, this, target));
        chatHandler.handle(new DirectMessage(ChatChannels.GLOBAL.getChannelName(), target.getUuid(), SERVER_SENDER, ChatUtils.success("%s has requested to teleport to you, type %s to accept or %s to deny.", name, "/tpaccept " + name, "/tpdeny " + name), false));
        return ChatUtils.success("Requested to teleport to %s.", target.getDisplayName());
    }

    public Component acceptTeleportRequest(User target) {
        Optional<TeleportRequest> optionalRequest = teleportRequests.stream().filter(request -> request.getTarget().equals(target)).findFirst();
        if (optionalRequest.isPresent()) {
            TeleportRequest teleportRequest = optionalRequest.get();
            teleportRequest.acceptRequest();
            TeleportEvent event = new TeleportEvent(this.uuid, target.getUuid(), TeleportRequestType.ACCEPT);
            try {
                chatHandler.handle(event, this.server);
            } catch (ServerNotFoundException e) {
                log.severe("Server: " + this.server + " not found for teleport event, even though it's set for this user: " + this.name);
                return ChatUtils.error("An error occurred, please contact a server administrator.");
            }
            return ChatUtils.success("Accepted teleport request from %s.", name);
        } else {
            return ChatUtils.error("There is no active teleport request from %s.", name);
        }
    }

    public Component denyTeleportRequest(User target) {
        Optional<TeleportRequest> optionalRequest = teleportRequests.stream().filter(request -> request.getTarget().equals(target)).findFirst();
        if (optionalRequest.isPresent()) {
            TeleportRequest teleportRequest = optionalRequest.get();
            teleportRequest.denyRequest();
            chatHandler.handle(new DirectMessage(ChatChannels.GLOBAL.getChannelName(), this.uuid, SERVER_SENDER, ChatUtils.error("%s has denied your teleport request.", target.getName()), false));
            return ChatUtils.success("Denied teleport request from %s.", name);
        } else {
            return ChatUtils.error("There is no active teleport request from %s.", name);
        }
    }

    public void removeTeleportRequest(UUID id, User target, boolean notifyRequester) {
        teleportRequests.removeIf(request -> request.getId().equals(id));
        if (notifyRequester && isOnline()) {
            chatHandler.handle(new DirectMessage(ChatChannels.GLOBAL.getChannelName(), this.uuid, SERVER_SENDER, ChatUtils.error("Your teleport request to %s has timed out.", target.getName()), false));
        }
    }

    public void cancelTeleportRequestTo(User user) {
        teleportRequests.stream().filter(request -> request.getTarget().equals(user)).findFirst().ifPresent(request -> {
            request.cancel();
            teleportRequests.remove(request);
        });
    }

    public void cancelTeleportRequests() {
        teleportRequests.forEach(TeleportRequest::cancel);
        teleportRequests.clear();
    }
    public void addAutoModFlag(AutoModFlag flag) {
        autoModFlags.add(flag);
    }

    public void removeExpiredAutoModFlags() {
        autoModFlags.removeIf(AutoModFlag::isExpired);
    }

    public int getAutoModFlagPoints() {
        return autoModFlags.stream().mapToInt(AutoModFlag::getPoints).sum();
    }

    private String getChatChannel() {
        return globalSQL.getString("SELECT chat_channel FROM player_data WHERE uuid='" + uuid + "';");
    }

    public void setTipsEnabled(boolean enabled) {
        globalSQL.update("UPDATE player_data SET tips_enabled=" + enabled + " WHERE uuid='" + uuid + "';");
    }

    private boolean isTipsEnabled() {
        return globalSQL.getBoolean("SELECT tips_enabled FROM player_data WHERE uuid='" + uuid + "';");
    }

    private List<Component> getOfflineMessages() {
        List<Component> components = new ArrayList<>();
        List<String> messages = globalSQL.getOfflineMessages(uuid);
        messages.forEach(message -> components.add(GsonComponentSerializer.gson().deserialize(message)));
        // Delete the messages.
        globalSQL.update("DELETE FROM messages WHERE recipient='" + uuid + "'");
        return components;
    }

    private void updateNameAsync(String uuid) {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {

            executor.submit(() -> {
                String stringUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.replace("-", "");
                try {
                    URL url = new URI(stringUrl).toURL();
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.connect();

                    //Getting the response code
                    int responsecode = connection.getResponseCode();

                    if (responsecode != 200) {
                        log.severe("Unable to fetch username for " + uuid + ", please update the name manually.");
                        log.warning("Setting the default name 'x' for user " + uuid + ".");
                        globalSQL.update("UPDATE player_data SET name='x' WHERE uuid='" + uuid + "';");
                    } else {
                        JsonNode jsonNode = getJsonNodeFromUrl(url);
                        JsonNode nameNode = jsonNode.get("name");
                        String name = nameNode.asText();

                        globalSQL.update("UPDATE player_data SET name='" + name + "' WHERE uuid='" + uuid + "';");
                    }

                } catch (IOException | URISyntaxException e) {
                    log.warning("Error occurred while fetching username for " + uuid + ": " + e.getMessage());
                }
            });
        }
    }

    private void loadAutoModFlags(List<AutoModRule> rules) {
        List<AutoModFlagDTO> dtos = globalSQL.getAutoModFlags(uuid);
        for (AutoModFlagDTO dto : dtos) {
            AutoModRule rule = rules.stream().filter(r -> Objects.equals(r.getId(), dto.ruleId())).findFirst().orElse(null);
            if (rule instanceof AutoModFlagRule flagRule) {
                AutoModFlag flag = new AutoModFlag(flagRule, dto.timestamp(), dto.message(), new AutoModMatch(dto.messageWord(), dto.flaggedWord()));
                if (!flag.isExpired()) {
                    autoModFlags.add(flag);
                }
            } else {
                log.warning("AutoModFlagRule not found for id: " + dto.ruleId());
            }
        }
        // After loading, we can clear the database entries for this user as they are now in-memory.
        globalSQL.update("DELETE FROM automod_flags WHERE uuid='" + uuid + "';");
    }

    public void saveAutoModFlags() {
        removeExpiredAutoModFlags();
        if (autoModFlags.isEmpty()) {
            globalSQL.update("DELETE FROM automod_flags WHERE uuid='" + uuid + "';");
            return;
        }

        List<AutoModFlagDTO> flags = new ArrayList<>();
        for (AutoModFlag flag : autoModFlags) {
            flags.add(new AutoModFlagDTO(
                    flag.getRule().getId(),
                    flag.getTimestamp(),
                    flag.getMessage(),
                    flag.getMatch().messageWord(),
                    flag.getMatch().flaggedWord()
            ));
        }
        globalSQL.saveAutoModFlags(uuid, flags);
    }

    private static JsonNode getJsonNodeFromUrl(URL url) throws IOException {
        StringBuilder inline = new StringBuilder();
        Scanner scanner = new Scanner(url.openStream());

        //Write all the JSON data into a string using a scanner
        while (scanner.hasNext()) {
            inline.append(scanner.nextLine());
        }

        //Close the scanner
        scanner.close();

        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(inline.toString());
    }

    /**
     * Recursively strips all text decorations (bold, italic, etc.) from the component tree,
     * preserving colors and inheritance.
     */
    private static Component stripDecorations(Component component) {
        Style cleanStyle = component.style().toBuilder()
            .decoration(TextDecoration.BOLD, TextDecoration.State.NOT_SET)
            .decoration(TextDecoration.ITALIC, TextDecoration.State.NOT_SET)
            .decoration(TextDecoration.UNDERLINED, TextDecoration.State.NOT_SET)
            .decoration(TextDecoration.STRIKETHROUGH, TextDecoration.State.NOT_SET)
            .decoration(TextDecoration.OBFUSCATED, TextDecoration.State.NOT_SET)
            .build();

        Component cleaned = component.style(cleanStyle);

        // Recurse on children, letting inheritance apply parent's clean style.
        if (!component.children().isEmpty()) {
            List<Component> cleanChildren = component.children().stream()
                .map(User::stripDecorations)
                .toList();
            cleaned = cleaned.children(cleanChildren);
        }

        return cleaned;
    }
}
