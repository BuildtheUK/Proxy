package org.btuk.proxy.core.chat.automod;

import java.util.LinkedHashSet;
import java.util.Set;

public record CandidateWord(String normalized, Set<String> originals) {

    public CandidateWord(String normalized) {
        this(normalized, new LinkedHashSet<>());
    }

    public void addOriginal(String original) {
        originals.add(original);
    }
}