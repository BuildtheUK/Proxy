package org.btuk.proxy.core.service;

import lombok.extern.java.Log;
import org.btuk.proxy.core.user.CoreUserManager;
import org.btuk.proxy.core.user.User;
import org.btuk.proxy.database.sql.GlobalSQL;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to resolve Minecraft usernames to UUIDs.
 * Checks online cache, database, and falls back to Mojang API.
 */
@Log
public class MinecraftUserResolver {

    private static final Pattern UUID_NO_DASHES = Pattern.compile("^([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})$");
    private static final Pattern MOJANG_ID_JSON = Pattern.compile("\"id\"\\s*:\\s*\"([0-9a-fA-F]{32})\"");

    private final CoreUserManager coreUserManager;
    private final GlobalSQL globalSQL;
    private final HttpClient httpClient;

    public MinecraftUserResolver(CoreUserManager coreUserManager, GlobalSQL globalSQL) {
        this(coreUserManager, globalSQL, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public MinecraftUserResolver(CoreUserManager coreUserManager, GlobalSQL globalSQL, HttpClient httpClient) {
        this.coreUserManager = coreUserManager;
        this.globalSQL = globalSQL;
        this.httpClient = httpClient;
    }

    /**
     * Resolves a username or raw UUID string to a {@link UUID}.
     *
     * @param usernameOrUuid username or UUID string
     * @return Optional containing the resolved UUID, or empty if unresolved
     */
    public Optional<UUID> resolve(String usernameOrUuid) {
        if (usernameOrUuid == null || usernameOrUuid.isBlank()) {
            return Optional.empty();
        }

        String input = usernameOrUuid.trim();

        // 1. Direct standard UUID parse
        try {
            return Optional.of(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
        }

        // 2. Unhyphenated 32-char UUID
        Matcher unhyphenatedMatcher = UUID_NO_DASHES.matcher(input);
        if (unhyphenatedMatcher.matches()) {
            try {
                String formatted = unhyphenatedMatcher.replaceFirst("$1-$2-$3-$4-$5");
                return Optional.of(UUID.fromString(formatted));
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 3. Check CoreUserManager (online players)
        if (coreUserManager != null) {
            User user = coreUserManager.getUserByName(input);
            if (user != null && user.getUuid() != null) {
                try {
                    return Optional.of(UUID.fromString(user.getUuid()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // 4. Check GlobalSQL (player_data table)
        if (globalSQL != null) {
            String uuidStr = globalSQL.getPlayerUuidByName(input);
            if (uuidStr != null) {
                try {
                    return Optional.of(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        // 5. Query Mojang API
        return fetchFromMojang(input);
    }

    /**
     * Fetches player UUID from official Mojang API.
     */
    protected Optional<UUID> fetchFromMojang(String username) {
        if (httpClient == null) {
            return Optional.empty();
        }
        try {
            String encoded = URLEncoder.encode(username, StandardCharsets.UTF_8);
            URI uri = URI.create("https://api.mojang.com/users/profiles/minecraft/" + encoded);

            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null && !response.body().isBlank()) {
                Matcher matcher = MOJANG_ID_JSON.matcher(response.body());
                if (matcher.find()) {
                    String rawId = matcher.group(1);
                    String formatted = rawId.replaceFirst("(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})", "$1-$2-$3-$4-$5");
                    return Optional.of(UUID.fromString(formatted));
                }
            } else if (response.statusCode() == 404 || response.statusCode() == 204) {
                log.warning("Mojang API reported player not found for username: " + username);
            }
        } catch (Exception e) {
            log.warning("Failed to query Mojang API for username '" + username + "': " + e.getMessage());
        }
        return Optional.empty();
    }
}
