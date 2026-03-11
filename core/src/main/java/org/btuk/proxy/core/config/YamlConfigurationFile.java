package org.btuk.proxy.core.config;

import lombok.extern.java.Log;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Log
public class YamlConfigurationFile extends ConfigurationFile {

    private static final Yaml YAML;

    static {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        YAML = new Yaml(options);
    }

    public static Map<String, Object> getMap(Object object) {
        String yamlDumped = YAML.dump(object);
        return YAML.load(yamlDumped);
    }

    public YamlConfigurationFile(InputStream source, File destination) throws YAMLException, IOException {
        super(source, destination);
        FileInputStream input = null;

        try {
            input = new FileInputStream(file);
            values = YAML.load(input);
            if (values == null) {
                values = new LinkedHashMap<>();
            }
            input.close();
        } catch (YAMLException e) {
            if (input != null) {
                input.close();
            }
            throw e;
        }
    }

    @Override
    public void save() {
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            YAML.dump(values, writer);
            writer.close();
        } catch (IOException e) {
            log.warning("Failed to save proxy-config.yml");
        }
    }

    public List<ConfigSocket> getSockets(String path) {
        List<ConfigSocket> sockets = new ArrayList<>();
        Object value = getObject(path, null);
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> socketValues = getMap(item);
                ConfigSocket socket = new ConfigSocket();
                socket.setServer((String) socketValues.get("server"));
                socket.setIP((String) socketValues.get("IP"));
                socket.setPort((Integer) socketValues.get("port"));
                sockets.add(socket);
            }
        }
        return sockets;
    }

    public List<Map<String, Object>> getList(String path) {
        List<Map<String, Object>> list = new ArrayList<>();
        Object object = getObject(path, null);
        if (object instanceof List<?> objectList) {
            for (Object objectValue : objectList) {
                Map<String, Object> valueMap = getMap(objectValue);
                if (valueMap != null) {
                    list.add(valueMap);
                }
            }
        }
        return list;
    }
}
