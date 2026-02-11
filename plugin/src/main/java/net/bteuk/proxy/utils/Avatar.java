package net.bteuk.proxy.utils;

import net.bteuk.proxy.Proxy;

public class Avatar {

    public static String getAvatarUrl(String uuid, String texture) {
        return constructAvatarUrl(uuid, texture);
    }

    private static String constructAvatarUrl(String uuid, String texture) {

        String url = "https://crafthead.net/helm/{uuid}/{size}#{texture}";
        url = url
                .replace("{texture}", texture != null ? texture : "")
                .replace("{uuid}", uuid != null ? uuid.replace("-", "") : "")
                .replace("{size}", "128");

        return url;
    }
}
