package net.bteuk.proxy.chat;

import net.bteuk.network.lib.dto.ChatMessage;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.PrivateMessage;
import net.bteuk.network.lib.dto.ReplyMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.bteuk.proxy.Proxy;
import net.bteuk.proxy.User;
import net.bteuk.proxy.UserManager;
import net.bteuk.proxy.utils.Analytics;
import net.bteuk.proxy.utils.Time;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import java.util.List;

import static net.bteuk.network.lib.enums.ChatChannels.GLOBAL;
import static net.bteuk.proxy.utils.Constants.DISCORD_SENDER;
import static net.bteuk.proxy.utils.Constants.SERVER_SENDER;
import static net.bteuk.proxy.utils.Moderation.getMuteDuration;
import static net.bteuk.proxy.utils.Moderation.getMutedReason;

/**
 * The chat manager keeps track of all the channels, players and statuses.
 */
public class ChatManager {

    private final UserManager userManager;

    private static final List<String> SERVER_USERS = List.of(new String[]{SERVER_SENDER, DISCORD_SENDER});

    private static final String FOCUS_ENABLED_PRESET = "%s is in focus mode, unable to send message.";

    public ChatManager(UserManager userManager) {
        this.userManager = userManager;
    }

    /**
     * Handle a chat message.
     * A chat message must be split into direct messages for each player who must receive it.
     *
     * @param chatMessage the chat message
     */
    public void handle(ChatMessage chatMessage) {
        // Send a direct message to all players
        for (User user : userManager.getUsers()) {
            sendDirectMessage(new DirectMessage(chatMessage.getChannel(), user.getUuid(), chatMessage.getSender(), chatMessage.getComponent(), false));
        }
        if (!SERVER_USERS.contains(chatMessage.getSender())) {
            Analytics.addMessage(chatMessage.getSender(), Time.getDate(Time.currentTime()));
        }
    }

    /**
     * Handle a direct message.
     * A direct message will be sent to a specific player if they don't have the sender muted.
     *
     * @param replyMessage reply message
     */
    public void handle(ReplyMessage replyMessage) {
        User sender = userManager.getUserByName(replyMessage.getSender());
        if (sender == null) {
            return;
        }
        if (sender.getLastMessagedUserID() == null) {
            sendDirectMessage(new DirectMessage(GLOBAL.getChannelName(), sender.getUuid(), SERVER_SENDER, ChatUtils.error("You have not messaged anyone yet."), false));
            return;
        }
        User receiver = userManager.getUserByUuid(sender.getLastMessagedUserID());
        if (receiver == null) {
            sendDirectMessage(new DirectMessage(GLOBAL.getChannelName(), sender.getUuid(), SERVER_SENDER, ChatUtils.error("%s is not online. No message sent",
                    Proxy.getInstance().getGlobalSQL().getString("SELECT name FROM player_data WHERE uuid = '" + sender.getLastMessagedUserID() + "';")), false));
            return;
        }
        Component message = ChatUtils.directMessage(receiver.getName(), sender.getName(), replyMessage.getMessage());
        handle(new DirectMessage(GLOBAL.getChannelName(), receiver.getUuid(), sender.getUuid(), message, replyMessage.isOffline()));
    }

    /**
     * Handle a direct message.
     * A direct message will be sent to a specific player if they don't have the sender muted.
     *
     * @param privateMessage direct message from a user
     */
    public void handle(PrivateMessage privateMessage) {
        User sender = userManager.getUserByName(privateMessage.getSender());
        if (sender == null) {
            return;
        }
        User receiver = userManager.getUserByName(privateMessage.getRecipient());
        if (receiver == null) {

            if (checkIfUserExistsByName(privateMessage.getRecipient())) {
                sendDirectMessage(new DirectMessage(GLOBAL.getChannelName(), sender.getUuid(), SERVER_SENDER,
                        ChatUtils.error("%s is not online. No message sent", privateMessage.getRecipient()), false));
            } else {
                sendDirectMessage(new DirectMessage(GLOBAL.getChannelName(), sender.getUuid(), SERVER_SENDER, ChatUtils.error("Unknown recipient. Unable to send message"), false));
            }
            return;
        }
        Component message = ChatUtils.directMessage(receiver.getName(), sender.getName(), privateMessage.getMessage());
        handle(new DirectMessage(GLOBAL.getChannelName(), receiver.getUuid(), sender.getUuid(), message, privateMessage.isOffline()));
    }

    /**
     * Handle a direct message.
     * A direct message will be sent to a specific player if they don't have the sender muted.
     *
     * @param directMessage direct message
     */
    public void handle(DirectMessage directMessage) {
        // If the message is sent a by a player, and the recipient is in focus mode, block the message and let the sender know.
        if (!SERVER_USERS.contains(directMessage.getSender())) {
            User sender = userManager.getUserByUuid(directMessage.getSender());
            User receiver = userManager.getUserByUuid(directMessage.getRecipient());

            if (sender.isMuted()) {
                sendDirectMessage(new DirectMessage(GLOBAL.getChannelName(), directMessage.getSender(), SERVER_SENDER, getMutedComponent(sender), false));
                return;
            }
            if (receiver == null) {
                sendDirectMessage(
                        new DirectMessage(GLOBAL.getChannelName(), directMessage.getSender(), SERVER_SENDER, ChatUtils.error("Unknown recipient. Unable to send message"), false));
                return;
            }
            if (receiver.isFocusEnabled()) {
                sendDirectMessage(
                        new DirectMessage(GLOBAL.getChannelName(), directMessage.getSender(), SERVER_SENDER, ChatUtils.error(FOCUS_ENABLED_PRESET, receiver.getName()), false));
                return;
            }
            // Checks if receiver is online, sends error if not.
            if (!receiver.isOnline() && !directMessage.isOffline()) {
                sendDirectMessage(new DirectMessage(GLOBAL.getChannelName(), directMessage.getSender(), SERVER_SENDER,
                        ChatUtils.error("%s is not online. No message sent", receiver.getName()), false));
                return;
            }
            // Updates both player's last messaged players.
            sender.setLastMessagedUserID(directMessage.getRecipient());
            receiver.setLastMessagedUserID(directMessage.getSender());

            // Send message to both the sender and recipient.
            sendDirectMessage(
                    new DirectMessage(GLOBAL.getChannelName(), directMessage.getRecipient(), directMessage.getSender(), directMessage.getComponent(), directMessage.isOffline()));
            sendDirectMessage(
                    new DirectMessage(GLOBAL.getChannelName(), directMessage.getSender(), directMessage.getSender(), directMessage.getComponent(), directMessage.isOffline()));
            return;
        }
        sendDirectMessage(directMessage);
    }

    /**
     * checks if a user exists based on name
     */
    private boolean checkIfUserExistsByName(String name) {
        return Proxy.getInstance().getGlobalSQL().checkIfUserExistsByName(name);
    }

    /**
     * Handle a direct message.
     * A direct message will be sent to a specific player if they don't have the sender muted.
     *
     * @param directMessage the direct message
     */
    public void sendDirectMessage(DirectMessage directMessage) {
        // If the sender is muted for the recipient, don't send the message.
        if (!userManager.isMutedForUser(directMessage.getRecipient(), directMessage.getSender())) {
            User user = userManager.getUserByUuid(directMessage.getRecipient());
            if (user != null && user.isOnline()) {
                // Only send the message if the user isn't in focus or if the sender is the server.
                if (!user.isFocusEnabled() || directMessage.getSender().equals(SERVER_SENDER)) {
                    Proxy.getInstance().getChatHandler().handle(directMessage);
                }
            } else if (directMessage.isOffline()) {
                // Send offline message.
                Proxy.getInstance().getGlobalSQL().insertMessage(directMessage.getRecipient(), GsonComponentSerializer.gson().serialize(directMessage.getComponent()));
            }
        }
    }

    public Component getMutedComponent(User user) {
        if (user.isMuted()) {
            return ChatUtils.error("You have been muted for ").append(Component.text(getMutedReason(user.getUuid()), NamedTextColor.DARK_RED)).append(ChatUtils.error(" until "))
                    .append(Component.text(getMuteDuration(user.getUuid()), NamedTextColor.DARK_RED));
        } else {
            return null;
        }
    }
}
