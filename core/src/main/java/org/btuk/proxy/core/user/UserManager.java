package org.btuk.proxy.core.user;

import lombok.Getter;
import lombok.extern.java.Log;
import net.bteuk.network.lib.dto.ChatMessage;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.FocusEvent;
import net.bteuk.network.lib.dto.MuteEvent;
import net.bteuk.network.lib.dto.OnlineUser;
import net.bteuk.network.lib.dto.OnlineUserAdd;
import net.bteuk.network.lib.dto.OnlineUserRemove;
import net.bteuk.network.lib.dto.PlotMessage;
import net.bteuk.network.lib.dto.SwitchServerEvent;
import net.bteuk.network.lib.dto.UserConnectReply;
import net.bteuk.network.lib.dto.UserConnectRequest;
import net.bteuk.network.lib.dto.UserDisconnect;
import net.bteuk.network.lib.dto.UserRemove;
import net.bteuk.network.lib.dto.UserUpdate;
import net.bteuk.network.lib.enums.ChatChannels;
import net.bteuk.network.lib.utils.ChatUtils;
import org.btuk.proxy.database.sql.GlobalSQL;
import org.btuk.proxy.database.sql.PlotSQL;
import org.btuk.proxy.database.sql.RegionSQL;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.btuk.proxy.core.discord.Discord;
import org.btuk.proxy.core.tab.TabManager;
import org.btuk.proxy.core.chat.ChatHandler;
import org.btuk.proxy.core.chat.ChatManager;
import org.btuk.proxy.core.exceptions.ErrorMessage;
import org.btuk.proxy.core.exceptions.ServerNotFoundException;
import org.btuk.proxy.core.player.PlayerManager;
import org.btuk.proxy.core.scheduler.Scheduler;
import org.btuk.proxy.core.server.CoreServerManager;
import org.btuk.proxy.core.utils.Analytics;
import org.btuk.proxy.core.utils.SwitchServer;
import org.btuk.proxy.core.utils.Time;

import static net.bteuk.network.lib.enums.ChatChannels.GLOBAL;
import static org.btuk.proxy.core.utils.Constants.*;

/**
 * Class to manage the users on the network.
 */
@Getter
@Log
public class UserManager {

    private final CoreUserManager coreUserManager;

    private final ChatHandler chatHandler;

    private final TabManager tabManager;

    private final GlobalSQL globalSQL;

    private final PlotSQL plotSQL;

    private final RegionSQL regionSQL;

    private final CoreServerManager coreServerManager;

    private final Scheduler scheduler;

    private final ChatManager chatManager;

    private final PlayerManager playerManager;

    private final Analytics analytics;

    private final Discord discord;

    public UserManager(CoreUserManager coreUserManager, ChatHandler chatHandler, TabManager tabManager, GlobalSQL globalSQL, PlotSQL plotSQL, RegionSQL regionSQL, CoreServerManager coreServerManager, Scheduler scheduler, ChatManager chatManager, PlayerManager playerManager, Analytics analytics, Discord discord) {
        this.coreUserManager = coreUserManager;
        this.chatHandler = chatHandler;
        this.tabManager = tabManager;
        this.globalSQL = globalSQL;
        this.plotSQL = plotSQL;
        this.regionSQL = regionSQL;
        this.coreServerManager = coreServerManager;
        this.scheduler = scheduler;
        this.chatManager = chatManager;
        this.playerManager = playerManager;
        this.analytics = analytics;
        this.discord = discord;
        initOnlineTracker();
    }

    public void handleUserConnect(UserConnectRequest request) {

        User user = addUser(request);
        log.info(String.format("UserConnectRequest for %s received.", request.getName()));

        // Get the information for the reply.
        UserConnectReply reply = user.createUserConnectReply();

        // Send the reply to the server.
        try {
            chatHandler.handle(reply, request.getServer());
            OnlineUser onlineUser = new OnlineUser(user.getUuid(), user.getName(), user.getServer());
            coreUserManager.addOnlineUser(onlineUser);
            chatHandler.handle(new OnlineUserAdd(onlineUser));
        } catch (ServerNotFoundException e) {
            // TODO: Handle exception
        }
    }

    public void handleUserDisconnect(UserDisconnect disconnect) {

        // Get the user.
        User user = coreUserManager.getUserByUuid(disconnect.getUuid());

        if (user == null) {
            log.warning(String.format("Disconnect event for %s was started, but no User exists by that uuid.", disconnect.getUuid()));
            return;
        }

        if (user.isBlockNextDisconnect()) {
            log.warning("User has already reconnected, cancelling disconnect.");
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

            Optional<OnlineUser> optionalOnlineUser = coreUserManager.getOnlineUsers().stream().filter(onlineUser -> onlineUser.getUuid().equals(user.getUuid())).findFirst();
            optionalOnlineUser.ifPresent(coreUserManager::removeOnlineUser);
            chatHandler.handle(new OnlineUserRemove(user.getUuid()));

        } else {
            log.info(String.format("Disconnect event for %s cancelled due to switching server.", disconnect.getUuid()));
        }
    }

    public void handleUserUpdate(UserUpdate userUpdate) {

        // Get the user.
        User user = coreUserManager.getUserByUuid(userUpdate.getUuid());

        if (user != null) {
            updateUser(user, userUpdate);
        } else {
            log.warning("Update event for " + userUpdate.getUuid() + " was received, but no User exists by that uuid.");
        }
    }

    /**
     * Handler for switch server events.
     * On receiving, switch the server of a user.
     * If the user does not switch within 10 seconds, cancel.
     *
     * @param switchServerEvent the event
     */
    public void handleSwitchServerEvent(SwitchServerEvent switchServerEvent) {

        User user = coreUserManager.getUserByUuid(switchServerEvent.getUuid());

        if (user != null) {
            SwitchServer switchServer = user.getSwitchServer();
            if (switchServer != null) {
                // Cancel the existing switch server event.
                switchServer.cancelTimeout();
            }
            // Connect the user to the server.
            coreServerManager.getServer(switchServerEvent.getTo_server()).ifPresentOrElse(server -> {
                user.setSwitchServer(new SwitchServer(this, coreServerManager, scheduler, user, switchServerEvent.getFrom_server(), switchServerEvent.getTo_server()));
                // save disconnect info.
                saveUserInfoFromDisconnect(user, switchServerEvent.getUserDisconnect());
                user.getPlayer().connectToServer(server);
                log.info(String.format("Connecting player to %s.", switchServerEvent.getTo_server()));
            }, () -> {
                // Send message that the server is not online.
                DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), user.getUuid(), "server",
                    ChatUtils.error("The server %s is not available, please contact an admin!", switchServerEvent.getTo_server()),
                    false);

                chatHandler.handle(directMessage);

            });

        } else {
            log.warning(String.format("Switch server event was received for non-existing user %s", switchServerEvent.getUuid()));
        }
    }

    public void handleMuteEvent(MuteEvent muteEvent) {

        Component returnMessage;

        try {
            User user = coreUserManager.getUserByUuid(muteEvent.getUuid());
            User userToMute = coreUserManager.getUserByUuid(muteEvent.getUuidToMute());

            if (user == null) {
                log.warning(String.format("Mute event was received from non-existing user %s", muteEvent.getUuid()));
                throw new ErrorMessage(ChatUtils.error("An error has occurred, please rejoin the server."));
            } else if (userToMute == null) {
                log.warning(String.format("Mute event was received for non-existing user %s", muteEvent.getUuidToMute()));
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
            tabManager.updatePlayerInTablistOfPlayer(user, userToMute);

        } catch (ErrorMessage errorMessage) {
            // Set the error message as the return message.
            returnMessage = errorMessage.getError();
        }

        DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), muteEvent.getUuid(), muteEvent.getUuid(), returnMessage, false);
        chatManager.sendDirectMessage(directMessage);
    }

    public void handleFocusEvent(FocusEvent focusEvent) {
        User user = coreUserManager.getUserByUuid(focusEvent.getUuid());
        if (user != null) {
            user.setFocusEnabled(focusEvent.isEnable());
        }
    }

    public User addUser(UserConnectRequest request) {

        String joinMessage = null;

        // See is user instance still exists.
        User user = coreUserManager.getUserByUuid(request.getUuid());
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
            user = new User(request, globalSQL, chatHandler, tabManager, analytics, scheduler);
            coreUserManager.addUser(user);

            if (!globalSQL.hasRow("SELECT uuid FROM player_data WHERE uuid='" + request.getUuid() + "';")) {
                // Send the welcome message.
                joinMessage = WELCOME_MESSAGE;

                // Set the user as a new user.
                user.setNewUser(true);
            } else {
                // Send the connect message.
                joinMessage = JOIN_MESSAGE;
            }
        }

        // Make sure the username is correct.
        // It is possible that the name is used by another user.
        user.setName(request.getName());

        // Set the server.
        user.setServer(request.getServer());

        // Set the proxy player.
        user.setPlayer(playerManager.getPlayers().stream().filter(player -> player.getUniqueId().toString().equals(request.getUuid())).findFirst().orElse(null));

        // Set the primary role.
        user.setPrimaryRole(request.getTabPlayer().getPrimaryGroup());

        // Send the join message, if not null.
        if (joinMessage != null) {
            sendConnectMessage(joinMessage, user, Color.GREEN);

            // Add the user to the tab for other players.
            tabManager.addPlayer(request.getTabPlayer());

            // If the user is a reviewer send messages for the number of submitted plots, region request and navigation requests.
            sendReviewerMessages(request);
        }

        // Log the player count.
        analytics.logPlayerCount();

        // Send the tab list to the user.
        tabManager.sendTablist(user);

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
        analytics.logPlayerCount();

        // Remove the player from tab.
        tabManager.removePlayer(user.getUuid());

        sendConnectMessage(LEAVE_MESSAGE, user, Color.RED);
        log.info(String.format("User %s has disconnected.", user.getName()));
    }

    public void updateUser(User user, UserUpdate update) {

        // Check what needs updating.
        if (update.getChannels() != null && !user.getChannels().equals(update.getChannels())) {
            user.getChannels().clear();
            user.getChannels().addAll(update.getChannels());
        }

        if (update.getAfk() != null && user.isAfk() != update.getAfk()) {
            user.setAfk(update.getAfk());
            tabManager.updatePlayerByUuid(update.getUuid());
        }

        if (update.getTabPlayer() != null && !update.getTabPlayer().getPrimaryGroup().equals(user.getPrimaryRole())) {
            user.setPrimaryRole(update.getTabPlayer().getPrimaryGroup());
            // Send the user update back to the servers, so they can potentially update the primary role.
            chatHandler.handle(update);
            tabManager.updatePlayer(update.getTabPlayer());
        }

        if (update.getDisplayName() != null) {
            Component displayName = user.updateDisplayName(update.getDisplayName());
            if (displayName != null) {
                update.setDisplayName(displayName);
                chatHandler.handle(update);
            }
        }
    }


    /**
     * Removes all users from the user list.
     * The removal is run as if they disconnected.
     * This is to be used on Proxy-shutdown.
     */
    public void removeAllUsers() {
        while (coreUserManager.countUsers() != 0) {
            removeUser(coreUserManager.getFirst(), true);
        }
        // Log the player count (which should always be 0 at this point).
        analytics.logPlayerCount();
    }

    public void sendPlotMessageToAll(PlotMessage plotMessage) {
        coreServerManager.getServers().forEach(server ->
            server.getPlayers().forEach(player -> {
                    // Get the user.
                    User user = coreUserManager.getUserByUuid(player.getUniqueId().toString());
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
        scheduler.createRepeatingTask(() -> {
            playerManager.getPlayers().forEach(player -> {

                // Update the last ping of the user.
                User user = coreUserManager.getUserByUuid(player.getUniqueId().toString());
                if (user == null) {
                    log.warning(String.format("Player %s is on the server but is not tracked, maybe they just logged in?", player.getUsername()));
                } else {
                    user.setLastPing(Time.currentTime());
                }
            });

            // If any user has not been pinged for 5 minutes, remove them.
            // Only do this for users that the server thinks are still online, offline users will be removed automatically after 5 minutes.
            coreUserManager.runForEach(user -> {
                long time = Time.currentTime();
                if (time - user.getLastPing() > 5 * 60 * 1000L && user.isOnline()) {
                    log.warning("Player " + user.getName() + " has not been pinged for 5 minutes, removing them from the proxy.");
                    removeUser(user, false);
                }
            });
        }, 0L, 1L, TimeUnit.MINUTES);
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
        coreUserManager.removeUser(user);
        user.delete();
        // Remove the user from the list of muted users for all players, if they had this player muted.
        coreUserManager.unmuteUser(user);
        UserRemove userRemoveEvent = new UserRemove(user.getUuid());

        chatHandler.handle(userRemoveEvent);
        if (!shutdown) {
            log.info(String.format("Removed user %s from the proxy, they have been offline for more than 5 minutes", user.getName()));
        } else {
            Optional<OnlineUser> optionalOnlineUser = coreUserManager.getOnlineUsers().stream().filter(onlineUser -> onlineUser.getUuid().equals(user.getUuid())).findFirst();
            optionalOnlineUser.ifPresent(coreUserManager::removeOnlineUser);
            chatHandler.handle(new OnlineUserRemove(user.getUuid()));
            log.info(String.format("Removed user %s from the proxy due to shutdown", user.getName()));
        }
    }

    private void sendReviewerMessages(UserConnectRequest request) {
        //Show the number of submitted plots.
        String uuid = request.getUuid();
        // Get the user.
        User user = coreUserManager.getUserByUuid(uuid);
        if (user == null) {
            return;
        }
        sendPlotReviewMessage(user, request.isArchitect(), request.isReviewer(), uuid, "There %s %s %s to review.", false);
        sendPlotVerifyMessage(user, request.isReviewer(), uuid, "There %s %s %s to verify.", false);

        //Show the number of submitted regions requests.
        if (request.isReviewer()) {
            int regions = regionSQL.getInt("SELECT COUNT(region) FROM region_requests WHERE staff_accept=0;");
            if (regions != 0) {
                Component regionMessage = ChatUtils.success("There " + (regions == 1 ? "is" : "are") + " %s region " + (regions == 1 ? "request" : "requests") + " to review.", String.valueOf(regions));
                DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), uuid, "server", regionMessage, false);
                chatHandler.handle(directMessage);
            }

            //Show the number of submitted navigation requests;
            int navigation = globalSQL.getInt("SELECT COUNT(location) FROM location_requests;");
            if (navigation != 0) {
                Component navigationMessage = ChatUtils.success("There " + (navigation == 1 ? "is" : "are") + " %s navigation " + (navigation == 1 ? "request" : "requests") + " to review.", String.valueOf(navigation));
                DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), uuid, "server", navigationMessage, false);
                chatHandler.handle(directMessage);
            }
        }
    }

    private void sendPlotReviewMessage(User user, boolean isArchitect, boolean isReviewer, String uuid, String
        messageTemplate, boolean includeZero) {
        if (isArchitect || isReviewer) {
            int plots = plotSQL.getReviewablePlotCount(uuid, isArchitect, isReviewer);
            if (user.getPreviousPlotSubmissionCount() != plots && (plots != 0 || includeZero)) {
                user.setPreviousPlotSubmissionCount(plots);
                Component plotMessage = ChatUtils.success(messageTemplate, ChatUtils.success(plots == 1 ? "is" : "are"), Component.text(String.valueOf(plots), NamedTextColor.DARK_AQUA), ChatUtils.success(plots == 1 ? "plot" : "plots"));
                DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), uuid, "server", plotMessage, false);
                chatHandler.handle(directMessage);
            }
        }
    }

    private void sendPlotVerifyMessage(User user, boolean isReviewer, String uuid, String messageTemplate, boolean includeZero) {
        if (isReviewer) {
            int plots = plotSQL.getVerifiablePlotCount(uuid, true);
            if (user.getPreviousPlotVerificationCount() != plots && (plots != 0 || includeZero)) {
                user.setPreviousPlotVerificationCount(plots);
                Component plotMessage = ChatUtils.success(messageTemplate, ChatUtils.success(plots == 1 ? "is" : "are"), Component.text(String.valueOf(plots), NamedTextColor.DARK_AQUA), ChatUtils.success(plots == 1 ? "plot" : "plots"));
                DirectMessage directMessage = new DirectMessage(ChatChannels.GLOBAL.getChannelName(), uuid, "server", plotMessage, false);
                chatHandler.handle(directMessage);
            }
        }
    }

    private void removeInvites(String uuid) {
        plotSQL.update("DELETE FROM plot_invites WHERE owner='" + uuid + "';");
        plotSQL.update("DELETE FROM zone_invites WHERE owner='" + uuid + "';");
        regionSQL.update("DELETE FROM region_invites WHERE owner='" + uuid + "';");

        plotSQL.update("DELETE FROM plot_invites WHERE uuid='" + uuid + "';");
        plotSQL.update("DELETE FROM zone_invites WHERE uuid='" + uuid + "';");
        regionSQL.update("DELETE FROM region_invites WHERE uuid='" + uuid + "';");
    }

    private void sendConnectMessage(String message, User user, Color colour) {
        discord.sendConnectEmbed(message, user.getName(), user.getUuid(), user.getPlayerSkin(), colour, null);
        sendConnectMessageToServer(message, user.getName());
    }

    private void sendConnectMessageToServer(String message, String name) {
        // Construct a chat message to send to the servers.
        Component component = Component.text(message.replace("%player%", name), NamedTextColor.YELLOW);
        ChatMessage chatMessage = new ChatMessage(GLOBAL.getChannelName(), SERVER_SENDER, component);
        chatManager.handle(chatMessage);
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
