package org.btuk.proxy.core.discord;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.time.DurationFormatUtils;
import org.btuk.network.lib.dto.ChatMessage;
import org.btuk.network.lib.dto.DiscordDirectMessage;
import org.btuk.network.lib.dto.DiscordEmbed;
import org.btuk.network.lib.dto.DiscordLinking;
import org.btuk.network.lib.dto.DiscordRole;
import org.btuk.proxy.core.chat.ChatHandler;
import org.btuk.proxy.core.chat.ChatManager;
import org.btuk.proxy.core.chat.automod.AutoModMatch;
import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.discord.command.CommandManager;
import org.btuk.proxy.core.scheduler.Scheduler;
import org.btuk.proxy.core.tab.TabManager;
import org.btuk.proxy.core.user.CoreUserManager;
import org.btuk.proxy.core.user.User;
import org.btuk.proxy.database.sql.GlobalSQL;
import org.btuk.proxy.database.sql.PlotSQL;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Log
public class Discord {

    @Setter
    @Getter
    private JDA jda;

    private final String chatChannelId;
    private final String staffChannelId;

    private TextChannel chat;
    private TextChannel staff;
    private TextChannel supportInfo;
    private TextChannel supportChat;
    private TextChannel moderatorChat;
    private List<Long> hasRoles;
    private List<Long> giveRoles;
    
    private final List<Linked> linking = new ArrayList<>();

    private final Config config;
    private final GlobalSQL globalSQL;
    private final ChatHandler chatHandler;
    private final Scheduler scheduler;

    public Discord(Config config, GlobalSQL globalSQL, ChatHandler chatHandler, Scheduler scheduler) {

        this.config = config;
        this.globalSQL = globalSQL;
        this.chatHandler = chatHandler;
        this.scheduler = scheduler;

        //Get token from config.
        String token = config.getString("token");
        this.chatChannelId = config.getString("chat.global");
        String support_info = config.getString("chat.support.info");
        String supportChat = config.getString("chat.support.chat");
        this.staffChannelId = config.getString("chat.staff");
        String moderatorChannel = config.getString("chat.moderator");

        //Create JDABuilder.
        JDABuilder builder = JDABuilder.createDefault(token);

        builder.enableIntents(GatewayIntent.MESSAGE_CONTENT);
        builder.enableIntents(GatewayIntent.GUILD_MEMBERS);
        builder.enableIntents(GatewayIntent.DIRECT_MESSAGES);
        builder.enableIntents(GatewayIntent.GUILD_MESSAGES);

        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        builder.setChunkingFilter(ChunkingFilter.NONE);
        builder.disableCache(CacheFlag.ACTIVITY);
        builder.disableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING);
        builder.setLargeThreshold(50);

        builder.setAutoReconnect(true);

        builder.setActivity(Activity.playing(config.getString("DiscordPlaying")));

        try {
            jda = builder.build();
            jda.awaitReady();

            chat = jda.getTextChannelById(chatChannelId);
            supportInfo = jda.getTextChannelById(support_info);
            this.supportChat = jda.getTextChannelById(supportChat);
            staff = jda.getTextChannelById(staffChannelId);
            this.moderatorChat = jda.getTextChannelById(moderatorChannel);

            //Load all members into cache.
            chat.getGuild().loadMembers().onSuccess(_ -> {
                log.info("Loaded all discord members into cache");

                //Enable role syncing.
                enableRoleSyncing();
            });

        } catch (InterruptedException e) {
            log.severe("An error occurred while loading discord!");
        }
    }

    public void addJDAEventListeners(ChatManager chatManager, CoreUserManager coreUserManager, TabManager tabManager, PlotSQL plotSQL) {
        jda.addEventListener(new DiscordChatListener(this, chatManager, chatChannelId, staffChannelId));
        jda.addEventListener(new BotChatListener(chatHandler, linking));

        CommandManager commandManager = new CommandManager(coreUserManager, tabManager, globalSQL, plotSQL, config);
        jda.addEventListener(commandManager);
        jda.getGuilds().forEach(commandManager::registerCommands);
    }

    /**
     * Handle a {@link ChatMessage}.
     *
     * @param message the message to handle
     */
    public void handle(ChatMessage message) {

        // Format the message according to the formatting rules.
        String text = format(message.getComponent());

        // Send the message to the relevent channel.
        switch (message.getChannel()) {

            case "global" -> chat.sendMessage(text).queue();
            case "staff" -> staff.sendMessage(staffMessage(text)).queue();

            // Ignore chat message in all other channels, they are not intended to be posted on discord.
        }
    }

    /**
     * Handle a {@link DiscordEmbed}
     *
     * @param embed the embed to handle
     */
    public void handle(DiscordEmbed embed) {

        // Create the embed from the transfer object.
        chat.sendMessageEmbeds(createEmbed(embed)).queue();

    }

    /**
     * Handle a {@link DiscordRole}
     *
     * @param role the role to add/remove
     */
    public void handle(DiscordRole role) {

        if (role.getUuid() == null || role.getRole() == null) {
            return;
        }

        // Get the user, cancel if not exists.
        long userId = globalSQL.getLong("SELECT discord_id FROM discord WHERE uuid='" + role.getUuid() + "';");
        if (userId == 0) {
            return;
        }

        // Get the role, cancel if not exists.
        long roleId = getRoleID(role.getRole());
        if (roleId == 0) {
            return;
        }

        if (role.isAddRole()) {
            addRole(userId, roleId, true);
        } else {
            removeRole(userId, roleId, true);
        }
    }

    /**
     * Handle a {@link DiscordDirectMessage}
     *
     * @param directMessage the direct message
     */
    public void handle(DiscordDirectMessage directMessage) {

        if (directMessage.getRecipient() == null || directMessage.getMessage() == null) {
            return;
        }

        // Get the user, cancel if not exists.
        long userId = globalSQL.getLong("SELECT discord_id FROM discord WHERE uuid='" + directMessage.getRecipient() + "';");
        if (userId == 0) {
            return;
        }

        // Send direct message to the user.
        sendDirectMessage(userId, directMessage.getMessage());

    }

    /**
     * Handle a {@link DiscordLinking}
     *
     * @param discordLinking the discord linking event to handle.
     */
    public void handle(DiscordLinking discordLinking) {

        if (discordLinking.getUuid() == null) {
            return;
        }

        if (discordLinking.isUnlink()) {
            unlinkUser(discordLinking.getUuid());
        } else {
            if (discordLinking.getToken() == null) {
                return;
            }

            // Add object for linking, with a time to remove.
            // If there is already an instance, replace it.
            Linked linked = null;
            for (Linked l : linking) {
                if (l.uuid.equalsIgnoreCase(discordLinking.getUuid())) {
                    linked = l;
                }
            }

            //If there was already a task for this player, close it first.
            if (linked != null) {
                linked.close();
                linking.remove(linked);
            }

            //Create a new link.
            linking.add(new Linked(scheduler, linking, discordLinking.getUuid(), discordLinking.getToken()));
        }
    }

    private MessageEmbed createEmbed(DiscordEmbed embed) {

        EmbedBuilder builder = new EmbedBuilder();

        // Add all non-null fields from the transfer object to the embed.

        if (embed.getTitle() != null) {
            builder.setTitle(embed.getTitle());
        }

        if (embed.getAuthor() != null || embed.getIcon() != null) {
            builder.setAuthor(embed.getAuthor(), null, embed.getIcon());
        }

        if (embed.getDescription() != null) {
            builder.setDescription(embed.getDescription());
        }

        if (embed.getFields() != null) {
            embed.getFields().forEach(field ->
                    builder.addField(new MessageEmbed.Field(field.getName(), field.getValue(), field.isInline())));
        }

        if (embed.getFooter() != null) {
            builder.setFooter(embed.getFooter());
        }

        // -1 is the default colour.
        if (embed.getColour() != -1) {
            builder.setColor(embed.getColour());
        }

        return builder.build();
    }

    public void unlinkUser(String uuid) {
        //Remove the user from the discord link table.
        globalSQL.update("DELETE FROM discord WHERE uuid='" + uuid + "';");
        log.info(String.format("Unlinked user with uuid %s", uuid));
    }

    public void unlinkUser(long userId) {
        //Remove the user from the discord link table.
        globalSQL.update("DELETE FROM discord WHERE discord_id=" + userId + ";");
        log.info(("Removed discord link for " + userId + ", they are no longer in the discord server."));

        // Send an unlink message to the servers to make sure it's also unlinked there.
        DiscordLinking discordLinking = new DiscordLinking();
        discordLinking.setDiscordId(userId);
        discordLinking.setUnlink(true);
        chatHandler.handle(discordLinking);
    }

    private void sendDirectMessage(long userId, String message) {
        jda.retrieveUserById(userId).queue(user -> {
            //Open a private channel with the user and send the message.
            user.openPrivateChannel().queue(channel -> channel.sendMessage(messageLimit(message)).queue());
        });
    }

    public void addRole(long userId, long role_id, boolean sync) {
        // Only give the role if they don't have it yet.
        try {
            // Get the member.
            Member member = chat.getGuild().getMember(UserSnowflake.fromId(userId));
            if (member == null) {
                // Unlink user is linked.
                unlinkUser(userId);
                return;
            }
            // Get the role.
            Role role = chat.getGuild().getRoleById(role_id);
            if (role == null) {
                return;
            }
            // If the member does not have the role, add it.
            if (!member.getRoles().contains(role)) {
                // If successful, resync if enabled.
                chat.getGuild().addRoleToMember(member, role).queue(
                        _ -> {
                            if (sync && hasRoles != null && giveRoles != null) {
                                syncRoles();
                            }
                        }, new UnknownUserErrorHandler(this, userId)
                );
            }
        } catch (Exception e) {
            //An error occurred, the user or role is null, this is not necessarily a problem, but is being caught to prevent console spam.
        }
    }

    public void removeRole(long userId, long role_id, boolean sync) {
        // Only remove the role if they don't have it yet.
        try {
            // Get the member.
            Member member = chat.getGuild().getMember(UserSnowflake.fromId(userId));
            if (member == null) {
                // Unlink user is linked.
                unlinkUser(userId);
                return;
            }
            // Get the role.
            Role role = chat.getGuild().getRoleById(role_id);
            if (role == null) {
                return;
            }
            // If the member does not have the role, add it.
            if (member.getRoles().contains(role)) {
                chat.getGuild().removeRoleFromMember(member, role).queue(
                        _ -> {
                            if (sync && hasRoles != null && giveRoles != null) {
                                syncRoles();
                            }
                        }, new UnknownUserErrorHandler(this, userId)
                );
            }
        } catch (Exception e) {
            //An error occurred, the user or role is null, this is not necessarily a problem, but is being caught to prevent console spam.
        }
    }

    /**
     * Sends an embed for a player join/leaving the server
     * @param message the message to format
     * @param name the name of the player
     * @param uuid the uuid of the player
     * @param playerSkin the player skin
     * @param consumer to run after success
     */
    public void sendConnectEmbed(String message, String name, String uuid, String playerSkin, Color colour, Consumer<Message> consumer) {
        MessageEmbed embed = createAuthorEmbed(message.replace("%player%", name), null, Avatar.getAvatarUrl(uuid, playerSkin), colour);
        sendEmbed(embed, consumer);
    }

    private void sendEmbed(MessageEmbed embed, Consumer<Message> consumer) {
        chat.sendMessageEmbeds(embed).queue(consumer);
    }

    /**
     * Create an embed with an author as message with an icon.
     *
     * @param author author
     * @param iconUrl icon url
     * @return the {@link MessageEmbed}
     */
    public MessageEmbed createAuthorEmbed(String author, String url, String iconUrl, Color colour) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setAuthor(author, url, iconUrl);
        builder.setColor(colour);
        return builder.build();
    }

    public TextChannel getSupportInfoChannel() {
        return supportInfo;
    }

    public TextChannel getSupportChatChannel() {
        return supportChat;
    }

    public String getReviewerRoleID() {
        return String.valueOf(getRoleID("reviewer"));
    }

    public void notifyModeratorsOfAutoMute(User mutedUser, List<AutoModMatch> matches, List<String> messages, Duration duration) {

        String durationMessage = DurationFormatUtils.formatDurationWords(duration.toMillis(), true, true);
        String message = String.format("User %s has been muted by the auto-moderator for %s, please evaluate this decision and update the punishment if necessary. You can update the punishment by running the /mute command again.", mutedUser.getName(), durationMessage);
        message += String.format("\n\nUser uuid: %s", mutedUser.getUuid());
        message += "\n\nFlagged words:\n";
        message += String.join(", ", matches.stream().map(AutoModMatch::messageWord).toList());
        message = messageLimit(message);

        String attachmentContent = buildAutoMuteAttachment(matches, messages);

        moderatorChat.sendMessage(message)
            .addFiles(FileUpload.fromData(
                attachmentContent.getBytes(StandardCharsets.UTF_8),
                "automod-report.txt"
            ))
            .queue();
    }

    private String buildAutoMuteAttachment(List<AutoModMatch> matches, List<String> messages) {
        return "Flagged Words:\n" +
            matches.stream()
                .map(match -> match.messageWord() + " - " + match.flaggedWord())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("") +
            "\n\nMessages:\n" +
            String.join("\n", messages);
    }

    private void enableRoleSyncing() {

        hasRoles = config.getLongArray("role_syncing.has");
        giveRoles = config.getLongArray("role_syncing.give");

        if (hasRoles == null || giveRoles == null) {
            return;
        }

        scheduler.createRepeatingTask(this::syncRoles, 0L, 5L, TimeUnit.MINUTES);
    }

    private void syncRoles() {
        // Get lists of all members with all the roles
        Map<Role, List<Member>> hasRolesMap = fillRoleMap(hasRoles);
        Map<Role, List<Member>> giveRolesMap = fillRoleMap(giveRoles);

        // Remove the role from members that shouldn't have it.
        for (Role role : giveRolesMap.keySet()) {
            chat.getGuild().getMembersWithRoles(role).forEach(member -> {
                if (member.getRoles().stream().noneMatch(hasRolesMap::containsKey)) {
                    removeRole(member.getIdLong(), role.getIdLong(), false);
                }
            });
        }

        // Add the roles to all members who should have it.
        for (Role role : hasRolesMap.keySet()) {
            chat.getGuild().getMembersWithRoles(role).forEach(member -> {
                for (Role giveRole : giveRolesMap.keySet()) {
                    // Only give the role if they don't have it yet.
                    if (!member.getRoles().contains(giveRole)) {
                        addRole(member.getIdLong(), giveRole.getIdLong(), false);
                    }
                }
            });
        }
    }

    private Map<Role, List<Member>> fillRoleMap(List<Long> role_ids) {
        Map<Role, List<Member>> roleMap = new HashMap<>();
        for (long role_id : role_ids) {
            // Get the role.
            Role role = chat.getGuild().getRoleById(role_id);
            if (role != null) {
                //Get all members with the role.
                roleMap.put(role, chat.getGuild().getMembersWithRoles(role));
            }
        }
        return roleMap;
    }

    private static String format(Component component) {

        // Format each section of the component individually.
        StringBuilder builder = new StringBuilder();

        if (component instanceof TextComponent textComponent) {
            builder.append(format(textComponent));
        }

        return messageLimit(builder.toString());
    }

    private static String format(TextComponent component) {

        String text = PlainTextComponentSerializer.plainText().serialize(component);

        // Escape all potential discord markdown of plaintext.
        text = escapeDiscordFormatting(text);

        // Apply bold, italic and underline from the component.
        if (component.hasDecoration(TextDecoration.ITALIC)) {
            text = italic(text);
        }
        if (component.hasDecoration(TextDecoration.BOLD)) {
            text = bold(text);
        }
        if (component.hasDecoration(TextDecoration.UNDERLINED)) {
            text = underline(text);
        }

        return text;
    }

    /**
     * Remove the [Staff] prefix from staff-messages.
     *
     * @param message the message with prefix
     * @return the message without prefix
     */
    private static String staffMessage(String message) {
        if (message.startsWith("\\[Staff\\]")) {
            message = message.substring(9);
        }
        return message;
    }

    public static String escapeDiscordFormatting(String message) {
        return message.replace("@", "@\u200B")
                .replaceAll("[*_#\\[\\]()\\-`>]", "\\\\$0");
    }

    private static String italic(String message) {
        return String.format("*%s*", message);
    }

    private static String bold(String message) {
        return String.format("**%s**", message);
    }

    private static String underline(String message) {
        return String.format("__%s__", message);
    }

    private static String messageLimit(String message) {
        return messageLimit(message, "...");
    }

    private static String messageLimit(String message, String suffix) {
        if (message.length() > 2000) {
            message = message.substring(0, 2000 - suffix.length()) + suffix;
        }
        return message;
    }

    private long getRoleID(String role) {
        return config.getLong("discord_roles." + role);
    }
}
