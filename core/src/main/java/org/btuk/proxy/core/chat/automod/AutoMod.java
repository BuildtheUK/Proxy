package org.btuk.proxy.core.chat.automod;

import lombok.extern.java.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.user.CoreUserManager;
import org.btuk.proxy.core.user.User;

/**
 * Represents the auto-moderation system for managing chat messages.
 */
@Log
public class AutoMod {

    private static final PlainTextComponentSerializer SERIALIZER = PlainTextComponentSerializer.builder().build();

    private final CoreUserManager userManager;

    private final List<AutoModRule> rules = new ArrayList<>();

    public AutoMod(CoreUserManager userManager, Config autoModConfig) {
        this.userManager = userManager;
        loadConfig(autoModConfig);
    }

    /**
     * Moderates a chat message based on the configured rules.
     *
     * @param sender           the sender of the message
     * @param messageComponent the message component to moderate
     * @return true if the message should be blocked
     */
    public boolean moderate(String sender, Component messageComponent) {
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
        rules.forEach(rule -> checkRule(rule, candidateWords, user, message));
        checkUser(user);
        return false;
    }

    /**
     * Checks if the user should be muted based on their moderation history.
     *
     * @param user the user to check
     */
    private void checkUser(User user) {
        // TODO: Mute user if they have accumulated too many points.
    }

    private static void loadConfig(Config config) {

    }

    private static void checkRule(AutoModRule rule, Map<String, CandidateWord> candidateWords, User user, String message) {
        List<AutoModMatch> matches = rule.getMatches(candidateWords);
        if (matches.isEmpty()) {
            return;
        }
        long timestamp = System.currentTimeMillis();
        switch (rule) {
            case AutoModMuteRule muteRule -> {
                // TODO: mute the user and notify the moderation team.
            }
            case AutoModFlagRule flagRule -> matches.forEach(match -> user.addAutoModFlag(new AutoModFlag(flagRule, timestamp, message, match)));
            default -> log.warning(String.format("Unknown rule type: %s", rule.getClass().getSimpleName()));
        }
    }
}
