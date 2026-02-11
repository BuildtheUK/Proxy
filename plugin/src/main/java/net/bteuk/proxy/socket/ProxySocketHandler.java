package net.bteuk.proxy.socket;

import net.bteuk.network.lib.dto.AbstractTransferObject;
import net.bteuk.network.lib.dto.ChatMessage;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.dto.DiscordDirectMessage;
import net.bteuk.network.lib.dto.DiscordEmbed;
import net.bteuk.network.lib.dto.DiscordLinking;
import net.bteuk.network.lib.dto.DiscordRole;
import net.bteuk.network.lib.dto.FocusEvent;
import net.bteuk.network.lib.dto.ModerationEvent;
import net.bteuk.network.lib.dto.MuteEvent;
import net.bteuk.network.lib.dto.PlotMessage;
import net.bteuk.network.lib.dto.PrivateMessage;
import net.bteuk.network.lib.dto.ReplyMessage;
import net.bteuk.network.lib.dto.ServerShutdown;
import net.bteuk.network.lib.dto.ServerStartup;
import net.bteuk.network.lib.dto.SwitchServerEvent;
import net.bteuk.network.lib.dto.UserConnectRequest;
import net.bteuk.network.lib.dto.UserDisconnect;
import net.bteuk.network.lib.dto.UserUpdate;
import net.bteuk.network.lib.socket.SocketHandler;
import net.bteuk.proxy.Proxy;
import net.bteuk.proxy.chat.ChatManager;

public class ProxySocketHandler implements SocketHandler {

    private final ChatManager manager;

    public ProxySocketHandler(ChatManager manager) {
        this.manager = manager;
    }

    @Override
    public synchronized AbstractTransferObject handle(AbstractTransferObject abstractTransferObject) {
        // Handle the different objects.
        switch (abstractTransferObject) {
            case ChatMessage chatMessage -> {
                manager.handle(chatMessage);
                Proxy.getInstance().getDiscord().handle(chatMessage);
            }
            case DirectMessage directMessage -> manager.handle(directMessage);
            case PrivateMessage privateMessage -> manager.handle(privateMessage);
            case ReplyMessage replyMessage -> manager.handle(replyMessage);
            case DiscordDirectMessage discordDirectMessage -> Proxy.getInstance().getDiscord().handle(discordDirectMessage);
            case DiscordEmbed discordEmbed -> Proxy.getInstance().getDiscord().handle(discordEmbed);
            case DiscordLinking discordLinking -> Proxy.getInstance().getDiscord().handle(discordLinking);
            case DiscordRole discordRole -> Proxy.getInstance().getDiscord().handle(discordRole);
            case UserConnectRequest userConnect -> Proxy.getInstance().getUserManager().handleUserConnect(userConnect);
            case UserDisconnect userDisconnect -> Proxy.getInstance().getUserManager().handleUserDisconnect(userDisconnect);
            case UserUpdate userUpdate -> Proxy.getInstance().getUserManager().handleUserUpdate(userUpdate);
            case SwitchServerEvent switchServerEvent -> Proxy.getInstance().getUserManager().handleSwitchServerEvent(switchServerEvent);
            case MuteEvent muteEvent -> Proxy.getInstance().getUserManager().handleMuteEvent(muteEvent);
            case ModerationEvent moderationEvent -> // Currently the moderation is handled on the servers, this is event is purely to update Tab for (un)muting.
                    Proxy.getInstance().getTabManager().updatePlayerByUuid(moderationEvent.getUuid());
            case FocusEvent focusEvent -> Proxy.getInstance().getUserManager().handleFocusEvent(focusEvent);
            case ServerStartup serverStart -> Proxy.getInstance().getServerManager().addServer(serverStart);
            case ServerShutdown serverClose -> Proxy.getInstance().getServerManager().removeServer(serverClose);
            case PlotMessage plotMessage -> Proxy.getInstance().getUserManager().sendPlotMessageToAll(plotMessage);
            default -> Proxy.getInstance().getLogger().warn("Socket object has an unrecognised type {}", abstractTransferObject.getClass().getTypeName());
        }
        return null;
    }
}
