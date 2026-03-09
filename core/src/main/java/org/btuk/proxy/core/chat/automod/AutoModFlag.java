package org.btuk.proxy.core.chat.automod;

import net.kyori.adventure.text.Component;

/**
 * Represent an auto mod flag for a specific user.
 */
public class AutoModFlag {

    private final AutoModFlagRule rule;

    private final long timestamp;

    private final Component message;

    public AutoModFlag(AutoModFlagRule rule, long timestamp, Component message) {
        this.rule = rule;
        this.timestamp = timestamp;
        this.message = message;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > rule.getDuration().toMillis();
    }

    public int getPoints() {
        return rule.getPoints();
    }
}
