package org.btuk.proxy.core.chat.automod;

import lombok.Getter;

import java.text.Normalizer;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class AutoModRule {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern NON_WHITESPACE_PATTERN = Pattern.compile("\\S+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]");

    private final Set<String> flaggedWords;
    @Getter
    private final String id;

    @Getter
    private final Duration duration;

    public AutoModRule(String id, List<String> flaggedWords, Duration duration) {
        this.id = id;
        this.flaggedWords = flaggedWords.stream()
            .map(AutoModRule::normalize)
            .filter(word -> !word.isBlank())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.duration = duration;
    }

    public abstract boolean blockMessage();

    /**
     * Matches words against flagged words.
     *
     * @param candidateWords map of candidate words keyed by normalized value
     * @return list of matches
     */
    public List<AutoModMatch> getMatches(Map<String, CandidateWord> candidateWords) {
        return candidateWords.values().stream()
            .filter(candidateWord -> flaggedWords.contains(candidateWord.normalized()))
            .flatMap(candidateWord -> candidateWord.originals().stream()
                .map(original -> new AutoModMatch(original, candidateWord.normalized())))
            .toList();
    }

    /**
     * Gets a map of candidate words based on a message.
     *
     * @param message the message to get candidates for
     * @return map of candidate words keyed by normalized value
     */
    public static LinkedHashMap<String, CandidateWord> getCandidateWords(String message) {
        LinkedHashMap<String, CandidateWord> candidates = new LinkedHashMap<>();

        Matcher tokenMatcher = TOKEN_PATTERN.matcher(message);
        while (tokenMatcher.find()) {
            String originalToken = tokenMatcher.group();
            String normalizedToken = normalize(originalToken);

            addCandidate(candidates, normalizedToken, originalToken);
        }

        Matcher chunkMatcher = NON_WHITESPACE_PATTERN.matcher(message);
        while (chunkMatcher.find()) {
            String originalChunk = chunkMatcher.group();
            String normalizedChunk = NON_ALPHANUMERIC.matcher(normalize(originalChunk)).replaceAll("");

            addCandidate(candidates, normalizedChunk, originalChunk);
        }

        return candidates;
    }

    private static void addCandidate(LinkedHashMap<String, CandidateWord> candidates, String normalized, String original) {
        if (normalized.isBlank() || original.isBlank()) {
            return;
        }

        candidates.computeIfAbsent(normalized, CandidateWord::new).addOriginal(original);
    }

    private static String normalize(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKD)
            .toLowerCase(Locale.ROOT);

        normalized = DIACRITICS.matcher(normalized).replaceAll("");

        return normalized
            .replace('@', 'a')
            .replace('$', 's')
            .replace('0', 'o')
            .replace('1', 'i')
            .replace('3', 'e')
            .replace('4', 'a')
            .replace('5', 's')
            .replace('7', 't')
            .trim();
    }
}
