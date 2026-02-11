package net.bteuk.proxy.config;

import com.google.common.base.Preconditions;
import net.bteuk.proxy.Proxy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class ConfigurationFile {

    protected Map<String, Object> values;
    protected final File file;

    protected  ConfigurationFile(InputStream source, File destination) throws IOException {
        Preconditions.checkNotNull(destination, "destination");
        this.file = destination;
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            Files.createDirectories(file.getParentFile().toPath());
        }
        if (!file.exists() && source == null) {
            throw new IllegalStateException("File does not exist and source is null.");
        }
        if (file.createNewFile()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))){
                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line + "\n");
                }
            }
        }
    }

    public abstract void save();

    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values;
    }

    public Object getObject(String path, Object defaultValue) {
        Object value = values;
        for (String section : path.contains(".") ? path.split("\\.") : new String[] {path}) {
            if (!(value instanceof Map)) {
                if (defaultValue != null) set(path, defaultValue);
                return defaultValue;
            }
            value = getIgnoreCase((Map<Object, Object>) value, section);
        }
        if (value == null && defaultValue != null) {
            Proxy.getInstance().getLogger().warn("Inserting missing config option \"" + path + "\" with value \"" + defaultValue + "\" into config.yml");
            set(path, defaultValue);
            return defaultValue;
        }
        return value;
    }

    public Object getObject(String path) {
        return getObject(path, null);
    }

    public Object getObject(String[] path){
        Object value = values;
        for (String section : path) {
            if (!(value instanceof Map)) {
                return null;
            }
            value = getIgnoreCase((Map<Object, Object>) value, section);
        }
        return value;
    }

    private Object getIgnoreCase(Map<Object, Object> map, String key) {
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            if (entry.getKey().toString().equalsIgnoreCase(key)) return entry.getValue();
        }
        return map.get(key);
    }

    public String getString(String path) {
        return getString(path, null);
    }

    public String getString(String path, String defaultValue) {
        Object value = getObject(path, defaultValue);
        if (value == null) return defaultValue;
        return String.valueOf(value);
    }

    public int getInt(String path) {
        return getInt(path, 0);
    }

    public int getInt(String path, int defaultValue) {
        Object value = getObject(path, defaultValue);
        if (value == null) return defaultValue;
        return (int) value;
    }

    public boolean getBoolean(String path) {
        return getBoolean(path, false);
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        Object value = getObject(path, defaultValue);
        if (value == null) return defaultValue;
        return (boolean) value;
    }

    public List<Long> getLongArray(String path) {
        return getLongArray(path, null);
    }

    public List<Long> getLongArray(String path, List<Long> defaultValue) {
        Object value = getObject(path, defaultValue);
        if (value instanceof List<?> list) {
            List<Long> newList = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Long) {
                    newList.add((Long) item);
                }
            }
            return newList;
        } else {
            return defaultValue;
        }
    }

    public long getLong(String path) {
        return getLong(path, 0L);
    }

    public long getLong(String path, long defaultValue) {
        Object value = getObject(path, defaultValue);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return defaultValue;
    }

    public abstract List<ConfigSocket> getSockets(String path);

    public void set(String path, Object value) {
        set(values, path, value);
        save();
    }

    private Map<String, Object> set(Map<String, Object> map, String path, Object value) {
        if (path.contains(".")) {
            String keyWord = getRealKey(map, path.split("\\.")[0]);
            Object subMap = map.get(keyWord);
            if (!(subMap instanceof Map)) {
                subMap = new LinkedHashMap<>();
            }
            map.put(keyWord, set((Map<String, Object>) subMap, path.substring(keyWord.length()+1), value));
        } else {
            if (value == null) {
                map.remove(getRealKey(map,path));
            } else {
                map.put(path, value);
            }
        }
        return map;
    }

    private String getRealKey(Map<?, ?> map, String key) {
        for (Object mapKey : map.keySet()) {
            if (mapKey.toString().equalsIgnoreCase(key)) return mapKey.toString();
        }
        return key;
    }

    public File getFile() {
        return file;
    }
}
