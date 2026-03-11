package org.btuk.proxy.core.chat.automod;

import lombok.extern.java.Log;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.btuk.proxy.core.config.Config;
import org.btuk.proxy.core.config.YamlConfigurationFile;

@Log
public class AutoModConfig {

    private static final String RULES_PATH = "rules";

    private static final String FLAGGED_WORDS_PATH = "flagged_words";

    private static final String PUNISHMENTS_PATH = "punishment";

    private static final String PUNISHMENT_TYPE_PATH = "type";

    private static final String PUNISHMENT_DURATION_PATH = "duration";

    private static final String PUNISHMENT_DURATION_UNIT_PATH = "duration_unit";

    private static final String PUNISHMENT_POINTS_PATH = "points";

    private static final String PUNISHMENT_DELETE_MESSAGES_PATH = "delete_message";

    public static List<AutoModRule> loadConfig(Config config) {
        List<Map<String, Object>> rulesList = config.getList(RULES_PATH);
        if (config.getBoolean("enabled")) {
            return loadRules(rulesList);
        }
        return Collections.emptyList();
    }

    private static List<AutoModRule> loadRules(List<Map<String, Object>> rulesList) {
        return rulesList.stream().map(map -> {
            try {
                return loadRule(map);
            } catch (RuntimeException e) {
                log.warning("Failed to load rule: " + e.getMessage());
                return null;
            }
        }).filter(Objects::nonNull).toList();
    }

    private static AutoModRule loadRule(Map<String, Object> ruleMap) {
        Object flaggedWordsObject = ruleMap.get(FLAGGED_WORDS_PATH);
        Object punishmentObject = ruleMap.get(PUNISHMENTS_PATH);
        Map<String, Object> punishmentMap = YamlConfigurationFile.getMap(punishmentObject);

        if (!(flaggedWordsObject instanceof List<?> flaggedWordsList)) {
            return null;
        }

        List<String> flaggedWords = flaggedWordsList.stream().map(Object::toString).toList();

        String punishmentType = punishmentMap.get(PUNISHMENT_TYPE_PATH).toString();
        int durationTime = Integer.parseInt(punishmentMap.get(PUNISHMENT_DURATION_PATH).toString());
        String durationUnit = punishmentMap.get(PUNISHMENT_DURATION_UNIT_PATH).toString();

        AutoModFlagRuleType type = AutoModFlagRuleType.valueOf(punishmentType.toUpperCase());
        ChronoUnit timeUnit = ChronoUnit.valueOf(durationUnit.toUpperCase());
        Duration duration = Duration.of(durationTime, timeUnit);

        switch (type) {
            case MUTE -> {
                return new AutoModMuteRule(flaggedWords, duration);
            }
            case FLAG -> {
                return loadFlagRule(flaggedWords, duration, punishmentMap);
            }
            default -> {
                log.warning(String.format("Unknown punishment type: %s", punishmentType));
                return null;
            }
        }
    }

    private static AutoModFlagRule loadFlagRule(List<String> flaggedWords, Duration duration, Map<String, Object> punishmentMap) {
        int points = Integer.parseInt(punishmentMap.get(PUNISHMENT_POINTS_PATH).toString());
        boolean deleteMessages = Boolean.parseBoolean(punishmentMap.get(PUNISHMENT_DELETE_MESSAGES_PATH).toString());

        return new AutoModFlagRule(flaggedWords, points, duration, deleteMessages);
    }
}
