package org.btuk.proxy.core.chat.automod;

import java.time.Duration;
import java.util.List;

public class AutoModMuteRule extends AutoModRule {

    public AutoModMuteRule(List<String> flaggedWords, Duration duration) {
        super(flaggedWords, duration);
    }
}
