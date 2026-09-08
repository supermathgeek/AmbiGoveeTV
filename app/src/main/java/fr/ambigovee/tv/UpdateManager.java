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
 * Correctifs importants :
 * - n'installe jamais deux fois la même APK en parallèle ;
 * - supprime automatiquement une APK "pending" si elle est déjà installée ;
 * - revalide le versionCode juste avant chaque installation ;
 * - évite la boucle d'auto-réinstallation après PACKAGE_REPLACED.
 */
final class UpdateManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static final AtomicBoolean BUSY = new AtomicBoolean(false);
    private static final AtomicBoolean DOWNLOADING = new AtomicBoolean(false);
    private static final AtomicBoolean INSTALLING = new AtomicBoolean(false);

    private static final String PREFS = "ambigovee_updates";
    private static final String KEY_PENDING_PATH = "pending_apk";
    private static final String KEY_PENDING_LABEL = "pending_label";
    private static final String EXPECTED_APK = "AmbiGoveeTV.apk";

    static final class Release {
        final String tag;
        final String name;
        final String apkUrl;
        final String pageUrl;

        Release(String tag, String name, String apkUrl, String pageUrl) {
            this.tag = tag;
            this.name = name;
            this.apkUrl = apkUrl;
            this.pageUrl = pageUrl;
        }
    }

    /** Vérification automatique à chaque lancement. */
    static void checkOnLaunch(Activity activity) {
        if (hasUsablePending(activity)) {
            resumePendingInstall(activity, false);
            return;
        }
        check(activity, false, true);
    }

    /** Vérification manuelle depuis l'interface. */
    static void checkNow(Activity activity) {
        if (hasUsablePending(activity)) {
            resumePendingInstall(activity, true);
            return;
        }

        Toast.makeText(
                activity,
                "Recherche d'une mise à jour…",
                Toast.LENGTH_SHORT
        ).show();

        check(activity, true, false);
    }

    /**
     * Appelé au retour dans l'Activity, notamment après l'autorisation
     * "installer des applications inconnues".
     */
    static void onActivityResumed(Activity activity) {
        if (!hasUsablePending(activity)) return;

        if (Build.VERSION.SDK_INT < 26
                || activity.getPackageManager().canRequestPackageInstalls()) {
            resumePendingInstall(activity, false);
        }
    }

    private static void check(
            Activity activity,
            boolean showUpToDate,
            boolean autoDownload
    ) {
        String repo = BuildConfig.UPDATE_REPO == null
                ? ""
                : BuildConfig.UPDATE_REPO.trim();

        if (repo.isEmpty() || !repo.contains("/")) {
            if (showUpToDate) {
                Toast.makeText(
                        activity,
                        "Dépôt de mise à jour non configuré.",
                        Toast.LENGTH_LONG
                ).show();
            }
            return;
        }

        if (!BUSY.compareAndSet(false, true)) return;

        EXECUTOR.execute(() -> {
            try {
                Release release = latest(repo);
                boolean newer = release != null
                        && isNewer(release.tag, BuildConfig.VERSION_NAME);

                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;

                    if (newer) {
                        if (autoDownload
                                && release != null
                                && release.apkUrl != null
                                && !release.apkUrl.isEmpty()) {

                            Toast.makeText(
                                    activity,
                                    "Nouvelle version "
                                            + cleanLabel(release)
                                            + " — téléchargement…",
                                    Toast.LENGTH_LONG
                            ).show();

                            downloadAndPrepare(activity, release);
                        } else {
                            showUpdate(activity, release);
                        }
                    } else if (showUpToDate) {
                        Toast.makeText(
                                activity,
                                "AmbiGovee v"
                                        + BuildConfig.VERSION_NAME
                                        + " est à jour ✓",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
            } catch (Exception e) {
                if (showUpToDate) {
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            Toast.makeText(
                                    activity,
                                    "Vérification impossible : " + shortMessage(e),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                    });
                }
            } finally {
                BUSY.set(false);
            }
        });
    }

    private static Release latest(String repo) throws Exception {
        URL url = new URL(
                "https://api.github.com/repos/"
                        + repo
                        + "/releases/latest"
        );

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty(
                "Accept",
                "application/vnd.github+json"
        );
        connection.setRequestProperty(
                "User-Agent",
                "AmbiGovee-TV/" + BuildConfig.VERSION_NAME
        );

        int code = connection.getResponseCode();
        if (code != 200) {
            connection.disconnect();
            return null;
        }

        String json = readAll(connection.getInputStream());
        connection.disconnect();

        JSONObject object = new JSONObject(json);

        String tag = object.optString("tag_name", "");
        String name = object.optString("name", tag);
        String page = object.optString("html_url", "");

        String apk = "";
        String fallbackApk = "";

        JSONArray assets = object.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) continue;

                String assetName = asset.optString("name", "");
                String lower = assetName.toLowerCase();
                String urlValue =
                        asset.optString("browser_download_url", "");

                if (EXPECTED_APK.equalsIgnoreCase(assetName)) {
                    apk = urlValue;
                    break;
                }

                if (fallbackApk.isEmpty()
                        && lower.endsWith(".apk")
                        && !lower.contains("debug")) {
                    fallbackApk = urlValue;
                }
            }
        }

        if (apk.isEmpty()) apk = fallbackApk;
        if (tag.isEmpty()) return null;

        return new Release(tag, name, apk, page);
    }

    private static void showUpdate(Activity activity, Release release) {
        if (activity.isFinishing()
                || activity.isDestroyed()
                || release == null) {
            return;
        }

        String label = cleanLabel(release);

        AlertDialog.Builder builder =
                new AlertDialog.Builder(activity)
                        .setTitle("Mise à jour AmbiGovee")
                        .setMessage(
                                "Une nouvelle version est disponible : "
                                        + label
                                        + "\n\nTes appareils et l'association "
                                        + "Philips seront conservés."
                        )
                        .setNegativeButton("PLUS TARD", null);

        if (release.apkUrl != null && !release.apkUrl.isEmpty()) {
            builder.setPositiveButton(
                    "METTRE À JOUR",
                    (dialog, which) ->
                            downloadAndPrepare(activity, release)
            );
        } else if (release.pageUrl != null
                && !release.pageUrl.isEmpty()) {
            builder.setPositiveButton(
                    "VOIR LA RELEASE",
                    (dialog, which) -> activity.startActivity(
                            new Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(release.pageUrl)
                            )
                    )
            );
        }

        builder.show();
    }

    private static void downloadAndPrepare(
            Activity activity,
            Release release
    ) {
        if (release == null
                || release.apkUrl == null
                || release.apkUrl.isEmpty()) {

            Toast.makeText(
                    activity,
                    "Cette Release ne contient pas d'APK.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (!DOWNLOADING.compareAndSet(false, true)) return;

        EXECUTOR.execute(() -> {
            try {
                File dir = new File(
                        activity.getCacheDir(),
                        "updates"
                );

                if (!dir.exists() && !dir.mkdirs()) {
                    throw new IllegalStateException(
                            "Dossier update inaccessible"
                    );
                }

                File apk = new File(dir, EXPECTED_APK);

                if (apk.exists() && !apk.delete()) {
                    throw new IllegalStateException(
                            "Ancienne mise à jour impossible à remplacer"
                    );
                }

                download(release.apkUrl, apk);

                // Première validation après téléchargement.
                verifyDownloadedApkIsNewer(activity, apk);

                savePending(
                        activity,
                        apk,
                        cleanLabel(release)
                );

                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing()
                            && !activity.isDestroyed()) {
                        resumePendingInstall(activity, false);
                    }
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> {
                    if (!activity.isFinishing()
                            && !activity.isDestroyed()) {
                        Toast.makeText(
                                activity,
                                "Mise à jour impossible : "
                                        + shortMessage(e),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
            } finally {
                DOWNLOADING.set(false);
            }
        });
    }

    private static void resumePendingInstall(
            Activity activity,
            boolean fromManualButton
    ) {
        if (!hasUsablePending(activity)) return;

        SharedPreferences preferences =
                activity.getSharedPreferences(
                        PREFS,
                        Activity.MODE_PRIVATE
                );

        String path = preferences.getString(
                KEY_PENDING_PATH,
                ""
        );

        String label = preferences.getString(
                KEY_PENDING_LABEL,
                "nouvelle version"
        );

        if (path == null || path.isEmpty()) return;

        File apk = new File(path);

        /*
         * CRITIQUE :
         * On revalide le versionCode juste avant l'installation.
         * Après une mise à jour réussie, la même APK n'est donc jamais
         * réinstallée si le callback Android n'a pas eu le temps d'effacer
         * le pending avant que le processus soit tué/remplacé.
         */
        try {
            verifyDownloadedApkIsNewer(activity, apk);
        } catch (Exception e) {
            clearPending(activity, true);

            if (fromManualButton
                    && !activity.isFinishing()
                    && !activity.isDestroyed()) {
                Toast.makeText(
                        activity,
                        "Aucune mise à jour en attente.",
                        Toast.LENGTH_SHORT
                ).show();
            }
            return;
        }

        if (Build.VERSION.SDK_INT >= 26
                && !activity.getPackageManager()
                .canRequestPackageInstalls()) {

            try {
                new AlertDialog.Builder(activity)
                        .setTitle("Autoriser les mises à jour")
                        .setMessage(
                                "Pour mettre AmbiGovee à jour directement "
                                        + "depuis GitHub, Android doit autoriser "
                                        + "AmbiGovee à installer sa propre APK.\n\n"
                                        + "Cette autorisation n'est demandée "
                                        + "qu'une seule fois."
                        )
                        .setNegativeButton("PLUS TARD", null)
                        .setPositiveButton(
                                "AUTORISER",
                                (dialog, which) -> {
                                    try {
                                        Intent settings = new Intent(
                                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                Uri.parse(
                                                        "package:"
                                                                + activity.getPackageName()
                                                )
                                        );
                                        activity.startActivity(settings);
                                    } catch (Exception e) {
                                        Toast.makeText(
                                                activity,
                                                "Autorise les sources inconnues "
                                                        + "pour AmbiGovee dans "
                                                        + "les paramètres Android.",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                }
                        )
                        .show();
            } catch (Exception e) {
                Toast.makeText(
                        activity,
                        "Autorise les sources inconnues pour AmbiGovee "
                                + "dans les paramètres Android.",
                        Toast.LENGTH_LONG
                ).show();
            }
            return;
        }

        /*
         * Empêche onCreate + onResume (ou plusieurs Activity) de créer
         * plusieurs sessions PackageInstaller pour la même APK.
         */
        if (!INSTALLING.compareAndSet(false, true)) {
            return;
        }

        try {
            Toast.makeText(
                    activity,
                    "Installation de " + label + "…",
                    Toast.LENGTH_SHORT
            ).show();

            installApk(activity, apk);
        } catch (Exception e) {
            INSTALLING.set(false);

            Toast.makeText(
                    activity,
                    "Installation impossible : " + shortMessage(e),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    /**
     * Retourne true uniquement si le fichier pending existe ET possède
     * réellement un versionCode supérieur à celui qui est actuellement installé.
     */
    private static boolean hasUsablePending(Activity activity) {
        SharedPreferences preferences =
                activity.getSharedPreferences(
                        PREFS,
                        Activity.MODE_PRIVATE
                );

        String path = preferences.getString(
                KEY_PENDING_PATH,
                ""
        );

        if (path == null || path.isEmpty()) {
            return false;
        }

        File apk = new File(path);

        if (!apk.isFile() || apk.length() < 10_000) {
            clearPending(activity, true);
            return false;
        }

        try {
            verifyDownloadedApkIsNewer(activity, apk);
            return true;
        } catch (Exception e) {
            // Même version, ancienne version, mauvais package ou fichier cassé.
            clearPending(activity, true);
            return false;
        }
    }

    private static void verifyDownloadedApkIsNewer(
            Activity activity,
            File apk
    ) throws Exception {
        if (apk == null
                || !apk.isFile()
                || apk.length() < 10_000) {
            throw new IllegalStateException(
                    "APK de mise à jour absente"
            );
        }

        PackageInfo remote =
                activity.getPackageManager()
                        .getPackageArchiveInfo(
                                apk.getAbsolutePath(),
                                0
                        );

        if (remote == null || remote.packageName == null) {
            throw new IllegalStateException(
                    "APK GitHub invalide"
            );
        }

        if (!activity.getPackageName()
                .equals(remote.packageName)) {
            throw new SecurityException(
                    "Mauvais package : " + remote.packageName
            );
        }

        long remoteCode = Build.VERSION.SDK_INT >= 28
                ? remote.getLongVersionCode()
                : remote.versionCode;

        PackageInfo current =
                activity.getPackageManager()
                        .getPackageInfo(
                                activity.getPackageName(),
                                0
                        );

        long currentCode = Build.VERSION.SDK_INT >= 28
                ? current.getLongVersionCode()
                : current.versionCode;

        if (remoteCode <= currentCode) {
            throw new IllegalStateException(
                    "APK déjà installée ou plus ancienne"
            );
        }
    }

    private static void installApk(
            Activity activity,
            File apk
    ) throws Exception {
        PackageInstaller installer =
                activity.getPackageManager()
                        .getPackageInstaller();

        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(
                        PackageInstaller.SessionParams.MODE_FULL_INSTALL
                );

        params.setAppPackageName(
                activity.getPackageName()
        );

        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(
                    PackageInstaller.SessionParams
                            .USER_ACTION_NOT_REQUIRED
            );
        }

        int sessionId = installer.createSession(params);

        PackageInstaller.Session session =
                installer.openSession(sessionId);

        try {
            try (
                    InputStream in = new BufferedInputStream(
                            new java.io.FileInputStream(apk)
                    );
                    OutputStream out = session.openWrite(
                            EXPECTED_APK,
                            0,
                            apk.length()
                    )
            ) {
                byte[] buffer = new byte[64 * 1024];
                int count;

                while ((count = in.read(buffer)) > 0) {
                    out.write(buffer, 0, count);
                }

                session.fsync(out);
            }

            Intent result =
                    new Intent(
                            activity,
                            UpdateInstallReceiver.class
                    );

            int flags =
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT;

            if (Build.VERSION.SDK_INT >= 31) {
                flags |= android.app.PendingIntent.FLAG_MUTABLE;
            }

            android.app.PendingIntent pendingIntent =
                    android.app.PendingIntent.getBroadcast(
                            activity,
                            sessionId,
                            result,
                            flags
                    );

            session.commit(
                    pendingIntent.getIntentSender()
            );
        } finally {
            session.close();
        }
    }

    /**
     * Appelé par UpdateInstallReceiver.
     * success=true : on efface le pending.
     * success=false : on libère seulement le verrou pour permettre un nouvel essai.
     */
    static void markInstallFinished(
            Context context,
            boolean success
    ) {
        INSTALLING.set(false);

        if (success) {
            clearPending(context, true);
        }
    }

    private static void savePending(
            Activity activity,
            File apk,
            String label
    ) {
        activity.getSharedPreferences(
                        PREFS,
                        Activity.MODE_PRIVATE
                )
                .edit()
                .putString(
                        KEY_PENDING_PATH,
                        apk.getAbsolutePath()
                )
                .putString(
                        KEY_PENDING_LABEL,
                        label
                )
                .apply();
    }

    private static void clearPending(
            Context context,
            boolean deleteFile
    ) {
        SharedPreferences preferences =
                context.getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE
                );

        String oldPath =
                preferences.getString(
                        KEY_PENDING_PATH,
                        ""
                );

        preferences.edit()
                .remove(KEY_PENDING_PATH)
                .remove(KEY_PENDING_LABEL)
                .apply();

        if (deleteFile
                && oldPath != null
                && !oldPath.isEmpty()) {
            try {
                File file = new File(oldPath);
                if (file.isFile()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
            } catch (Exception ignored) {}
        }
    }

    private static void download(
            String address,
            File file
    ) throws Exception {
        HttpURLConnection connection =
                (HttpURLConnection)
                        new URL(address).openConnection();

        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty(
                "User-Agent",
                "AmbiGovee-TV/" + BuildConfig.VERSION_NAME
        );

        int status = connection.getResponseCode();

        if (status >= 300 && status < 400) {
            String location =
                    connection.getHeaderField("Location");

            connection.disconnect();

            if (location == null) {
                throw new IllegalStateException(
                        "Redirection GitHub invalide"
                );
            }

            download(location, file);
            return;
        }

        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new IllegalStateException(
                    "GitHub HTTP " + status
            );
        }

        try (
                InputStream in =
                        new BufferedInputStream(
                                connection.getInputStream()
                        );
                FileOutputStream out =
                        new FileOutputStream(file)
        ) {
            byte[] buffer =
                    new byte[64 * 1024];

            int count;

            while ((count = in.read(buffer)) > 0) {
                out.write(
                        buffer,
                        0,
                        count
                );
            }
        } finally {
            connection.disconnect();
        }

        if (file.length() < 10_000) {
            throw new IllegalStateException(
                    "APK incomplet"
            );
        }
    }

    private static String cleanLabel(
            Release release
    ) {
        if (release == null) return "";

        String label =
                release.name == null
                        || release.name.trim().isEmpty()
                        ? release.tag
                        : release.name;

        return label == null
                ? ""
                : label.trim();
    }

    private static boolean isNewer(
            String remote,
            String local
    ) {
        int[] r = parse(remote);
        int[] l = parse(local);

        for (int i = 0;
             i < Math.max(r.length, l.length);
             i++) {

            int rv = i < r.length
                    ? r[i]
                    : 0;

            int lv = i < l.length
                    ? l[i]
                    : 0;

            if (rv > lv) return true;
            if (rv < lv) return false;
        }

        return false;
    }

    private static int[] parse(String value) {
        String clean =
                value == null
                        ? ""
                        : value
                        .replaceFirst("^[vV]", "")
                        .split("[-+]", 2)[0];

        String[] parts = clean.split("\\.");

        int[] out =
                new int[parts.length];

        for (int i = 0;
             i < parts.length;
             i++) {

            try {
                String digits =
                        parts[i]
                                .replaceAll(
                                        "[^0-9]",
                                        ""
                                );

                out[i] =
                        digits.isEmpty()
                                ? 0
                                : Integer.parseInt(digits);
            } catch (Exception e) {
                out[i] = 0;
            }
        }

        return out;
    }

    private static String readAll(
            InputStream input
    ) throws Exception {
        java.io.ByteArrayOutputStream out =
                new java.io.ByteArrayOutputStream();

        byte[] buffer =
                new byte[8192];

        int count;

        while ((count = input.read(buffer)) > 0) {
            out.write(
                    buffer,
                    0,
                    count
            );
        }

        return out.toString("UTF-8");
    }

    private static String shortMessage(
            Exception e
    ) {
        String value = e.getMessage();

        if (value == null
                || value.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }

        return value.length() > 100
                ? value.substring(0, 100) + "…"
                : value;
    }
}
