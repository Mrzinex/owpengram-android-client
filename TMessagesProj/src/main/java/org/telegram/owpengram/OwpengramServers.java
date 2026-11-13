package org.telegram.owpengram;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import android.text.TextUtils;

import android.graphics.Bitmap;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;

import org.telegram.messenger.AndroidUtilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class OwpengramServers {

    private static final String PREFS_NAME = "owpengram_servers";
    private static final String KEY_CUSTOM_SERVERS = "custom_servers";
    private static final String KEY_ACCOUNT_SERVER = "account_server_";
    // Built-in servers are rebuilt from compile-time constants by
    // owpengramServer()/telegramServer(), so unlike custom ones they have no
    // KEY_CUSTOM_SERVERS entry to write a refreshed name/description/logo
    // back into. This holds those cosmetic overrides, keyed by server id.
    private static final String KEY_BUILTIN_IDENTITY = "builtin_identity";

    public static final String ID_OWPENGRAM = "owpengram";
    public static final String ID_TELEGRAM  = "telegram";

    /** Public repository of the OwpenGram server, opened from the settings entry. */
    public static final String SERVER_REPO_URL = "https://dl.mobutu.ir";

    private static final CopyOnWriteArrayList<Runnable> customServersChangedListeners = new CopyOnWriteArrayList<>();

    public static void addCustomServersChangedListener(Runnable listener) {
        customServersChangedListeners.add(listener);
    }

    public static void removeCustomServersChangedListener(Runnable listener) {
        customServersChangedListeners.remove(listener);
    }

    private static final String DEFAULT_HOST = "85.133.244.9";
    private static final int    DEFAULT_PORT = 2398;

    // MobuGram's actual production server RSA key from /owpengram/server-info
    static final String OWPENGRAM_RSA_KEY =
        "-----BEGIN RSA PUBLIC KEY-----\n" +
        "MIIBCgKCAQEA0+uE59V2fbKiciXl1/ALQJKzkTUtHnQf+y7hWYFjjysHj6unqFdQ\n" +
        "Dld2HI6C64WpXO5U0bDGJfGbFhN7ak0W6c0hGhZPZqtXw7lL8XHqSKUVMkCRwJaX\n" +
        "VQGMRCREd1cJ7nDTgDs6K1RGIDP1Isi5T0Tkq90UyYIMqHolUxp5++QhrF8qim2e\n" +
        "Vzkha1TUAlweNOfBc4GUiH32mW5ge7e/iKYGwkvM36HHkV72V8bLnenJwrKeJDc3\n" +
        "2oVjhBRW2PQdArAayNN4L4guBodSNIBoUOUksDfHAz1Kr2bQR+Z+4OJhU7vqaMqo\n" +
        "KotBiCB4W5lexzrWSEvHLfAeufbbSyCIGQIDAQAB\n" +
        "-----END RSA PUBLIC KEY-----";
    // Exact 64-bit fingerprint for the key above (0x75996030ec97ec01L)
    static final long OWPENGRAM_RSA_FINGERPRINT = 0x75996030ec97ec01L;

    // Official Telegram production RSA key (restored from original Android source)
    static final String TELEGRAM_RSA_KEY =
        "-----BEGIN RSA PUBLIC KEY-----\n" +
        "MIIBCgKCAQEA6LszBcC1LGzyr992NzE0ieY+BSaOW622Aa9Bd4ZHLl+TuFQ4lo4g\n" +
        "5nKaMBwK/BIb9xUfg0Q29/2mgIR6Zr9krM7HjuIcCzFvDtr+L0GQjae9H0pRB2OO\n" +
        "62cECs5HKhT5DZ98K33vmWiLowc621dQuwKWSQKjWf50XYFw42h21P2KXUGyp2y/\n" +
        "+aEyZ+uVgLLQbRA1dEjSDZ2iGRy12Mk5gpYc397aYp438fsJoHIgJ2lgMv5h7WY9\n" +
        "t6N/byY9Nw9p21Og3AoXSL2q/2IJ1WRUhebgAdGVMlV1fkuOQoEzR7EdpqtQD9Cs\n" +
        "5+bfo3Nhmcyvk5ftB0WkJ9z6bNZ7yxrP8wIDAQAB\n" +
        "-----END RSA PUBLIC KEY-----";
    static final long TELEGRAM_RSA_FINGERPRINT = 0xd09d1d85de64fd85L;

    // --- Built-in servers ---

    public static OwpengramServer owpengramServer() {
        OwpengramServer s = new OwpengramServer();
        s.id                 = ID_OWPENGRAM;
        s.name               = "MobuGram";
        s.description        = "This is a MobuGram server.";
        s.host               = DEFAULT_HOST;
        s.port               = DEFAULT_PORT;
        s.isOfficial         = true;
        s.isTelegram         = false;
        s.multiDc            = false;
        s.mainDcId           = 2;
        s.rsaPublicKey       = OWPENGRAM_RSA_KEY;
        s.rsaKeyFingerprint  = OWPENGRAM_RSA_FINGERPRINT;
        s.logoPath           = ensureBundledLogo();
        applyBuiltinIdentity(s);
        return s;
    }

    private static String ensureBundledLogo() {
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), SERVER_LOGOS_DIR);
        File file = new File(dir, "mobu_bundled_logo.png");
        if (file.exists()) {
            return file.getAbsolutePath();
        }
        if (!dir.exists() && !dir.mkdirs()) {
            FileLog.e("OwpengramServers: failed to create " + dir);
            return null;
        }
        try (InputStream in = ApplicationLoader.applicationContext.getResources()
                .openRawResource(R.drawable.mobu_server_logo);
             FileOutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static OwpengramServer telegramServer() {
        OwpengramServer s = new OwpengramServer();
        s.id                 = ID_TELEGRAM;
        s.name               = "Telegram";
        s.description        = "Official Telegram cloud. Sign in with your Telegram account.";
        s.host               = "149.154.167.51";
        s.port               = 443;
        s.isOfficial         = true;
        s.isTelegram         = true;
        s.multiDc            = true;
        s.mainDcId           = 2;
        s.rsaPublicKey       = TELEGRAM_RSA_KEY;
        s.rsaKeyFingerprint  = TELEGRAM_RSA_FINGERPRINT;
        return s;
    }

    // --- Server list ---

    public static List<OwpengramServer> listServers() {
        List<OwpengramServer> result = new ArrayList<>();
        result.add(telegramServer());
        result.add(owpengramServer());
        result.addAll(loadCustomServers());
        return result;
    }

    public static OwpengramServer getServerById(String id) {
        if (id == null) return null;
        for (OwpengramServer s : listServers()) {
            if (id.equals(s.id)) return s;
        }
        return null;
    }

    // --- Custom server persistence ---

    public static List<OwpengramServer> loadCustomServers() {
        List<OwpengramServer> result = new ArrayList<>();
        try {
            String json = getPrefs().getString(KEY_CUSTOM_SERVERS, "[]");
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                result.add(OwpengramServer.fromJson(array.getJSONObject(i)));
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return result;
    }

    private static void saveCustomServers(List<OwpengramServer> customs) {
        try {
            JSONArray array = new JSONArray();
            for (OwpengramServer s : customs) {
                array.put(s.toJson());
            }
            getPrefs().edit().putString(KEY_CUSTOM_SERVERS, array.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            for (Runnable listener : customServersChangedListeners) {
                listener.run();
            }
        });
    }

    public static void addCustomServer(OwpengramServer server) {
        if (server.id == null || server.id.isEmpty()) {
            server.id = UUID.randomUUID().toString();
        }
        server.isOfficial = false;
        List<OwpengramServer> customs = loadCustomServers();
        customs.add(server);
        saveCustomServers(customs);
    }

    public static void updateCustomServer(OwpengramServer updated) {
        List<OwpengramServer> customs = loadCustomServers();
        for (int i = 0; i < customs.size(); i++) {
            if (updated.id.equals(customs.get(i).id)) {
                customs.set(i, updated);
                saveCustomServers(customs);
                return;
            }
        }
    }

    public static void removeCustomServer(String id) {
        List<OwpengramServer> customs = loadCustomServers();
        for (int i = 0; i < customs.size(); i++) {
            if (id.equals(customs.get(i).id)) {
                customs.remove(i);
                saveCustomServers(customs);
                return;
            }
        }
    }

    // --- Account - server binding ---

    public static String getServerIdForAccount(int accountNum) {
        return getPrefs().getString(KEY_ACCOUNT_SERVER + accountNum, null);
    }

    public static void setServerForAccount(String serverId, int accountNum) {
        getPrefs().edit().putString(KEY_ACCOUNT_SERVER + accountNum, serverId).apply();
    }

    public static OwpengramServer getServerForAccount(int accountNum) {
        return getServerById(getServerIdForAccount(accountNum));
    }

    public static boolean hasServerForAccount(int accountNum) {
        return getServerIdForAccount(accountNum) != null;
    }

    public static void clearServerForAccount(int accountNum) {
        getPrefs().edit().remove(KEY_ACCOUNT_SERVER + accountNum).apply();
    }

    public static String serverScopeKeyForAccount(int accountNum) {
        OwpengramServer s = getServerForAccount(accountNum);
        if (s == null || s.isTelegram || TextUtils.isEmpty(s.host)) {
            return "";
        }
        return s.host.toLowerCase(java.util.Locale.US);
    }

    public static boolean serverSupportsPremium(int accountNum) {
        OwpengramServer s = getServerForAccount(accountNum);
        if (s == null) {
            return false;
        }
        if (s.isTelegram) {
            return true;
        }
        MessagesController mc = MessagesController.getInstance(accountNum);
        return !mc.premiumLocked || !mc.starsLocked;
    }

    public static boolean serverIsOfficialTelegram(int accountNum) {
        OwpengramServer s = getServerForAccount(accountNum);
        return s != null && s.isTelegram;
    }

    public static boolean serverHasEmailSignup(int accountNum) {
        return MessagesController.getInstance(accountNum).emailSignupEnabled;
    }

    public static boolean sameAccountServer(int accountA, int accountB) {
        String idA = getServerIdForAccount(accountA);
        String idB = getServerIdForAccount(accountB);
        if (java.util.Objects.equals(idA, idB)) {
            return true;
        }
        OwpengramServer sa = getServerById(idA);
        OwpengramServer sb = getServerById(idB);
        if (sa == null || sb == null) {
            return false;
        }
        return sa.isTelegram == sb.isTelegram
                && sa.port == sb.port
                && sa.host != null && sa.host.equals(sb.host);
    }

    // --- DC application ---

    public static void applyServerToAccount(OwpengramServer server, int accountNum) {
        applyServerToAccount(server, accountNum, true);
    }

    public static void applyServerToAccount(OwpengramServer server, int accountNum, boolean resetKeys) {
        if (server == null) return;
        final String key;
        final long fingerprint;
        if (server.isTelegram) {
            key = TELEGRAM_RSA_KEY;
            fingerprint = TELEGRAM_RSA_FINGERPRINT;
        } else if (server.rsaPublicKey != null && !server.rsaPublicKey.isEmpty()) {
            key = server.rsaPublicKey;
            fingerprint = (server.rsaKeyFingerprint != 0) ? server.rsaKeyFingerprint : OWPENGRAM_RSA_FINGERPRINT;
        } else {
            key = OWPENGRAM_RSA_KEY;
            fingerprint = OWPENGRAM_RSA_FINGERPRINT;
        }
        int mainDc = server.mainDcId > 0
                ? server.mainDcId
                : (server.isTelegram ? 2 : 2);
        ConnectionsManager.native_applyServerConfig(
                accountNum,
                server.host,
                server.port,
                server.isTelegram,
                mainDc,
                key,
                fingerprint,
                resetKeys);
    }

    public static void applyStoredServerToAccount(int accountNum) {
        OwpengramServer server = getServerForAccount(accountNum);
        if (server != null) {
            applyServerToAccount(server, accountNum, false);
        }
    }

    // --- Helpers ---

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static List<Integer> accountsUsingServer(String serverId) {
        List<Integer> result = new ArrayList<>();
        if (TextUtils.isEmpty(serverId)) {
            return result;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()
                    && serverId.equals(getServerIdForAccount(a))) {
                result.add(a);
            }
        }
        return result;
    }

    public static boolean anyAccountIsTelegram() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                OwpengramServer s = getServerForAccount(a);
                if (s != null && s.isTelegram) return true;
            }
        }
        return false;
    }

    public static int findOwpengramAccountForHost(String host) {
        if (TextUtils.isEmpty(host)) {
            return -1;
        }
        host = host.toLowerCase();
        int selected = UserConfig.selectedAccount;
        if (accountOwnsHost(selected, host)) {
            return selected;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (a != selected && accountOwnsHost(a, host)) {
                return a;
            }
        }
        return -1;
    }

    private static boolean accountOwnsHost(int accountNum, String host) {
        if (!UserConfig.getInstance(accountNum).isClientActivated()) {
            return false;
        }
        if (serverIsOfficialTelegram(accountNum)) {
            return false;
        }
        MessagesController controller = MessagesController.getInstance(accountNum);
        String prefix = controller.linkPrefix;
        if (prefix != null && host.equalsIgnoreCase(prefix)) {
            return true;
        }
        OwpengramServer server = getServerForAccount(accountNum);
        if (server != null && server.host != null && host.equalsIgnoreCase(server.host)) {
            return true;
        }
        return false;
    }

    public static int firstTelegramAccount() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                OwpengramServer s = getServerForAccount(a);
                if (s != null && s.isTelegram) return a;
            }
        }
        return -1;
    }

    public static String serverNameForAccount(int accountNum) {
        OwpengramServer s = getServerForAccount(accountNum);
        return (s != null) ? s.name : null;
    }

    // --- Account limits (mirrors desktop: premium cap is Telegram-only) ---

    public static int firstFreeAccountSlot() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                return a;
            }
        }
        return -1;
    }

    public static int countTelegramAccounts() {
        int n = 0;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (UserConfig.getInstance(a).isClientActivated()) {
                OwpengramServer s = getServerForAccount(a);
                if (s != null && s.isTelegram) n++;
            }
        }
        return n;
    }

    private static final int TELEGRAM_FREE_LIMIT = 3;
    private static final int TELEGRAM_PREMIUM_LIMIT = 6;

    public static int telegramAccountLimit() {
        int limit = UserConfig.hasPremiumOnAccounts()
                ? TELEGRAM_PREMIUM_LIMIT
                : TELEGRAM_FREE_LIMIT;
        return Math.min(limit, UserConfig.MAX_ACCOUNT_COUNT);
    }

    public static boolean canAddTelegramAccount() {
        return countTelegramAccounts() < telegramAccountLimit();
    }

    public static class ServerInfoFetchResult {
        public final String rsaPublicKeyPem;
        public final int dcId;
        public final String name;
        public final String description;
        public final boolean hasIcon;

        public ServerInfoFetchResult(String rsaPublicKeyPem, int dcId, String name, String description, boolean hasIcon) {
            this.rsaPublicKeyPem = rsaPublicKeyPem;
            this.dcId = dcId;
            this.name = name;
            this.description = description;
            this.hasIcon = hasIcon;
        }
    }

    public static org.telegram.ui.web.HttpGetTask fetchServerInfo(String host, int port, org.telegram.messenger.Utilities.Callback<ServerInfoFetchResult> callback) {
        if (TextUtils.isEmpty(host) || port <= 0) {
            callback.run(null);
            return null;
        }
        String url = "http://" + host + ":" + port + "/owpengram/server-info";
        org.telegram.ui.web.HttpGetTask task = new org.telegram.ui.web.HttpGetTask(body -> {
            if (body == null) {
                callback.run(null);
                return;
            }
            try {
                JSONObject obj = new JSONObject(body);
                String pem = obj.optString("rsa_public_key_pem", "");
                int dcId = obj.optInt("dc_id", 0);
                String name = obj.optString("name", "");
                String description = obj.optString("description", "");
                boolean hasIcon = obj.optBoolean("has_icon", false);
                callback.run(pem.isEmpty() ? null : new ServerInfoFetchResult(pem, dcId, name, description, hasIcon));
            } catch (Exception e) {
                callback.run(null);
            }
        });
        task.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR, url);
        return task;
    }

    private static final String SERVER_LOGOS_DIR = "owpengram_server_logos";

    public static org.telegram.ui.web.HttpGetBitmapTask fetchServerIcon(String host, int port, org.telegram.messenger.Utilities.Callback<Bitmap> callback) {
        if (TextUtils.isEmpty(host) || port <= 0) {
            callback.run(null);
            return null;
        }
        String url = "http://" + host + ":" + port + "/owpengram/server-icon";
        org.telegram.ui.web.HttpGetBitmapTask task = new org.telegram.ui.web.HttpGetBitmapTask(callback::run);
        task.executeOnExecutor(android.os.AsyncTask.THREAD_POOL_EXECUTOR, url);
        return task;
    }

    public static String saveFetchedIcon(Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), SERVER_LOGOS_DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            FileLog.e("OwpengramServers: failed to create " + dir);
            return null;
        }
        File file = new File(dir, "fetched_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
        return file.getAbsolutePath();
    }

    // --- Live server identity (name / description / icon) ---

    private static boolean refreshRunning = false;
    private static int refreshRemaining = 0;

    public static void refreshServersInfo() {
        if (refreshRunning) {
            return;
        }
        List<OwpengramServer> targets = new ArrayList<>();
        for (OwpengramServer s : listServers()) {
            if (s.isTelegram || TextUtils.isEmpty(s.host) || s.port <= 0) {
                continue;
            }
            targets.add(s);
        }
        if (targets.isEmpty()) {
            return;
        }
        refreshRunning = true;
        refreshRemaining = targets.size();
        for (OwpengramServer target : targets) {
            final OwpengramServer server = target;
            fetchServerInfo(server.host, server.port, result -> {
                if (result == null) {
                    refreshFinished();
                    return;
                }
                final String name = result.name;
                final String description = result.description;
                if (!result.hasIcon) {
                    applyFetchedIdentity(server, name, description, null, false);
                    refreshFinished();
                    return;
                }
                fetchServerIcon(server.host, server.port, bitmap -> {
                    applyFetchedIdentity(
                            server,
                            name,
                            description,
                            saveFetchedIconStable(server.id, bitmap),
                            true);
                    refreshFinished();
                });
            });
        }
    }

    private static void refreshFinished() {
        if (--refreshRemaining <= 0) {
            refreshRunning = false;
        }
    }

    private static void applyFetchedIdentity(
            OwpengramServer server,
            String name,
            String description,
            String logoPath,
            boolean hasIcon) {
        String supersededLogo = null;
        boolean changed = false;

        if (server.isOfficial) {
            JSONObject stored = readBuiltinIdentityRoot().optJSONObject(server.id);
            String currentName = stored != null ? stored.optString("name", "") : "";
            String currentDescription = stored != null ? stored.optString("description", "") : "";
            String currentLogo = stored != null ? stored.optString("logoPath", "") : "";
            String nextName = currentName;
            String nextDescription = currentDescription;
            String nextLogo = currentLogo;
            if (!TextUtils.isEmpty(name) && !name.equals(currentName)) {
                nextName = name;
                changed = true;
            }
            if (!TextUtils.isEmpty(description) && !description.equals(currentDescription)) {
                nextDescription = description;
                changed = true;
            }
            if (!TextUtils.isEmpty(logoPath)) {
                if (!logoPath.equals(currentLogo)) {
                    nextLogo = logoPath;
                    changed = true;
                }
            } else if (!hasIcon && isFetchedServerLogo(server.id, currentLogo)) {
                nextLogo = "";
                changed = true;
            }
            if (!changed) {
                return;
            }
            if (!TextUtils.equals(nextLogo, currentLogo)) {
                supersededLogo = currentLogo;
            }
            writeBuiltinIdentity(server.id, nextName, nextDescription, nextLogo);
            AndroidUtilities.runOnUIThread(() -> {
                for (Runnable listener : customServersChangedListeners) {
                    listener.run();
                }
            });
        } else {
            if (!TextUtils.isEmpty(name) && !name.equals(server.name)) {
                server.name = name;
                changed = true;
            }
            if (!TextUtils.isEmpty(description) && !description.equals(server.description)) {
                server.description = description;
                changed = true;
            }
            if (!TextUtils.isEmpty(logoPath)) {
                if (!logoPath.equals(server.logoPath)) {
                    supersededLogo = server.logoPath;
                    server.logoPath = logoPath;
                    changed = true;
                }
            } else if (!hasIcon && isFetchedServerLogo(server.id, server.logoPath)) {
                supersededLogo = server.logoPath;
                server.logoPath = "";
                changed = true;
            }
            if (!changed) {
                return;
            }
            updateCustomServer(server);
        }

        if (!TextUtils.isEmpty(supersededLogo)) {
            deleteServerLogoFile(supersededLogo);
        }
    }

    private static boolean isFetchedServerLogo(String serverId, String logoPath) {
        if (TextUtils.isEmpty(serverId) || TextUtils.isEmpty(logoPath)) {
            return false;
        }
        try {
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), SERVER_LOGOS_DIR);
            File file = new File(logoPath);
            return dir.equals(file.getParentFile())
                    && file.getName().startsWith(serverId + "_");
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private static JSONObject readBuiltinIdentityRoot() {
        try {
            return new JSONObject(getPrefs().getString(KEY_BUILTIN_IDENTITY, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void writeBuiltinIdentity(
            String serverId,
            String name,
            String description,
            String logoPath) {
        try {
            JSONObject root = readBuiltinIdentityRoot();
            JSONObject object = new JSONObject();
            if (!TextUtils.isEmpty(name)) {
                object.put("name", name);
            }
            if (!TextUtils.isEmpty(description)) {
                object.put("description", description);
            }
            if (!TextUtils.isEmpty(logoPath)) {
                object.put("logoPath", logoPath);
            }
            root.put(serverId, object);
            getPrefs().edit().putString(KEY_BUILTIN_IDENTITY, root.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    static void applyBuiltinIdentity(OwpengramServer s) {
        JSONObject object = readBuiltinIdentityRoot().optJSONObject(s.id);
        if (object == null) {
            return;
        }
        String name = object.optString("name", "");
        String description = object.optString("description", "");
        String logoPath = object.optString("logoPath", "");
        if (!TextUtils.isEmpty(name)) {
            s.name = name;
        }
        if (!TextUtils.isEmpty(description)) {
            s.description = description;
        }
        if (!TextUtils.isEmpty(logoPath)) {
            s.logoPath = logoPath;
        }
    }

    private static String saveFetchedIconStable(String serverId, Bitmap bitmap) {
        if (bitmap == null) {
            return null;
        }
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer);
            byte[] bytes = buffer.toByteArray();
            byte[] hash = MessageDigest.getInstance("MD5").digest(bytes);
            final char[] hex = "0123456789abcdef".toCharArray();
            StringBuilder digest = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                int value = hash[i] & 0xFF;
                digest.append(hex[value >>> 4]).append(hex[value & 0x0F]);
            }
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), SERVER_LOGOS_DIR);
            if (!dir.exists() && !dir.mkdirs()) {
                FileLog.e("OwpengramServers: failed to create " + dir);
                return null;
            }
            File file = new File(dir, serverId + "_" + digest + ".png");
            if (!file.exists()) {
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(bytes);
                }
            }
            return file.getAbsolutePath();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void deleteServerLogoFile(String path) {
        if (TextUtils.isEmpty(path)) {
            return;
        }
        try {
            File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), SERVER_LOGOS_DIR);
            File file = new File(path);
            if (dir.equals(file.getParentFile()) && file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
