package org.btuk.proxy.core.chat.automod;

import lombok.Getter;

import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AutoModRule {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern NON_WHITESPACE_PATTERN = Pattern.compile("\\S+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{N}]");

    private static final char SPACE = ' ';

    private final Set<String> flaggedWords;
    @Getter
    private final String id;

    @Getter
    private final Duration duration;

    private final int maxLength;

    public AutoModRule(String id, List<String> flaggedWords, Duration duration) {
        int maxLength = 0;
        this.id = id;
        java.util.Set<String> normalizedSet = new java.util.HashSet<>();
        for (String word : flaggedWords) {
            String normalized = normalize(word);
            List<String> tokens = new ArrayList<>();
            Matcher matcher = TOKEN_PATTERN.matcher(normalized);
            while (matcher.find()) {
                tokens.add(matcher.group());
            }
            if (tokens.isEmpty()) continue;

            normalizedSet.add(String.join(" ", tokens));
            String joined = String.join("", tokens);
            maxLength = Math.max(maxLength, joined.length());
            normalizedSet.add(joined);
        }
        this.flaggedWords = java.util.Collections.unmodifiableSet(normalizedSet);
        this.duration = duration;
        this.maxLength = maxLength;
    }

    public abstract boolean blockMessage();

    /**
     * Matches words against flagged words.
     *
     * @param candidateWords list of candidate words in order
     * @return list of matches
     */
    public List<AutoModMatch> getMatches(List<CandidateWord> candidateWords) {
        List<AutoModMatch> matches = new ArrayList<>();
        int totalCandidates = candidateWords.size();
        for (int i = 0; i < totalCandidates; i++) {
            StringBuilder sbNormalizedWithSpaces = new StringBuilder();
            StringBuilder sbNormalizedNoSpaces = new StringBuilder();

            for (int j = i; j < totalCandidates; j++) {
                CandidateWord cw = candidateWords.get(j);
                String normalized = cw.normalized();

                sbNormalizedNoSpaces.append(normalized);
                if (sbNormalizedNoSpaces.length() > maxLength) {
                    break;
                }

                if (j > i) {
                    sbNormalizedWithSpaces.append(SPACE);
                }
                sbNormalizedWithSpaces.append(normalized);

                String phraseWithSpaces = sbNormalizedWithSpaces.toString();
                if (flaggedWords.contains(phraseWithSpaces)) {
                    StringBuilder sbOriginal = new StringBuilder();
                    for (int k = i; k <= j; k++) {
                        if (k > i) {
                            sbOriginal.append(SPACE);
                        }
                        sbOriginal.append(candidateWords.get(k).original());
                    }
                    matches.add(new AutoModMatch(sbOriginal.toString(), phraseWithSpaces));
                } else if (j > i) {
                    // Try joining without spaces to catch things like "b a d" if "bad" is flagged
                    String phraseNoSpaces = sbNormalizedNoSpaces.toString();
                    if (flaggedWords.contains(phraseNoSpaces)) {
                        StringBuilder sbOriginal = new StringBuilder();
                        for (int k = i; k <= j; k++) {
                            sbOriginal.append(candidateWords.get(k).original());
                        }
                        matches.add(new AutoModMatch(sbOriginal.toString(), phraseNoSpaces));
                    }
                }
            }
        }
        return matches;
    }

    /**
     * Gets a list of candidate words based on a message.
     *
     * @param message the message to get candidates for
     * @return list of candidate words in order
     */
    public static List<CandidateWord> getCandidateWords(String message) {
        List<CandidateWord> candidates = new ArrayList<>();

        Matcher chunkMatcher = NON_WHITESPACE_PATTERN.matcher(message);
        while (chunkMatcher.find()) {
            String originalChunk = chunkMatcher.group();
            String normalizedChunk = NON_ALPHANUMERIC.matcher(normalize(originalChunk)).replaceAll("");

            if (normalizedChunk.isBlank()) continue;

            // Try to see if this chunk contains multiple words
            Matcher tokenMatcher = TOKEN_PATTERN.matcher(originalChunk);
            List<String> subTokens = new ArrayList<>();
            while (tokenMatcher.find()) {
                subTokens.add(tokenMatcher.group());
            }

            if (subTokens.size() > 1) {
                // If it contains multi-character tokens, it's likely multiple words (e.g., "bad-word")
                boolean hasMultiCharToken = false;
                for (String token : subTokens) {
                    if (token.length() > 1) {
                        hasMultiCharToken = true;
                        break;
                    }
                }

                if (hasMultiCharToken) {
                    StringBuilder joinedSubBuilder = new StringBuilder();
                    for (String sub : subTokens) {
                        String normalizedSub = normalize(sub);
                        candidates.add(new CandidateWord(normalizedSub, sub));
                        joinedSubBuilder.append(normalizedSub);
                    }
                    // Also add the combined version if it's different from simple concatenation
                    String joinedSub = joinedSubBuilder.toString();
                    if (!normalizedChunk.equals(joinedSub)) {
                        candidates.add(new CandidateWord(normalizedChunk, originalChunk));
                    }
                } else {
                    // All single letters, likely a single word broken up (e.g., "b.a.d")
                    candidates.add(new CandidateWord(normalizedChunk, originalChunk));
                }
            } else {
                // Single token or no sub-tokens (punctuation only)
                candidates.add(new CandidateWord(normalizedChunk, originalChunk));
            }
        }

        return candidates;
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

    @Override
    public String toString() {
        return String.format("AutoModRule: %s, id: %s", getClass().getSimpleName(), id);
    }
}
