package org.btuk.proxy.core.config;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Config {

    private final ConfigurationFile config;

    public Config(File dataFolder) throws IOException {
        config = new YamlConfigurationFile(getClass().getClassLoader().getResourceAsStream("proxy-config.yml"), new File(dataFolder, "proxy-config.yml"));
    }

    public String getString(String path) {
        return config.getString(path);
    }

    public int getInt(String path) {
        return config.getInt(path);
    }

    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    public List<Long> getLongArray(String path) {
        return config.getLongArray(path);
    }

    public long getLong(String path) {
        return config.getLong(path);
    }

    public List<ConfigSocket> getSockets(String path) {
        return config.getSockets(path);
    }
}
