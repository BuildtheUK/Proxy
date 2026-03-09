package org.btuk.proxy.core.chat.automod;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.user.CoreUserManager;
import org.btuk.proxy.core.user.User;

/**
 * Represents the auto-moderation system for managing chat messages.
 */
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
        checkUser(user);
        return false;
    }

    /**
     * Checks if the user should be muted based on their moderation history.
     *
     * @param user the user to check
     */
    private void checkUser(User user) {

    }

    private static void loadConfig(Config config) {

    }
}
