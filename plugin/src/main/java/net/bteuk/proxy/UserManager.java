package net.bteuk.proxy;

import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import lombok.extern.java.Log;
import net.bteuk.network.lib.dto.ChatMessage;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.FocusEvent;
import net.bteuk.network.lib.dto.MuteEvent;
import net.bteuk.network.lib.dto.OnlineUser;
import net.bteuk.network.lib.dto.OnlineUserAdd;
import net.bteuk.network.lib.dto.OnlineUserRemove;
import net.bteuk.network.lib.dto.OnlineUsersReply;
import net.bteuk.network.lib.dto.PlotMessage;
import net.bteuk.network.lib.dto.SwitchServerEvent;
import net.bteuk.network.lib.dto.UserConnectReply;
import net.bteuk.network.lib.dto.UserConnectRequest;
import net.bteuk.network.lib.dto.UserDisconnect;
import net.bteuk.network.lib.dto.UserRemove;
import net.bteuk.network.lib.dto.UserUpdate;
import net.bteuk.network.lib.enums.ChatChannels;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.proxy.eventing.listeners.ServerConnectListener;
import net.bteuk.proxy.exceptions.ErrorMessage;
import net.bteuk.proxy.exceptions.ServerNotFoundException;
import net.bteuk.proxy.utils.SwitchServer;
import net.bteuk.proxy.utils.Time;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.awt.Color;
import java.time.Duration;
import java.util.*;

import static net.bteuk.network.lib.enums.ChatChannels.GLOBAL;
import static net.bteuk.proxy.utils.Analytics.logPlayerCount;
import static net.bteuk.proxy.utils.Constants.JOIN_MESSAGE;
import static net.bteuk.proxy.utils.Constants.LEAVE_MESSAGE;
import static net.bteuk.proxy.utils.Constants.RECONNECT_MESSAGE;
import static net.bteuk.proxy.utils.Constants.SERVER_SENDER;
import static net.bteuk.proxy.utils.Constants.WELCOME_MESSAGE;

/**
 * Class to manage the users on the network.
 */
@Getter
@Log
public class UserManager {

    private final ProxyServer server;

    @Getter
    private final List<User> users = new ArrayList<>();

    private final Set<OnlineUser> onlineUsers = new HashSet<>();

    public UserManager(ProxyServer server) {
        this.server = server;
        initOnlineTracker();
    }

    public void handleUserConnect(UserConnectRequest request) {

        User user = addUser(request);
        Proxy.getInstance().getLogger().info(String.format("UserConnectRequest for %s received.", request.getName()));

        // Get the information for the reply.
        UserConnectReply reply = user.createUserConnectReply();

        // Send the reply to the server.
        try {
            Proxy.getInstance().getChatHandler().handle(reply, request.getServer());
            OnlineUser onlineUser = new OnlineUser(user.getUuid(), user.getName(), user.getServer());
            onlineUsers.add(onlineUser);
            Proxy.getInstance().getChatHandler().handle(new OnlineUserAdd(onlineUser));
        } catch (ServerNotFoundException e) {
            // TODO: Handle exception
        }
    }

    public void handleUserDisconnect(UserDisconnect disconnect) {

        // Get the user.
        User user = getUserByUuid(disconnect.getUuid());

        if (user == null) {
            Proxy.getInstance().getLogger().warn(String.format("Disconnect event for %s was started, but no User exists by that uuid.", disconnect.getUuid()));
            return;
        }

        if (user.isBlockNextDisconnect()) {
            Proxy.getInstance().getLogger().warn("User has already reconnected, cancelling disconnect.");
            user.setBlockNextDisconnect(false);
            return;
        }

        if (user.getSwitchServer() == null && user.getServer().equals(disconnect.getServer())) {
            // Disconnect.
            disconnectUser(user);

            // Save information about the user.
            if (disconnect.getNavigatorEnabled() != null) {
                user.setNavigatorEnabled(disconnect.getNavigatorEnabled());
            }
            if (disconnect.getNightvisionEnabled() != null) {
                user.setNightvisionEnabled(disconnect.getNightvisionEnabled());
            }
            if (disconnect.getTipsEnabled() != null) {
                user.setTipsEnabled(disconnect.getTipsEnabled());
            }
            if (disconnect.getChatChannel() != null) {
                user.setChatChannel(disconnect.getChatChannel());
            }
            if (disconnect.getTeleportEnabled() != null) {
                user.setTeleportEnabled(disconnect.getTeleportEnabled());
            }

            Optional<OnlineUser> optionalOnlineUser = onlineUsers.stream().filter(onlineUser -> onlineUser.getUuid().equals(user.getUuid())).findFirst();
            optionalOnlineUser.ifPresent(onlineUsers::remove);
            Proxy.getInstance().getChatHandler().handle(new OnlineUserRemove(user.getUuid()));

        } else {
            Proxy.getInstance().getLogger().info(String.format("Disconnect event for %s cancelled due to switching server.", disconnect.getUuid()));
        }
    }

    public void handleUserUpdate(UserUpdate userUpdate) {

        // Get the user.
        User user = getUserByUuid(userUpdate.getUuid());

        if (user != null) {
            updateUser(user, userUpdate);
        } else {
            Proxy.getInstance().getLogger().warn("Update event for {} was received, but no User exists by that uuid.", userUpdate.getUuid());
        }
    }

    /**
     * Handler for switch server events.
     * On receiving, switch the server of a user.
     * If the user does not switch within 10 seconds, cancel.
     * The {@link ServerConnectListener} will be able to check if the switch has actually happened.
     *
     * @param switchServerEvent the event
     */
    public void handleSwitchServerEvent(SwitchServerEvent switchServerEvent) {

        User user = getUserByUuid(switchServerEvent.getUuid());

        if (user != null) {
            SwitchServer switchServer = user.getSwitchServer();
            if (switchServer != null) {
                // Cancel the existing switch server event.
                switchServer.cancelTimeout();
            }
            // Connect the user to the server.
            Proxy.getInstance().getServer().getServer(switchServerEvent.getTo_server()).ifPresentOrElse(server -> {
                user.setSwitchServer(new SwitchServer(user, switchServerEvent.getFrom_server(), switchServerEvent.getTo_server()));
                // save disconnect info.
                saveUserInfoFromDisconnect(user, switchServerEvent.getUserDisconnect());
                user.getPlayer().createConnectionRequest(server).fireAndForget();
                Proxy.getInstance().getLogger().info(String.format("Connecting player to %s.", server.getServerInfo().getName()));
            }, () -> {
                // Send message that the server is not online.
                DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), user.getUuid(), "server",
                        ChatUtils.error("The server %s is not available, please contact an admin!", switchServerEvent.getTo_server()),
                        false);

                Proxy.getInstance().getChatHandler().handle(directMessage);

            });

        } else {
            Proxy.getInstance().getLogger().warn(String.format("Switch server event was received for non-existing user %s", switchServerEvent.getUuid()));
        }
    }

    public void handleMuteEvent(MuteEvent muteEvent) {

        Component returnMessage;

        try {
            User user = getUserByUuid(muteEvent.getUuid());
            User userToMute = getUserByUuid(muteEvent.getUuidToMute());

            if (user == null) {
                Proxy.getInstance().getLogger().warn(String.format("Mute event was received from non-existing user %s", muteEvent.getUuid()));
                throw new ErrorMessage(ChatUtils.error("An error has occurred, please rejoin the server."));
            } else if (userToMute == null) {
                Proxy.getInstance().getLogger().warn(String.format("Mute event was received for non-existing user %s", muteEvent.getUuidToMute()));
                throw new ErrorMessage(ChatUtils.error("The selected player is no longer online."));
            }

            // Check if the player is already muted or unmuted.
            // Prevent from muting yourself.
            if (muteEvent.isMute() && user == userToMute) {
                throw new ErrorMessage(ChatUtils.error("You can't mute yourself, just stop sending messages."));
            } else if (!muteEvent.isMute() && !user.isMuted(userToMute)) {
                throw new ErrorMessage(ChatUtils.error("%s is not muted.", userToMute.getName()));
            }

            if (muteEvent.isMute() && !user.isMuted(userToMute)) {
                user.mute(userToMute);
                returnMessage = ChatUtils.success("Muted %s for this session.", userToMute.getName());
            } else {
                user.unmute(userToMute);
                returnMessage = ChatUtils.success("Unmuted %s", userToMute.getName());
            }

            // Update tab list to reflect mute.
            Proxy.getInstance().getTabManager().updatePlayerInTablistOfPlayer(user, userToMute);

        } catch (ErrorMessage errorMessage) {
            // Set the error message as the return message.
            returnMessage = errorMessage.getError();
        }

        DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), muteEvent.getUuid(), muteEvent.getUuid(), returnMessage, false);
        Proxy.getInstance().getChatManager().sendDirectMessage(directMessage);
    }

    public void handleOnlineUsersRequest() {
        Proxy.getInstance().getChatHandler().handle(new OnlineUsersReply(onlineUsers));
        Proxy.getInstance().getTabManager().sendAddTeam();
    }

    public void handleFocusEvent(FocusEvent focusEvent) {
        User user = getUserByUuid(focusEvent.getUuid());
        if (user != null) {
            user.setFocusEnabled(focusEvent.isEnable());
        }
    }

    public User addUser(UserConnectRequest request) {

        String joinMessage = null;

        // See is user instance still exists.
        User user = getUserByUuid(request.getUuid());
        if (user != null) {

            SwitchServer switchServer = user.getSwitchServer();
            if (switchServer != null) {

                // Check if the user is switching the server they are actually connecting to.
                // Else cancel their join eventing, since they weren't meant for this server.
                // Cancel the switch server task either way.
                if (!switchServer.getToServer().equals(request.getServer())) {
                    user.clearJoinEvent();
                }

                switchServer.cancelTimeout();
                user.setSwitchServer(null);

            } else {
                // If the user is still online, quickly cancel the disconnect event.
                if (user.isOnline()) {
                    user.setBlockNextDisconnect(true);
                }
                // Cancel disconnect task.
                user.reconnect();

                // Send reconnect message to servers and discord.
                joinMessage = RECONNECT_MESSAGE;
            }

        } else {

            // Add user.
            user = new User(request);
            users.add(user);

            if (!Proxy.getInstance().getGlobalSQL().hasRow("SELECT uuid FROM player_data WHERE uuid='" + request.getUuid() + "';")) {
                // Send welcome message.
                joinMessage = WELCOME_MESSAGE;

                // Set the user as a new user.
                user.setNewUser(true);
            } else {
                // Send connect message.
                joinMessage = JOIN_MESSAGE;
            }
        }

        // Make sure the username is correct.
        // It is possible that the name is used by another user.
        user.setName(request.getName());

        // Set the server.
        user.setServer(request.getServer());

        // Set the proxy player.
        user.setPlayer(server.getAllPlayers().stream().filter(player -> player.getUniqueId().toString().equals(request.getUuid())).findFirst().orElse(null));

        // Set primary role.
        user.setPrimaryRole(request.getTabPlayer().getPrimaryGroup());

        // Send join message, if not null.
        if (joinMessage != null) {
            sendConnectMessage(joinMessage, user, Color.GREEN);

            // Add the user to tab for other players.
            Proxy.getInstance().getTabManager().addPlayer(request.getTabPlayer());

            // If the user is a reviewer send messages for the number of submitted plots, region request and navigation requests.
            sendReviewerMessages(request);
        }

        // Log the player count.
        logPlayerCount(getUsers());

        // Send the tab list to the user.
        Proxy.getInstance().getTabManager().sendTablist(user);

        return user;
    }

    /**
     * A user has disconnected, start their removal timer.
     *
     * @param user the {@link User}
     */
    public void disconnectUser(User user) {

        user.disconnect(() -> removeUser(user, false));

        // Log the player count.
        logPlayerCount(getUsers());

        // Remove the player from tab.
        Proxy.getInstance().getTabManager().removePlayer(user.getUuid());

        sendConnectMessage(LEAVE_MESSAGE, user, Color.RED);
        Proxy.getInstance().getLogger().info(String.format("User %s has disconnected.", user.getName()));
    }

    /**
     * Check if a user has another user muted.
     *
     * @param userUuid      uuid of user
     * @param otherUserUuid uuid of user to check
     * @return boolean if user has otherUser muted
     */
    public boolean isMutedForUser(String userUuid, String otherUserUuid) {
        User user = getUserByUuid(userUuid);
        if (user != null) {
            User otherUser = getUserByUuid(otherUserUuid);
            if (otherUser != null) {
                return user.isMuted(otherUser);
            }
        }
        return false;
    }

    public void updateUser(User user, UserUpdate update) {

        // Check what needs updating.
        if (update.getChannels() != null && !user.getChannels().equals(update.getChannels())) {
            user.getChannels().clear();
            user.getChannels().addAll(update.getChannels());
        }

        if (update.getAfk() != null && user.isAfk() != update.getAfk()) {
            user.setAfk(update.getAfk());
            Proxy.getInstance().getTabManager().updatePlayerByUuid(update.getUuid());
        }

        if (update.getTabPlayer() != null && !update.getTabPlayer().getPrimaryGroup().equals(user.getPrimaryRole())) {
            user.setPrimaryRole(update.getTabPlayer().getPrimaryGroup());
            // Send the user update back to the servers, so they can potentially update the primary role.
            Proxy.getInstance().getChatHandler().handle(update);
            Proxy.getInstance().getTabManager().updatePlayer(update.getTabPlayer());
        }

        if (!Objects.equals(user.getDisplayName(), update.getDisplayName())) {
            user.updateDisplayName(update.getDisplayName());
        }
    }

    /**
     * Get a user by uuid.
     *
     * @param uuid the uuid of the user to get
     * @return the {@link User} or null if not exists
     */
    public User getUserByUuid(String uuid) {
        return users.stream().filter(user -> user.getUuid().equals(uuid)).findFirst().orElse(null);
    }

    /**
     * Get a user by name.
     *
     * @param name the uuid of the user to get
     * @return the {@link User} or null if not exists
     */
    public User getUserByName(String name) {
        return users.stream().filter(user -> user.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    /**
     * Removes all users from the user list.
     * The removal is run as if they disconnected.
     * This is to be used on Proxy-shutdown.
     */
    public void removeAllUsers() {
        while (!getUsers().isEmpty()) {
            removeUser(getUsers().getFirst(), true);
        }
        // Log the player count (which should always be 0 at this point).
        logPlayerCount(getUsers());
    }

    public void sendPlotMessageToAll(PlotMessage plotMessage) {
        Proxy.getInstance().getServerManager().getServers().forEach(server ->
                server.getRegisteredServer().getPlayersConnected().forEach(player -> {
                            // Get the user.
                            User user = getUserByUuid(player.getUniqueId().toString());
                            if (user == null) {
                                return;
                            }
                            if (plotMessage.isVerify()) {
                                sendPlotVerifyMessage(user, player.hasPermission("group.reviewer"), player.getUniqueId().toString(), plotMessage.getMessageTemplate(), true);
                            } else {
                                sendPlotReviewMessage(user, player.hasPermission("group.architect"), player.hasPermission("group.reviewer"), player.getUniqueId().toString(), plotMessage.getMessageTemplate(), true);
                            }
                        }
                )
        );
    }

    private void initOnlineTracker() {
        Proxy.getInstance().getServer().getScheduler().buildTask(Proxy.getInstance(), () -> {
                    Proxy.getInstance().getServer().getAllPlayers().forEach(player -> {

                        // Update the last ping of the user.
                        User user = getUserByUuid(player.getUniqueId().toString());
                        if (user == null) {
                            log.warning(String.format("Player %s is on the server but is not tracked, maybe they just logged in?", player.getUsername()));
                        } else {
                            user.setLastPing(Time.currentTime());
                        }
                    });

                    // If any user has not been pinged for 5 minutes, remove them.
                    // Only do this for users that the server thinks are still online, offline users will be removed automatically after 5 minutes.
                    users.forEach(user -> {
                        long time = Time.currentTime();
                        if (time - user.getLastPing() > 5 * 60 * 1000L && user.isOnline()) {
                            log.warning("Player " + user.getName() + " has not been pinged for 5 minutes, removing them from the proxy.");
                            removeUser(user, false);
                        }
                    });
                })
                .repeat(Duration.ofMinutes(1L)).schedule();
    }

    /**
     * Remove a user from the proxy.
     *
     * @param user the user to remove
     */
    private void removeUser(User user, boolean shutdown) {
        // If the user is online, disconnect them first.
        if (user.isOnline()) {
            user.disconnect(() -> {
            });
        }

        // Remove all plot, zone and region invites sent and received.
        removeInvites(user.getUuid());

        // Remove the user from the list.
        users.remove(user);
        user.delete();
        // Remove the user from the list of muted users for all players, if they had this player muted.
        users.forEach(u -> u.unmute(user));
        UserRemove userRemoveEvent = new UserRemove(user.getUuid());

        Proxy.getInstance().getChatHandler().handle(userRemoveEvent);
        if (!shutdown) {
            Proxy.getInstance().getLogger().info(String.format("Removed user %s from the proxy, they have been offline for more than 5 minutes", user.getName()));
        } else {
            Optional<OnlineUser> optionalOnlineUser = onlineUsers.stream().filter(onlineUser -> onlineUser.getUuid().equals(user.getUuid())).findFirst();
            optionalOnlineUser.ifPresent(onlineUsers::remove);
            Proxy.getInstance().getChatHandler().handle(new OnlineUserRemove(user.getUuid()));
            Proxy.getInstance().getLogger().info(String.format("Removed user %s from the proxy due to shutdown", user.getName()));
        }
    }

    private void sendReviewerMessages(UserConnectRequest request) {
        //Show the number of submitted plots.
        String uuid = request.getUuid();
        // Get the user.
        User user = getUserByUuid(uuid);
        if (user == null) {
            return;
        }
        sendPlotReviewMessage(user, request.isArchitect(), request.isReviewer(), uuid, "There %s %s %s to review.", false);
        sendPlotVerifyMessage(user, request.isReviewer(), uuid, "There %s %s %s to verify.", false);

        //Show the number of submitted regions requests.
        if (request.isReviewer()) {
            int regions = Proxy.getInstance().getRegionSQL().getInt("SELECT COUNT(region) FROM region_requests WHERE staff_accept=0;");
            if (regions != 0) {
                Component regionMessage = ChatUtils.success("There " + (regions == 1 ? "is" : "are") + " %s region " + (regions == 1 ? "request" : "requests") + " to review.", String.valueOf(regions));
                DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), uuid, "server", regionMessage, false);
                Proxy.getInstance().getChatHandler().handle(directMessage);
            }

            //Show the number of submitted navigation requests;
            int navigation = Proxy.getInstance().getGlobalSQL().getInt("SELECT COUNT(location) FROM location_requests;");
            if (navigation != 0) {
                Component navigationMessage = ChatUtils.success("There " + (navigation == 1 ? "is" : "are") + " %s navigation " + (navigation == 1 ? "request" : "requests") + " to review.", String.valueOf(navigation));
                DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), uuid, "server", navigationMessage, false);
                Proxy.getInstance().getChatHandler().handle(directMessage);
            }
        }
    }

    private void sendPlotReviewMessage(User user, boolean isArchitect, boolean isReviewer, String uuid, String
            messageTemplate, boolean includeZero) {
        if (isArchitect || isReviewer) {
            int plots = Proxy.getInstance().getPlotSQL().getReviewablePlotCount(uuid, isArchitect, isReviewer);
            if (user.getPreviousPlotSubmissionCount() != plots && (plots != 0 || includeZero)) {
                user.setPreviousPlotSubmissionCount(plots);
                Component plotMessage = ChatUtils.success(messageTemplate, ChatUtils.success(plots == 1 ? "is" : "are"), Component.text(String.valueOf(plots), NamedTextColor.DARK_AQUA), ChatUtils.success(plots == 1 ? "plot" : "plots"));
                DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), uuid, "server", plotMessage, false);
                Proxy.getInstance().getChatHandler().handle(directMessage);
            }
        }
    }

    private void sendPlotVerifyMessage(User user, boolean isReviewer, String uuid, String messageTemplate, boolean includeZero) {
        if (isReviewer) {
            int plots = Proxy.getInstance().getPlotSQL().getVerifiablePlotCount(uuid, true);
            if (user.getPreviousPlotVerificationCount() != plots && (plots != 0 || includeZero)) {
                user.setPreviousPlotVerificationCount(plots);
                Component plotMessage = ChatUtils.success(messageTemplate, ChatUtils.success(plots == 1 ? "is" : "are"), Component.text(String.valueOf(plots), NamedTextColor.DARK_AQUA), ChatUtils.success(plots == 1 ? "plot" : "plots"));
                DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), uuid, "server", plotMessage, false);
                Proxy.getInstance().getChatHandler().handle(directMessage);
            }
        }
    }

    private void removeInvites(String uuid) {
        Proxy.getInstance().getPlotSQL().update("DELETE FROM plot_invites WHERE owner='" + uuid + "';");
        Proxy.getInstance().getPlotSQL().update("DELETE FROM zone_invites WHERE owner='" + uuid + "';");
        Proxy.getInstance().getRegionSQL().update("DELETE FROM region_invites WHERE owner='" + uuid + "';");

        Proxy.getInstance().getPlotSQL().update("DELETE FROM plot_invites WHERE uuid='" + uuid + "';");
        Proxy.getInstance().getPlotSQL().update("DELETE FROM zone_invites WHERE uuid='" + uuid + "';");
        Proxy.getInstance().getRegionSQL().update("DELETE FROM region_invites WHERE uuid='" + uuid + "';");
    }

    private void sendConnectMessage(String message, User user, Color colour) {
        Proxy.getInstance().getDiscord().sendConnectEmbed(message, user.getName(), user.getUuid(), user.getPlayerSkin(), colour, null);
        sendConnectMessageToServer(message, user.getName());
    }

    private void sendConnectMessageToServer(String message, String name) {
        // Construct a chat message to send to the servers.
        Component component = Component.text(message.replace("%player%", name), NamedTextColor.YELLOW);
        ChatMessage chatMessage = new ChatMessage(GLOBAL.getChannelName(), SERVER_SENDER, component);
        Proxy.getInstance().getChatManager().handle(chatMessage);
    }

    private void saveUserInfoFromDisconnect(User user, UserDisconnect disconnect) {
        // Save information about the user.
        user.setNavigatorEnabled(disconnect.getNavigatorEnabled());
        user.setNightvisionEnabled(disconnect.getNightvisionEnabled());
        user.setTipsEnabled(disconnect.getTipsEnabled());
        user.setChatChannel(disconnect.getChatChannel());
        user.setTeleportEnabled(disconnect.getTeleportEnabled());
    }
}
