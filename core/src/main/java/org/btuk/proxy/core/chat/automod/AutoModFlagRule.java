package org.btuk.proxy.core.chat.automod;

import lombok.Getter;

import java.time.Duration;
import java.util.List;

public class AutoModFlagRule extends AutoModRule {

    public static final String TYPE = "flag";

    @Getter
    private final int points;

    private final boolean deleteMessage;

    public AutoModFlagRule(List<String> flaggedWords, int points, Duration duration, boolean deleteMessage) {
        super(flaggedWords, duration);
        this.points = points;
        this.deleteMessage = deleteMessage;
    }
}
