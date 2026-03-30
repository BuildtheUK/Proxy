package org.btuk.proxy.core.chat.automod;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record CandidateWord(String normalized, List<String> originals) {

    public CandidateWord(String normalized) {
        this(normalized, new ArrayList<>());
    }

    public void addOriginal(String original) {
        originals.add(original);
    }
}