package org.btuk.proxy.core.chat.automod;

import java.time.Duration;
import java.util.List;

public class AutoModMuteRule extends AutoModRule {

    public AutoModMuteRule(String id, List<String> flaggedWords, Duration duration) {
        super(id, flaggedWords, duration);
    }

    @Override
    public boolean blockMessage() {
        return true;
    }
}
