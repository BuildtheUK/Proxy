package org.btuk.proxy.core.chat.automod;

/**
 * Represents a match between a message word and a flagged word.
 *
 * @param messageWord the word in the message
 * @param flaggedWord the word that triggered the flag
 */
public record AutoModMatch(String messageWord, String flaggedWord) {
}
