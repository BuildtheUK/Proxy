package org.btuk.proxy.core.socket;

import lombok.extern.java.Log;

import org.btuk.network.lib.dto.*;
import org.btuk.network.lib.socket.SocketHandler;
import org.btuk.proxy.core.chat.ChatManager;
import org.btuk.proxy.core.discord.Discord;
import org.btuk.proxy.core.regions.RegionManager;
import org.btuk.proxy.core.server.ServerManager;
import org.btuk.proxy.core.user.UserManager;

@Log
public class ProxySocketHandler implements SocketHandler {

    private final ChatManager chatManager;
    private final Discord discord;
    private final UserManager userManager;
    private final ServerManager serverManager;
    private final RegionManager regionManager;

    public ProxySocketHandler(ChatManager chatManager, Discord discord, UserManager userManager, ServerManager serverManager, RegionManager regionManager) {
        this.chatManager = chatManager;
        this.discord = discord;
        this.userManager = userManager;
        this.serverManager = serverManager;
        this.regionManager = regionManager;
    }

    @Override
    public synchronized AbstractTransferObject handle(AbstractTransferObject abstractTransferObject) {
        // Handle the different objects.
        switch (abstractTransferObject) {
            case ChatMessage chatMessage -> {
                chatManager.handle(chatMessage, true);
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
            case RegionRequestEvent regionRequestEvent -> regionManager.handleRegionRequestEvent(regionRequestEvent);
            default ->
                log.warning("Socket object has an unrecognised type: " + abstractTransferObject.getClass().getTypeName());
        }
        return null;
    }
}
