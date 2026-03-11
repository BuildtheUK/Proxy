package org.btuk.proxy.core.chat.automod;

public enum AutoModFlagRuleType {
    MUTE,
    FLAG;

    private final String type;

    AutoModFlagRuleType() {
        this.type = name().toLowerCase();
    }

    public boolean isType(String type) {
        return this.type.equals(type);
    }
}
