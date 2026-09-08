package fr.ambigovee.tv;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

final class ConfigStore {
    private static final String PREFS = "ambigovee_config";
    private static final String KEY_LIGHTS = "govee_lights_json";

    static final String PROFILE_DIRECT = "direct";
    static final String PROFILE_CINEMA = "cinema";
    static final String PROFILE_DOUX = "doux";

    static void seedDefaults(Context context) {
        SharedPreferences p = prefs(context);
        SharedPreferences.Editor e = p.edit();
        boolean changed = false;

        // Les valeurs privées d'un build de test ne sont injectées qu'une seule fois.
        // Ainsi, "Déconnecter la Philips" reste réellement déconnecté ensuite.
        if (!p.getBoolean("build_defaults_seeded", false)) {
            if (!p.contains("tv_ip") && !BuildDefaults.TV_IP.isEmpty()) e.putString("tv_ip", BuildDefaults.TV_IP);
            if (!p.contains("tv_user") && !BuildDefaults.TV_USER.isEmpty()) e.putString("tv_user", BuildDefaults.TV_USER);
            if (!p.contains("tv_key") && !BuildDefaults.TV_KEY.isEmpty()) e.putString("tv_key", BuildDefaults.TV_KEY);
            e.putBoolean("build_defaults_seeded", true);
            changed = true;
        }
        if (!p.contains("profile")) { e.putString("profile", PROFILE_DIRECT); changed = true; }
        if (!p.contains("auto_enabled")) { e.putBoolean("auto_enabled", true); changed = true; }
        if (changed) e.apply();

        // Migration automatique de l'ancienne configuration mono-lampe.
        if (!p.contains(KEY_LIGHTS)) {
            String ip = p.getString("govee_ip", BuildDefaults.GOVEE_IP);
            String device = p.getString("govee_device", BuildDefaults.GOVEE_DEVICE);
            String sku = p.getString("govee_sku", BuildDefaults.GOVEE_SKU);
            if ((!n(ip).isEmpty() || !n(device).isEmpty() || !n(sku).isEmpty())) {
                List<GoveeConfig> one = new ArrayList<>();
                one.add(new GoveeConfig(ip, device, sku, GoveeConfig.POSITION_ROOM, "", true));
                saveGoveeLights(context, one);
            } else {
                p.edit().putString(KEY_LIGHTS, "[]").apply();
            }
        }
    }

    static boolean isConfigured(Context context) {
        seedDefaults(context);
        return !tvUser(context).isEmpty() && !tvKey(context).isEmpty() && !goveeLights(context).isEmpty();
    }

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static String tvIp(Context c) { return prefs(c).getString("tv_ip", BuildDefaults.TV_IP); }
    static String tvUser(Context c) { return prefs(c).getString("tv_user", BuildDefaults.TV_USER); }
    static String tvKey(Context c) { return prefs(c).getString("tv_key", BuildDefaults.TV_KEY); }
    static String profile(Context c) { return prefs(c).getString("profile", PROFILE_DIRECT); }
    static boolean autoEnabled(Context c) { return prefs(c).getBoolean("auto_enabled", true); }
    static boolean setupCompleted(Context c) { return prefs(c).getBoolean("setup_completed", false); }
    static void markSetupCompleted(Context c) { prefs(c).edit().putBoolean("setup_completed", true).apply(); }

    static void savePhilips(Context c, String ip, String user, String key) {
        prefs(c).edit().putString("tv_ip", n(ip)).putString("tv_user", n(user)).putString("tv_key", n(key)).apply();
    }

    static List<GoveeConfig> goveeLights(Context c) {
        seedDefaults(c);
        ArrayList<GoveeConfig> result = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(KEY_LIGHTS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                GoveeConfig g = GoveeConfig.fromJson(a.optJSONObject(i));
                if (g != null && (!g.ip.isEmpty() || !g.device.isEmpty())) result.add(g);
            }
        } catch (Exception ignored) {}
        return result;
    }

    static void saveGoveeLights(Context c, List<GoveeConfig> lights) {
        JSONArray a = new JSONArray();
        if (lights != null) {
            for (GoveeConfig g : lights) {
                if (g == null) continue;
                try { a.put(g.toJson()); } catch (Exception ignored) {}
            }
        }
        prefs(c).edit().putString(KEY_LIGHTS, a.toString()).apply();
    }

    static void upsertGovee(Context c, GoveeConfig incoming) {
        List<GoveeConfig> lights = goveeLights(c);
        boolean found = false;
        for (int i = 0; i < lights.size(); i++) {
            if (lights.get(i).identity().equals(incoming.identity())) {
                lights.set(i, incoming); found = true; break;
            }
        }
        if (!found) lights.add(incoming);
        saveGoveeLights(c, lights);
    }

    static void removeGovee(Context c, String identity) {
        List<GoveeConfig> lights = goveeLights(c);
        ArrayList<GoveeConfig> out = new ArrayList<>();
        for (GoveeConfig g : lights) if (!g.identity().equals(identity)) out.add(g);
        saveGoveeLights(c, out);
    }

    static void updateGoveeIp(Context c, String identity, String newIp) {
        List<GoveeConfig> lights = goveeLights(c);
        boolean changed = false;
        for (int i = 0; i < lights.size(); i++) {
            GoveeConfig g = lights.get(i);
            if (g.identity().equals(identity) && !g.ip.equals(newIp)) {
                lights.set(i, g.withIp(newIp)); changed = true;
            }
        }
        if (changed) saveGoveeLights(c, lights);
    }


    static int enabledGoveeCount(Context c) {
        int count = 0;
        for (GoveeConfig g : goveeLights(c)) if (g.enabled) count++;
        return count;
    }

    static void setGoveeEnabled(Context c, String identity, boolean enabled) {
        List<GoveeConfig> lights = goveeLights(c);
        for (int i = 0; i < lights.size(); i++) {
            GoveeConfig g = lights.get(i);
            if (g.identity().equals(identity)) lights.set(i, g.withEnabled(enabled));
        }
        saveGoveeLights(c, lights);
    }

    static void disconnectPhilips(Context c) {
        prefs(c).edit().remove("tv_user").remove("tv_key").remove("tv_ip").apply();
    }

    static void setProfile(Context c, String value) { prefs(c).edit().putString("profile", value).apply(); }
    static void setAutoEnabled(Context c, boolean value) { prefs(c).edit().putBoolean("auto_enabled", value).apply(); }

    static void clearPairing(Context c) {
        prefs(c).edit().remove("tv_user").remove("tv_key").remove("tv_ip").putString(KEY_LIGHTS, "[]").apply();
    }

    private static String n(String s) { return s == null ? "" : s.trim(); }
}
