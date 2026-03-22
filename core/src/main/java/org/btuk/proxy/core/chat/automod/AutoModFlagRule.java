package org.btuk.proxy.core.chat.automod;

import lombok.Getter;

import java.time.Duration;
import java.util.List;

public class AutoModFlagRule extends AutoModRule {

    @Getter
    private final int points;

    private final boolean deleteMessage;

    public AutoModFlagRule(String id, List<String> flaggedWords, int points, Duration duration, boolean deleteMessage) {
        super(id, flaggedWords, duration);
        this.points = points;
        this.deleteMessage = deleteMessage;
    }

    @Override
    public boolean blockMessage() {
        return deleteMessage;
    }
}
