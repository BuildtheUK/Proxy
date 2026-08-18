package org.btuk.proxy.core.socket;

import lombok.extern.java.Log;
import org.btuk.network.lib.dto.AbstractTransferObject;
import org.btuk.network.lib.dto.ChatMessage;
import org.btuk.network.lib.dto.DirectMessage;
import org.btuk.network.lib.dto.DiscordDirectMessage;
import org.btuk.network.lib.dto.DiscordEmbed;
import org.btuk.network.lib.dto.DiscordLinking;
import org.btuk.network.lib.dto.DiscordRole;
import org.btuk.network.lib.dto.FocusEvent;
import org.btuk.network.lib.dto.ModerationEvent;
import org.btuk.network.lib.dto.MuteEvent;
import org.btuk.network.lib.dto.PlotMessage;
import org.btuk.network.lib.dto.PrivateMessage;
import org.btuk.network.lib.dto.ReplyMessage;
import org.btuk.network.lib.dto.ServerShutdown;
import org.btuk.network.lib.dto.ServerStartup;
import org.btuk.network.lib.dto.SwitchServerEvent;
import org.btuk.network.lib.dto.TeleportEvent;
import org.btuk.network.lib.dto.UserConnectRequest;
import org.btuk.network.lib.dto.UserDisconnect;
import org.btuk.network.lib.dto.UserUpdate;
import org.btuk.network.lib.socket.SocketHandler;
import org.btuk.proxy.core.chat.ChatManager;
import org.btuk.proxy.core.discord.Discord;
import org.btuk.proxy.core.server.ServerManager;
import org.btuk.proxy.core.user.UserManager;

@Log
public class ProxySocketHandler implements SocketHandler {

    private final ChatManager chatManager;
    private final Discord discord;
    private final UserManager userManager;
    private final ServerManager serverManager;

    public ProxySocketHandler(ChatManager chatManager, Discord discord, UserManager userManager, ServerManager serverManager) {
        this.chatManager = chatManager;
        this.discord = discord;
        this.userManager = userManager;
        this.serverManager = serverManager;
    }

    @Override
    public synchronized AbstractTransferObject handle(AbstractTransferObject abstractTransferObject) {
        // Handle the different objects.
        switch (abstractTransferObject) {
            case ChatMessage chatMessage -> {
                chatManager.handle(chatMessage);
            }
            case DirectMessage directMessage -> chatManager.handle(directMessage);
            case PrivateMessage privateMessage -> chatManager.handle(privateMessage);
            case ReplyMessage replyMessage -> chatManager.handle(replyMessage);
            case DiscordDirectMessage discordDirectMessage -> discord.handle(discordDirectMessage);
            case DiscordEmbed discordEmbed -> discord.handle(discordEmbed);
            case DiscordLinking discordLinking -> discord.handle(discordLinking);
            case DiscordRole discordRole -> discord.handle(discordRole);
            case UserConnectRequest userConnect -> userManager.handleUserConnect(userConnect);
            case UserDisconnect userDisconnect -> userManager.handleUserDisconnect(userDisconnect);
            case UserUpdate userUpdate -> userManager.handleUserUpdate(userUpdate);
            case SwitchServerEvent switchServerEvent -> userManager.handleSwitchServerEvent(switchServerEvent);
            case MuteEvent muteEvent -> userManager.handleMuteEvent(muteEvent);
            case
                ModerationEvent moderationEvent -> userManager.handleModerationEvent(moderationEvent);
            case FocusEvent focusEvent -> userManager.handleFocusEvent(focusEvent);
            case ServerStartup serverStart -> serverManager.addServer(serverStart);
            case ServerShutdown serverClose -> serverManager.removeServer(serverClose);
            case PlotMessage plotMessage -> userManager.sendPlotMessageToAll(plotMessage);
            case TeleportEvent teleportEvent -> userManager.handleTeleportEvent(teleportEvent);
            default ->
                log.warning("Socket object has an unrecognised type: " + abstractTransferObject.getClass().getTypeName());
        }
        return null;
    }
}
