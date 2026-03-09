package org.btuk.proxy.core.chat.automod;

import lombok.Getter;

import java.time.Duration;
import java.util.List;

public class AutoModFlagRule extends AutoModRule {

    @Getter
    private final int points;

    public AutoModFlagRule(List<String> flaggedWords, int points, Duration duration) {
        super(flaggedWords, duration);
        this.points = points;
    }
}
