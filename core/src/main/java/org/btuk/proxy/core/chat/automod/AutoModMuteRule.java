package org.btuk.proxy.core.chat.automod;

import lombok.extern.java.Log;

import java.time.Duration;
import java.util.List;

@Log
public class AutoModMuteRule extends AutoModRule {

    public AutoModMuteRule(String id, List<String> flaggedWords, Duration duration) {
        super(id, flaggedWords, duration);
        log.info(String.format("Loaded mute rule, id: %s, flagged words: %s", id, flaggedWords));
    }

    @Override
    public boolean blockMessage() {
        return true;
    }
}
