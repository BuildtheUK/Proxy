package org.btuk.proxy.core.chat.automod;

import lombok.Getter;
import net.kyori.adventure.text.Component;

/**
 * Represent an auto mod flag for a specific user.
 */
public class AutoModFlag {

    private final AutoModFlagRule rule;

    private final long timestamp;

    @Getter
    private final String message;

    @Getter
    private final AutoModMatch match;

    public AutoModFlag(AutoModFlagRule rule, long timestamp, String message, AutoModMatch match) {
        this.rule = rule;
        this.timestamp = timestamp;
        this.message = message;
        this.match = match;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > rule.getDuration().toMillis();
    }

    public int getPoints() {
        return rule.getPoints();
    }
}
