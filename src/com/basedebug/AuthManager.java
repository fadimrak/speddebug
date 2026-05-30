/*
 * Decompiled with CFR 0.152.
 */
package com.basedebug;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Properties;
import java.util.prefs.Preferences;

public class AuthManager {
    private static final String DEFAULT_AUTH_URL = "https://YOUR-SERVER-URL-HERE/auth";
    private static final Path CONFIG_PATH = Paths.getName((String)"config", (String[])new String[]{"sped-debug-auth.properties"});
    private static final long RECHECK_INTERVAL_MS = 300000L;
    private static final String PREFS_NODE = "SpedDebugAuth";
    private static final String PREFS_KEY = "license_key";
    private static final Preferences PREFS = Preferences.userRoot().node("SpedDebugAuth");
    private static volatile boolean authPassed = false;
    private static volatile boolean authFinished = false;
    private static volatile boolean recheckStarted = false;
    private static volatile String lastFailureReason = "Auth has not completed yet.";

    public static void checkLicense() {
        Thread authThread = new Thread(() -> {
            authFinished = false;
            authPassed = false;
            AuthManager.log("Starting background license check.");
            try {
                Credentials credentials = AuthManager.loadCredentials();
                if (!credentials.complete()) {
                    AuthManager.createConfigTemplateIfMissing();
                    AuthManager.failAuth("Missing license credentials. Use your personalized jar from /download.");
                    return;
                }
                AuthManager.log("Using auth URL: " + credentials.authUrl());
                AuthResult result = AuthManager.queryServer(credentials.authUrl(), credentials.key(), AuthManager.generateHwid());
                if (result.valid) {
                    authPassed = true;
                    lastFailureReason = "";
                    AuthManager.log("LICENSE KEY SUCCESS. License is authorized and HWID is valid.");
                    AuthManager.startPeriodicRecheck();
                } else {
                    AuthManager.failAuth(AuthManager.authMessageFor(result.reason));
                }
            }
            catch (Exception e) {
                AuthManager.failAuth("Auth server unreachable or errored: " + e.getMessage());
            }
            finally {
                authFinished = true;
            }
        }, "SpedDebug-Auth");
        authThread.setDaemon(true);
        authThread.start();
    }

    public static boolean isAuthorized() {
        return authPassed;
    }

    public static boolean isAuthFinished() {
        return authFinished;
    }

    public static String getLastFailureReason() {
        return lastFailureReason;
    }

    public static void resetCredentials() {
        PREFS.remove(PREFS_KEY);
        authPassed = false;
        authFinished = false;
        lastFailureReason = "Saved license cleared. Update " + String.valueOf(CONFIG_PATH.toAbsolutePath()) + " and restart Minecraft.";
        AuthManager.log(lastFailureReason);
    }

    public static void resetUID() {
        AuthManager.resetCredentials();
    }

    private static void startPeriodicRecheck() {
        if (recheckStarted) {
            return;
        }
        recheckStarted = true;
        Thread recheckThread = new Thread(() -> {
            while (true) {
                try {
                    while (true) {
                        Thread.sleep(300000L);
                        Credentials credentials = AuthManager.loadCredentials();
                        if (!credentials.complete()) {
                            AuthManager.failAuth("Missing license credentials during recheck.");
                            continue;
                        }
                        AuthResult result = AuthManager.queryServer(credentials.authUrl(), credentials.key(), AuthManager.generateHwid());
                        if (result.valid) {
                            authPassed = true;
                            lastFailureReason = "";
                            AuthManager.log("Periodic auth recheck passed.");
                            continue;
                        }
                        AuthManager.failAuth("Periodic auth recheck failed: " + AuthManager.authMessageFor(result.reason));
                    }
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                catch (Exception e) {
                    AuthManager.failAuth("Auth recheck failed: " + e.getMessage());
                    continue;
                }
                break;
            }
        }, "SpedDebug-Auth-Recheck");
        recheckThread.setDaemon(true);
        recheckThread.start();
    }

    private static AuthResult queryServer(String authUrl, String key, String hwid) throws Exception {
        InputStream stream;
        URL url = new URL(authUrl);
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);
        conn.setDoOutput(true);
        conn.setRequestProperty("User-Agent", "SpedDebug-Auth/1.0");
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        String body = "{\"key\":\"" + AuthManager.jsonEscape(key) + "\",\"hwid\":\"" + AuthManager.jsonEscape(hwid) + "\"}";
        try (OutputStream stream2 = conn.getOutputStream();){
            stream2.write(body.getBytes(StandardCharsets.UTF_8));
        }
        int status = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        InputStream inputStream = stream = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
        if (stream != null) {
            String line;
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
        }
        conn.disconnect();
        String response = sb.toString();
        AuthManager.log("Auth HTTP " + status + " response: " + response);
        if (status != 200) {
            return new AuthResult(false, "HTTP_" + status);
        }
        if (response.contains("\"valid\":true")) {
            return new AuthResult(true, "");
        }
        String reason = AuthManager.extractReason(response);
        return new AuthResult(false, reason == null ? "INVALID_LICENSE" : reason);
    }

    private static Credentials loadCredentials() {
        Properties config = AuthManager.loadConfig();
        Properties embedded = AuthManager.loadEmbeddedConfig();
        String authUrl = AuthManager.firstNonBlank(System.getProperty("speddebug.authUrl"), System.getenv("SPED_DEBUG_AUTH_URL"), embedded.getProperty("auth_url"), config.getProperty("auth_url"), DEFAULT_AUTH_URL);
        String key = AuthManager.firstNonBlank(System.getProperty("speddebug.key"), System.getenv("SPED_DEBUG_KEY"), embedded.getProperty(PREFS_KEY), config.getProperty(PREFS_KEY), PREFS.getName(PREFS_KEY, null));
        if (key != null) {
            key = key.trim().toUpperCase(Locale.ROOT);
        }
        return new Credentials(authUrl, key);
    }

    private static Properties loadConfig() {
        Properties properties = new Properties();
        if (!Files.isRegularFile(CONFIG_PATH, new LinkOption[0])) {
            return properties;
        }
        try (InputStream stream = Files.newInputStream(CONFIG_PATH, new OpenOption[0]);){
            properties.load(stream);
        }
        catch (IOException e) {
            AuthManager.log("Could not read auth config " + String.valueOf(CONFIG_PATH.toAbsolutePath()) + ": " + e.getMessage());
        }
        return properties;
    }

    private static Properties loadEmbeddedConfig() {
        Properties properties = new Properties();
        try (InputStream stream = AuthManager.class.getResourceAsStream("/sped-debug-auth.properties");){
            if (stream != null) {
                properties.load(stream);
            }
        }
        catch (IOException e) {
            AuthManager.log("Could not read embedded auth config: " + e.getMessage());
        }
        return properties;
    }

    private static void createConfigTemplateIfMissing() {
        if (Files.exists(CONFIG_PATH, new LinkOption[0])) {
            return;
        }
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent, new FileAttribute[0]);
            }
            Properties properties = new Properties();
            properties.setProperty("auth_url", DEFAULT_AUTH_URL);
            properties.setProperty(PREFS_KEY, "");
            try (OutputStream stream = Files.newOutputStream(CONFIG_PATH, new OpenOption[0]);){
                properties.store(stream, "Sped-Debug fallback auth config. Usually you should use the personalized jar from /download.");
            }
            AuthManager.log("Created auth config template at " + String.valueOf(CONFIG_PATH.toAbsolutePath()));
        }
        catch (IOException e) {
            AuthManager.log("Could not create auth config template: " + e.getMessage());
        }
    }

    private static String generateHwid() throws Exception {
        String raw = String.join((CharSequence)"|", AuthManager.prop("os.name"), AuthManager.prop("os.arch"), AuthManager.prop("user.name"), AuthManager.prop("user.home"), AuthManager.env("COMPUTERNAME"), AuthManager.env("PROCESSOR_IDENTIFIER"), AuthManager.env("PROCESSOR_ARCHITECTURE"), AuthManager.env("NUMBER_OF_PROCESSORS"));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(String.format(Locale.ROOT, "%02x", b));
        }
        return hex.toString();
    }

    private static String jsonEscape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String extractReason(String response) {
        int key = response.indexOf("\"reason\"");
        if (key < 0) {
            return null;
        }
        int colon = response.indexOf(58, key);
        int firstQuote = response.indexOf(34, colon + 1);
        int secondQuote = response.indexOf(34, firstQuote + 1);
        if (colon < 0 || firstQuote < 0 || secondQuote < 0) {
            return null;
        }
        return response.substring(firstQuote + 1, secondQuote);
    }

    private static String authMessageFor(String reason) {
        if ("HWID_MISMATCH".equals(reason)) {
            return "HWID_MISMATCH: license is locked to another computer.";
        }
        if ("MISSING_KEY".equals(reason) || "INVALID_KEY_FORMAT".equals(reason)) {
            return reason + ": license key is missing or formatted wrong.";
        }
        if ("KEY_NOT_REDEEMED".equals(reason)) {
            return "KEY_NOT_REDEEMED: run /redeem in Discord before /download.";
        }
        if ("NOT_DOWNLOADED".equals(reason)) {
            return "NOT_DOWNLOADED: run /download in Discord before launching the addon.";
        }
        if ("EXPIRED".equals(reason)) {
            return "EXPIRED: this license has expired.";
        }
        if ("NO_ACTIVE_LICENSE".equals(reason) || "INVALID_LICENSE".equals(reason)) {
            return reason + ": no active license matches this key.";
        }
        if ("INVALID_HWID".equals(reason)) {
            return "INVALID_HWID: local HWID hash could not be validated.";
        }
        return reason;
    }

    private static void failAuth(String reason) {
        authPassed = false;
        lastFailureReason = reason;
        AuthManager.log("AUTH FAILED: " + reason);
    }

    private static String firstNonBlank(String ... values) {
        for (String value : values) {
            if (!AuthManager.isUsableSetting(value)) continue;
            return value.trim();
        }
        return null;
    }

    private static boolean isUsableSetting(String value) {
        return value != null && !value.isBlank() && !value.contains("YOUR-SERVER-URL-HERE") && !value.startsWith("YOUR_") && !value.startsWith("PASTE_");
    }

    private static String prop(String name) {
        return System.getProperty(name, "");
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value;
    }

    private static void log(String message) {
        System.out.println("[Sped-Debug] " + message);
    }

    private record AuthResult(boolean valid, String reason) {
    }

    private record Credentials(String authUrl, String key) {
        private boolean complete() {
            return this.authUrl != null && !this.authUrl.isBlank() && this.key != null && !this.key.isBlank();
        }
    }
}

