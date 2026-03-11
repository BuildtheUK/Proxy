package org.btuk.proxy.core.chat.automod;

import lombok.extern.java.Log;
import net.bteuk.network.lib.dto.DirectMessage;
import net.bteuk.network.lib.utils.ChatUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.btuk.proxy.core.chat.ChatHandler;
import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.discord.Discord;
import org.btuk.proxy.core.tab.TabManager;
import org.btuk.proxy.core.user.CoreUserManager;
import org.btuk.proxy.core.user.User;
import org.btuk.proxy.core.utils.Moderation;
import org.btuk.proxy.core.utils.Time;

import static net.bteuk.network.lib.enums.ChatChannels.GLOBAL;
import static org.btuk.proxy.core.utils.Constants.SERVER_SENDER;

/**
 * Represents the auto-moderation system for managing chat messages.
 */
@Log
public class AutoMod {

    private static final PlainTextComponentSerializer SERIALIZER = PlainTextComponentSerializer.builder().build();

    private static final String AUTOMOD_REASON = "AUTOMOD";

    private static final Component AUTOMOD_REASON_COMPONENT = ChatUtils.error("You have been muted by the auto-moderation system due to recent chat messages, the moderation team will evaluate this.");

    private static final Duration FLAG_MUTE_DURATION = Duration.ofHours(12);

    private final CoreUserManager userManager;

    private final Moderation moderation;

    private final Discord discord;

    private final ChatHandler chatHandler;

    private final TabManager tabManager;

    private final AutoModConfig autoModConfig;

    public AutoMod(CoreUserManager userManager, Config autoModConfig, Moderation moderation, Discord discord, ChatHandler chatHandler, TabManager tabManager) {
        this.userManager = userManager;
        this.moderation = moderation;
        this.discord = discord;
        this.chatHandler = chatHandler;
        this.tabManager = tabManager;
        this.autoModConfig = new AutoModConfig(autoModConfig);
        this.autoModConfig.loadRules();
    }

    /**
     * Moderates a chat message based on the configured rules.
     *
     * @param sender           the sender of the message
     * @param messageComponent the message component to moderate
     * @return true if the message should be blocked
     */
    public boolean moderate(String sender, Component messageComponent) {
        if (!autoModConfig.isEnabled()) {
            return false;
        }
        User user = userManager.getUserByUuid(sender);
        if (user == null) {
            return true;
        }
        user.removeExpiredAutoModFlags();
        String message = SERIALIZER.serialize(messageComponent);
        return checkMessage(user, message);
    }

    /**
     * Checks if a message should be blocked and updates the users' moderation history.
     *
     * @param user    the sender of the message
     * @param message the message to check
     * @return true if the message should be blocked
     */
    private boolean checkMessage(User user, String message) {
        Map<String, CandidateWord> candidateWords = AutoModRule.getCandidateWords(message);
        autoModConfig.getRules().forEach(rule -> checkRule(rule, candidateWords, user, message));
        checkUser(user);
        return false;
    }

    /**
     * Checks if the user should be muted based on their moderation history.
     *
     * @param user the user to check
     */
    private void checkUser(User user) {
        if (user.getAutoModFlagPoints() > autoModConfig.getPointsThreshold()) {
            List<AutoModFlag> flags = user.getAutoModFlags();
            muteUser(user, FLAG_MUTE_DURATION, flags.stream().map(AutoModFlag::getMatch).toList(), flags.stream().map(AutoModFlag::getMessage).distinct().toList());
        }
    }

    private void checkRule(AutoModRule rule, Map<String, CandidateWord> candidateWords, User user, String message) {
        List<AutoModMatch> matches = rule.getMatches(candidateWords);
        if (matches.isEmpty()) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        switch (rule) {
            case AutoModMuteRule muteRule -> muteUser(user, muteRule.getDuration(), matches, Collections.singletonList(message));
            case AutoModFlagRule flagRule -> matches.forEach(match -> user.addAutoModFlag(new AutoModFlag(flagRule, timestamp, message, match)));
            default -> log.warning(String.format("Unknown rule type: %s", rule.getClass().getSimpleName()));
        }
    }

    private void muteUser(User user, Duration duration, List<AutoModMatch> matches, List<String> messages) {
        long endTime = Time.currentTime() + duration.toMillis();
        moderation.mute(user.getUuid(), endTime, AUTOMOD_REASON);
        chatHandler.handle(new DirectMessage(GLOBAL.getChannelName(), user.getUuid(), SERVER_SENDER, AUTOMOD_REASON_COMPONENT, false));
        tabManager.updatePlayerByUuid(user.getUuid());
        discord.notifyModeratorsOfAutoMute(user, matches, messages, duration);
    }
}
