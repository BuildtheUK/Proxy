package org.btuk.proxy.core.discord;

import lombok.extern.java.Log;
import net.bteuk.network.lib.dto.DiscordLinking;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

import org.btuk.proxy.core.chat.ChatHandler;

@Log
public class BotChatListener extends ListenerAdapter {

    private final ChatHandler chatHandler;
    private final List<Linked> linking;

    public BotChatListener(ChatHandler chatHandler, List<Linked> linking) {
        this.chatHandler = chatHandler;
        this.linking = linking;
        log.info("Enabling Bot Chat Listener");
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {

        // Check channel type.
        if (event.getChannelType() != ChannelType.PRIVATE) {
            return;
        }

        // Block messages from bot.
        if (event.getAuthor().isBot()) {
            return;
        }

        if (StringUtils.isBlank(event.getMessage().getContentRaw())) {
            return;
        }

        // Check if the author is in the linked list.
        Linked l = null;
        for (Linked linked : linking) {

            // Check message
            if (event.getMessage().getContentRaw().equalsIgnoreCase(linked.token)) {
                // Link accounts.
                DiscordLinking discordLinking = new DiscordLinking();
                discordLinking.setUuid(linked.uuid);
                discordLinking.setDiscordId(event.getAuthor().getIdLong());

                chatHandler.handle(discordLinking);

                log.info(String.format("Linking Discord user of " + event.getAuthor().getName() + " to Minecraft uuid " + linked.uuid));

                l = linked;
                break;
            }
        }

        if (l != null) {
            //Close link and remove it from list.
            l.close();
            linking.remove(l);
        }
    }
}
