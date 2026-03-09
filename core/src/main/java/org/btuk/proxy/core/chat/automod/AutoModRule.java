package org.btuk.proxy.core.chat.automod;

import lombok.Getter;

import java.time.Duration;
import java.util.List;

public abstract class AutoModRule {

    private final List<String> flaggedWords;

    @Getter
    private final Duration duration;

    public AutoModRule(List<String> flaggedWords, Duration duration) {
        this.flaggedWords = flaggedWords;
        this.duration = duration;
    }
}
