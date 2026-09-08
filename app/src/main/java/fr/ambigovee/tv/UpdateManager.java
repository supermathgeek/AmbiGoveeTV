package fr.ambigovee.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Auto-update GitHub Releases.
 *
 * Flux :
 * 1) Au lancement, vérifie /releases/latest sur le dépôt configuré.
 * 2) Si une version plus récente existe, télécharge automatiquement AmbiGoveeTV.apk.
 * 3) Vérifie que l'APK téléchargé est bien fr.ambigovee.tv et qu'il est plus récent.
 * 4) Demande une seule fois l'autorisation "installer des applis inconnues" si nécessaire.
 * 5) Lance l'installation Android. Android peut toujours demander une confirmation utilisateur :
 *    une application normale ne peut pas contourner cette protection.
 *
 * Toutes les versions doivent conserver la même signature Android.
 */
final class UpdateManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean(false);
    private static final String PREFS = "ambigovee_updates";
    private static final String KEY_PENDING_PATH = "pending_apk";
    private static final String KEY_PENDING_LABEL = "pending_label";
    private static final String EXPECTED_APK = "AmbiGoveeTV.apk";

    static final class Release {
        final String tag, name, apkUrl, pageUrl;
        Release(String tag, String name, String apkUrl, String pageUrl) {
            this.tag = tag;
            this.name = name;
            this.apkUrl = apkUrl;
            this.pageUrl = pageUrl;
        }
    }

    /** Vérification automatique à chaque lancement de l'app. */
    static void checkOnLaunch(Activity activity) {
        // S'il y a déjà une APK téléchargée, tente de reprendre l'installation.
        if (hasPending(activity)) {
            resumePendingInstall(activity, false);
            return;
        }
        check(activity, false, true);
    }

    /** Bouton manuel de l'interface. */
    static void checkNow(Activity activity) {
        if (hasPending(activity)) {
            resumePendingInstall(activity, true);
            return;
        }
        Toast.makeText(activity, "Recherche d'une mise à jour…", Toast.LENGTH_SHORT).show();
        check(activity, true, false);
    }

    /** À appeler dans onResume(), notamment après le retour de l'écran d'autorisation Android. */
    static void onActivityResumed(Activity activity) {
        if (!hasPending(activity)) return;
        if (Build.VERSION.SDK_INT < 26 || activity.getPackageManager().canRequestPackageInstalls()) {
            resumePendingInstall(activity, false);
        }
    }

    private static void check(Activity activity, boolean showUpToDate, boolean autoDownload) {
        String repo = BuildConfig.UPDATE_REPO == null ? "" : BuildConfig.UPDATE_REPO.trim();
        if (repo.isEmpty() || !repo.contains("/")) {
            if (showUpToDate) {
                Toast.makeText(activity, "Dépôt de mise à jour non configuré.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (!BUSY.compareAndSet(false, true)) return;

        EXECUTOR.execute(() -> {
            try {
                Release release = latest(repo);
                boolean newer = release != null && isNewer(release.tag, BuildConfig.VERSION_NAME);
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    if (newer) {
                        if (autoDownload && release != null && !release.apkUrl.isEmpty()) {
                            Toast.makeText(activity,
                                    "Nouvelle version " + cleanLabel(release) + " — téléchargement…",
                                    Toast.LENGTH_LONG).show();
                            downloadAndPrepare(activity, release);
                        } else {
                            showUpdate(activity, release);
                        }
                    } else if (showUpToDate) {
                        Toast.makeText(activity,
                                "AmbiGovee v" + BuildConfig.VERSION_NAME + " est à jour ✓",
                                Toast.LENGTH_LONG).show();
                    }
                });
            } catch (Exception e) {
                if (showUpToDate) {
                    activity.runOnUiThread(() -> Toast.makeText(activity,
                            "Vérification impossible : " + shortMessage(e),
                            Toast.LENGTH_LONG).show());
                }
            } finally {
                BUSY.set(false);
            }
        });
    }

    private static Release latest(String repo) throws Exception {
        URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(3000);
        c.setReadTimeout(5000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "AmbiGovee-TV/" + BuildConfig.VERSION_NAME);

        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            return null;
        }

        String json = readAll(c.getInputStream());
        c.disconnect();
        JSONObject o = new JSONObject(json);
        String tag = o.optString("tag_name", "");
        String name = o.optString("name", tag);
        String page = o.optString("html_url", "");
        String apk = "";
        String fallbackApk = "";

        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String assetName = a.optString("name", "");
                String lower = assetName.toLowerCase();
                String urlValue = a.optString("browser_download_url", "");

                // Nom stable recommandé pour toutes les releases.
                if (EXPECTED_APK.equalsIgnoreCase(assetName)) {
                    apk = urlValue;
                    break;
                }
                if (fallbackApk.isEmpty() && lower.endsWith(".apk") && !lower.contains("debug")) {
                    fallbackApk = urlValue;
                }
            }
        }

        if (apk.isEmpty()) apk = fallbackApk;
        if (tag.isEmpty()) return null;
        return new Release(tag, name, apk, page);
    }

    private static void showUpdate(Activity activity, Release r) {
        if (activity.isFinishing() || r == null) return;
        String label = cleanLabel(r);
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle("Mise à jour AmbiGovee")
                .setMessage("Une nouvelle version est disponible : " + label
                        + "\n\nTes appareils et l'association Philips seront conservés.")
                .setNegativeButton("PLUS TARD", null);

        if (!r.apkUrl.isEmpty()) {
            b.setPositiveButton("METTRE À JOUR", (d, w) -> downloadAndPrepare(activity, r));
        } else if (!r.pageUrl.isEmpty()) {
            b.setPositiveButton("VOIR LA RELEASE", (d, w) ->
                    activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl))));
        }
        b.show();
    }

    private static void downloadAndPrepare(Activity activity, Release r) {
        if (r == null || r.apkUrl == null || r.apkUrl.isEmpty()) {
            Toast.makeText(activity, "Cette Release ne contient pas d'APK.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!DOWNLOADING.compareAndSet(false, true)) return;

        EXECUTOR.execute(() -> {
            try {
                File dir = new File(activity.getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException("Dossier update inaccessible");
                }
                File apk = new File(dir, EXPECTED_APK);
                if (apk.exists() && !apk.delete()) {
                    throw new IllegalStateException("Ancienne mise à jour impossible à remplacer");
                }

                download(r.apkUrl, apk);
                verifyDownloadedApk(activity, apk);
                savePending(activity, apk, cleanLabel(r));

                activity.runOnUiThread(() -> resumePendingInstall(activity, false));
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity,
                        "Mise à jour impossible : " + shortMessage(e),
                        Toast.LENGTH_LONG).show());
            } finally {
                DOWNLOADING.set(false);
            }
        });
    }

    private static void resumePendingInstall(Activity activity, boolean fromManualButton) {
        SharedPreferences p = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE);
        String path = p.getString(KEY_PENDING_PATH, "");
        String label = p.getString(KEY_PENDING_LABEL, "nouvelle version");
        if (path == null || path.isEmpty()) return;

        File apk = new File(path);
        if (!apk.isFile() || apk.length() < 10_000) {
            clearPending(activity);
            if (fromManualButton) {
                Toast.makeText(activity, "Le fichier de mise à jour n'est plus disponible.", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                new AlertDialog.Builder(activity)
                        .setTitle("Autoriser les mises à jour")
                        .setMessage("Pour mettre AmbiGovee à jour directement depuis GitHub, Android doit autoriser AmbiGovee à installer sa propre APK.\n\nCette autorisation n'est demandée qu'une seule fois.")
                        .setNegativeButton("PLUS TARD", null)
                        .setPositiveButton("AUTORISER", (d, w) -> {
                            try {
                                Intent settings = new Intent(
                                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                        Uri.parse("package:" + activity.getPackageName()));
                                activity.startActivity(settings);
                            } catch (Exception e) {
                                Toast.makeText(activity,
                                        "Autorise les sources inconnues pour AmbiGovee dans les paramètres Android.",
                                        Toast.LENGTH_LONG).show();
                            }
                        })
                        .show();
            } catch (Exception e) {
                Toast.makeText(activity,
                        "Autorise les sources inconnues pour AmbiGovee dans les paramètres Android.",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }

        try {
            Toast.makeText(activity, "Installation de " + label + "…", Toast.LENGTH_SHORT).show();
            installApk(activity, apk);
        } catch (Exception e) {
            Toast.makeText(activity,
                    "Installation impossible : " + shortMessage(e),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static void verifyDownloadedApk(Activity activity, File apk) throws Exception {
        PackageInfo info = activity.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (info == null || info.packageName == null) {
            throw new IllegalStateException("APK GitHub invalide");
        }
        if (!activity.getPackageName().equals(info.packageName)) {
            throw new SecurityException("Mauvais package : " + info.packageName);
        }

        long remoteCode;
        if (Build.VERSION.SDK_INT >= 28) remoteCode = info.getLongVersionCode();
        else remoteCode = info.versionCode;

        PackageInfo current = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        long currentCode;
        if (Build.VERSION.SDK_INT >= 28) currentCode = current.getLongVersionCode();
        else currentCode = current.versionCode;

        if (remoteCode <= currentCode) {
            throw new IllegalStateException("L'APK téléchargé n'est pas plus récent");
        }
    }

    private static void installApk(Activity activity, File apk) throws Exception {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(activity.getPackageName());
        // Android 12+ peut autoriser une app à se mettre elle-même à jour sans
        // confirmation supplémentaire si toutes les conditions système sont remplies.
        // On garde quand même le receiver STATUS_PENDING_USER_ACTION en fallback.
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        }

        int id = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(id);
        try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(apk));
             OutputStream out = session.openWrite(EXPECTED_APK, 0, apk.length())) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
            session.fsync(out);
        }

        Intent result = new Intent(activity, UpdateInstallReceiver.class);
        int pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) pendingFlags |= android.app.PendingIntent.FLAG_MUTABLE;
        android.app.PendingIntent pending = android.app.PendingIntent.getBroadcast(
                activity, id, result, pendingFlags);
        session.commit(pending.getIntentSender());
        session.close();
    }

    static void markInstallFinished(Context context, boolean success) {
        if (success) clearPending(context);
    }

    private static void savePending(Activity activity, File apk, String label) {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .edit()
                .putString(KEY_PENDING_PATH, apk.getAbsolutePath())
                .putString(KEY_PENDING_LABEL, label)
                .apply();
    }

    private static boolean hasPending(Activity activity) {
        String path = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
                .getString(KEY_PENDING_PATH, "");
        return path != null && !path.isEmpty() && new File(path).isFile();
    }

    private static void clearPending(Context activity) {
        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_PENDING_PATH)
                .remove(KEY_PENDING_LABEL)
                .apply();
    }

    private static void download(String address, File file) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(5000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "AmbiGovee-TV/" + BuildConfig.VERSION_NAME);
        int status = c.getResponseCode();

        if (status >= 300 && status < 400) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            if (loc == null) throw new IllegalStateException("Redirection GitHub invalide");
            download(loc, file);
            return;
        }
        if (status < 200 || status >= 300) {
            c.disconnect();
            throw new IllegalStateException("GitHub HTTP " + status);
        }

        try (InputStream in = new BufferedInputStream(c.getInputStream());
             FileOutputStream out = new FileOutputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        } finally {
            c.disconnect();
        }

        if (file.length() < 10_000) throw new IllegalStateException("APK incomplet");
    }

    private static String cleanLabel(Release r) {
        if (r == null) return "";
        String label = r.name == null || r.name.trim().isEmpty() ? r.tag : r.name;
        return label == null ? "" : label.trim();
    }

    private static boolean isNewer(String remote, String local) {
        int[] r = parse(remote), l = parse(local);
        for (int i = 0; i < Math.max(r.length, l.length); i++) {
            int rv = i < r.length ? r[i] : 0;
            int lv = i < l.length ? l[i] : 0;
            if (rv > lv) return true;
            if (rv < lv) return false;
        }
        return false;
    }

    private static int[] parse(String s) {
        String clean = s == null ? "" : s.replaceFirst("^[vV]", "").split("[-+]", 2)[0];
        String[] p = clean.split("\\.");
        int[] out = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            try {
                String digits = p[i].replaceAll("[^0-9]", "");
                out[i] = digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (Exception e) {
                out[i] = 0;
            }
        }
        return out;
    }

    private static String readAll(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
        return out.toString("UTF-8");
    }

    private static String shortMessage(Exception e) {
        String s = e.getMessage();
        return s == null || s.trim().isEmpty()
                ? e.getClass().getSimpleName()
                : (s.length() > 100 ? s.substring(0, 100) + "…" : s);
    }
}
