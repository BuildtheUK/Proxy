package net.bteuk.proxy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.TaskStatus;
import lombok.Getter;
import lombok.Setter;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.UserConnectReply;
import net.bteuk.network.lib.dto.UserConnectRequest;
import net.bteuk.network.lib.enums.ChatChannels;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.proxy.database.sql.GlobalSQL;
import net.bteuk.proxy.utils.Analytics;
import net.bteuk.proxy.utils.SwitchServer;
import net.bteuk.proxy.utils.Time;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * User object, stored specific information about the user.
 */
public class User {

    @Getter
    private boolean online = true;

    /** Indicator for new users, so the database object is created when fetching information. */
    @Setter
    private boolean newUser = false;

    @Getter
    private final String uuid;

    /** The Proxy {@link Player}, this may be null if the user is not currently online. */
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

    /** List of muted users for this session */
    private final Set<User> mutedUsers = new HashSet<>();

    /** List of channels the user can read */
    @Getter
    private final Set<String> channels = new HashSet<>();

    @Getter
    private boolean afk = false;

    private ScheduledTask disconnectTask;

    /** Utility reference to the database. */
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

    public User(UserConnectRequest request) {
        this.uuid = request.getUuid();
        this.name = request.getName();
        this.playerSkin = request.getPlayerSkin();
        this.channels.addAll(request.getChannels());

        this.globalSQL = Proxy.getInstance().getGlobalSQL();

        this.lastPing = Time.currentTime();

        setDisplayName();
    }

    public void setDisplayName() {
        String displayName = globalSQL.getString("SELECT display_name FROM player_data WHERE uuid='" + uuid + "';");
        if (displayName != null) {
            this.displayName = GsonComponentSerializer.gson().deserialize(displayName);
        } else {
            this.displayName = Component.text(this.name);
        }
    }

    public void updateDisplayName(Component newDisplayName) {
        // Assert whether the display name is valid.
        if (PlainTextComponentSerializer.plainText().serialize(newDisplayName).length() > 16) {
            Proxy.getInstance().getChatHandler().handle(new DirectMessage(ChatChannels.GLOBAL.getChannelName(), this.uuid, "server", ChatUtils.error("Your nickname must not exceed 16 characters."), false));
            return;
        }
        // Strip any formatting.
        stripDecorations(newDisplayName);

        String displayName = GsonComponentSerializer.gson().serialize(newDisplayName);
        globalSQL.update("UPDATE player_data SET display_name='" + displayName + "' WHERE uuid='" + uuid + "';");
        this.displayName = newDisplayName;

        // Update TAB.
        Proxy.getInstance().getTabManager().updatePlayerByUuid(uuid);
        Proxy.getInstance().getChatHandler().handle(new DirectMessage(ChatChannels.GLOBAL.getChannelName(), this.uuid, "server", ChatUtils.success("Updated nickname to ").append(newDisplayName), false));
    }

    /**
     * The user has disconnected from the network.
     * Store their user for 5 minutes before removing it.
     * This allows their local settings to remain stored in case they reconnect.
     */
    public void disconnect(Runnable runnable) {
        long time = Time.currentTime();

        //Set last_online time in playerdata.
        Proxy.getInstance().getGlobalSQL().update("UPDATE player_data SET last_online=" + time + " WHERE UUID='" + uuid + "';");

        Analytics.save(this, Time.getDate(time), time);
        online = false;
        // Run a delayed task to remove the user.
        disconnectTask = Proxy.getInstance().getServer().getScheduler().buildTask(Proxy.getInstance(), runnable)
                .delay(5L, TimeUnit.MINUTES)
                .schedule();
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
        if (disconnectTask != null && disconnectTask.status() == TaskStatus.SCHEDULED) {
            disconnectTask.cancel();
        }
        disconnectTask = null;
    }

    /**
     * Delete the user instance.
     */
    public void delete() {
        // If the disconnectTask is running cancel.
        if (disconnectTask != null && disconnectTask.status() == TaskStatus.SCHEDULED) {
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
        if (newUser && globalSQL.createUser(uuid, name, playerSkin)) {
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
            Analytics.save(this, Time.getDate(time), time);
        } else {
            //Reset last logged time.
            last_time_log = Time.currentTime();
        }
        this.afk = afk;
    }

    public void setFocusEnabled(boolean focusEnabled) {
        this.focusEnabled = focusEnabled;
        // Update Tab for this player.
        Proxy.getInstance().getTabManager().updatePlayerByUuid(uuid);
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
        Proxy.getInstance().getServer().getScheduler().buildTask(Proxy.getInstance(), () -> {
            String stringUrl = "https://sessionserver.mojang.com/session/minecraft/profile/"+uuid.replace("-", "");
            try {
                URL url = new URI(stringUrl).toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                //Getting the response code
                int responsecode = connection.getResponseCode();

                if (responsecode != 200) {
                    Proxy.getInstance().getLogger().error("Unable to fetch username for {}, please update the name manually.", uuid);
                    Proxy.getInstance().getLogger().warn("Setting the default name 'x' for user {}.", uuid);
                    globalSQL.update("UPDATE player_data SET name='x' WHERE uuid='" + uuid + "';");
                } else {
                    JsonNode jsonNode = getJsonNodeFromUrl(url);
                    JsonNode nameNode = jsonNode.get("name");
                    String name = nameNode.asText();

                    globalSQL.update("UPDATE player_data SET name='" + name + "' WHERE uuid='" + uuid + "';");
                }

            } catch (IOException | URISyntaxException e) {
                Proxy.getInstance().getLogger().warn("Error occurred while fetching username for {}: {}", uuid, e.getMessage());
            }
        }).schedule();
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
