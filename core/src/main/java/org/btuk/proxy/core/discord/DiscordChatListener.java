package org.btuk.proxy.core.discord;

import lombok.extern.java.Log;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.lang3.StringUtils;
import org.btuk.network.lib.dto.ChatMessage;
import org.btuk.network.lib.enums.ChatChannels;
import org.btuk.network.lib.utils.ChatUtils;
import org.btuk.proxy.core.chat.ChatManager;

import static org.btuk.proxy.core.utils.Constants.DISCORD_SENDER;

@Log
public class DiscordChatListener extends ListenerAdapter {

    private final Discord discord;
    private final ChatManager chatManager;
    private final String chatChannel;
    private final String staffChannel;

    private static final Component DISCORD_PREFIX = Component.text("[Discord] ", NamedTextColor.DARK_GRAY);

    private static final Component SEPARATOR = Component.text(" > ", NamedTextColor.GRAY).decorate(TextDecoration.BOLD);

    private static final Component STAFF_PREFIX = Component.text("[Staff]", NamedTextColor.RED);

    public DiscordChatListener(Discord discord, ChatManager chatManager, String chatChannel, String staffChannel) {
        this.discord = discord;
        this.chatManager = chatManager;
        this.chatChannel = chatChannel;
        this.staffChannel = staffChannel;
        log.info("Enabling Discord Chat Listener");
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {

        // Block messages from bot and null author.
        if ((event.getMember() == null && !event.isWebhookMessage()) || event.getAuthor().equals(discord.getJda().getSelfUser())) {
            return;
        }

        // Block webhooks.
        if (event.isWebhookMessage()) {
            return;
        }

        if (StringUtils.isBlank(event.getMessage().getContentRaw())) {
            return;
        }

        // Block from all channels except linked.
        if (event.getChannel().getId().equals(chatChannel) || event.getChannel().getId().equals(staffChannel)) {
            TextColor nameColour = TextColor.color(event.getMember().getColorRaw());

            Component discordMessage = DISCORD_PREFIX
                    .append(Component.text(event.getMember().getEffectiveName(), nameColour))
                    .append(SEPARATOR)
                    .append(ChatUtils.line(event.getMessage().getContentRaw()));

            ChatChannels channel = ChatChannels.GLOBAL;

            if (event.getChannel().getId().equals(staffChannel)) {
                // Add the prefix for staff chat.
                discordMessage = STAFF_PREFIX.append(discordMessage);
                channel = ChatChannels.STAFF;
            }

            ChatMessage chatMessage = new ChatMessage(channel.getChannelName(), DISCORD_SENDER, discordMessage);
            chatManager.handle(chatMessage);
        }
    }
}
