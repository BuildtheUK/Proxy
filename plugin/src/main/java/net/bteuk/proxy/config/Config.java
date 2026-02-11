package net.bteuk.proxy.config;

import net.bteuk.proxy.Proxy;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Config {

    private final ConfigurationFile config = new YamlConfigurationFile(getClass().getClassLoader().getResourceAsStream("config.yml"),
            new File(Proxy.getInstance().getDataFolder(), "config.yml"));

    public Config() throws IOException {
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
