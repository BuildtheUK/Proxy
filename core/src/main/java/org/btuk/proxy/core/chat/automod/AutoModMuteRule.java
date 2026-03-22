package org.btuk.proxy.core.chat.automod;

import java.time.Duration;
import java.util.List;

public class AutoModMuteRule extends AutoModRule {

    public static final String TYPE = "mute";

    public AutoModMuteRule(List<String> flaggedWords, Duration duration) {
        super(flaggedWords, duration);
    }

    @Override
    public boolean blockMessage() {
        return true;
    }
}
