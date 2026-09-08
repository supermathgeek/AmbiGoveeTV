package fr.ambigovee.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
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

/** Vérifie GitHub Releases au lancement et propose l'installation de l'APK. */
final class UpdateManager {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    static final class Release {
        final String tag, name, apkUrl, pageUrl;
        Release(String tag, String name, String apkUrl, String pageUrl) {
            this.tag=tag; this.name=name; this.apkUrl=apkUrl; this.pageUrl=pageUrl;
        }
    }

    static void checkOnLaunch(Activity activity) {
        String repo = BuildConfig.UPDATE_REPO == null ? "" : BuildConfig.UPDATE_REPO.trim();
        if (repo.isEmpty() || !repo.contains("/")) return;
        EXECUTOR.execute(() -> {
            try {
                Release release = latest(repo);
                if (release == null || !isNewer(release.tag, BuildConfig.VERSION_NAME)) return;
                activity.runOnUiThread(() -> showUpdate(activity, release));
            } catch (Exception ignored) {
                // Pas de pop-up d'erreur : une panne Internet ne doit jamais gêner AmbiGovee.
            }
        });
    }

    private static Release latest(String repo) throws Exception {
        URL url = new URL("https://api.github.com/repos/" + repo + "/releases/latest");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(2500); c.setReadTimeout(3000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "AmbiGovee-TV/" + BuildConfig.VERSION_NAME);
        int code = c.getResponseCode();
        if (code != 200) { c.disconnect(); return null; }
        String json = readAll(c.getInputStream()); c.disconnect();
        JSONObject o = new JSONObject(json);
        String tag=o.optString("tag_name","");
        String name=o.optString("name",tag);
        String page=o.optString("html_url","");
        String apk="";
        JSONArray assets=o.optJSONArray("assets");
        if(assets!=null){
            for(int i=0;i<assets.length();i++){
                JSONObject a=assets.optJSONObject(i); if(a==null)continue;
                String n=a.optString("name","").toLowerCase();
                if(n.endsWith(".apk")&&!n.contains("debug")){apk=a.optString("browser_download_url","");break;}
            }
        }
        if(tag.isEmpty())return null;
        return new Release(tag,name,apk,page);
    }

    private static void showUpdate(Activity activity, Release r) {
        if (activity.isFinishing()) return;
        String label = r.name == null || r.name.trim().isEmpty() ? r.tag : r.name;
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle("Mise à jour AmbiGovee")
                .setMessage("Une nouvelle version est disponible : " + label + "\n\nLa configuration Philips et tes lampes seront conservées.")
                .setNegativeButton("PLUS TARD", null);
        if (!r.apkUrl.isEmpty()) {
            b.setPositiveButton("METTRE À JOUR", (d,w) -> beginInstall(activity,r));
        } else if (!r.pageUrl.isEmpty()) {
            b.setPositiveButton("VOIR LA RELEASE", (d,w) -> activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.pageUrl))));
        }
        b.show();
    }

    private static void beginInstall(Activity activity, Release r) {
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(settings);
                Toast.makeText(activity,"Autorise AmbiGovee à installer ses mises à jour, puis relance METTRE À JOUR.",Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(activity,"Autorise les sources inconnues pour AmbiGovee dans les paramètres Android.",Toast.LENGTH_LONG).show();
            }
            return;
        }

        Toast.makeText(activity,"Téléchargement de la mise à jour…",Toast.LENGTH_SHORT).show();
        EXECUTOR.execute(() -> {
            try {
                File dir = new File(activity.getCacheDir(), "updates");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Dossier update inaccessible");
                File apk = new File(dir, "AmbiGovee-update.apk");
                download(r.apkUrl, apk);
                installApk(activity, apk);
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity,"Mise à jour impossible : " + shortMessage(e),Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void installApk(Activity activity, File apk) throws Exception {
        PackageInstaller installer = activity.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(activity.getPackageName());
        int id = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(id);
        try (InputStream in = new BufferedInputStream(new java.io.FileInputStream(apk));
             OutputStream out = session.openWrite("AmbiGovee.apk", 0, apk.length())) {
            byte[] buffer = new byte[64 * 1024]; int n;
            while ((n=in.read(buffer))>0) out.write(buffer,0,n);
            session.fsync(out);
        }
        Intent result = new Intent(activity, UpdateInstallReceiver.class);
        int pendingFlags = android.app.PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) pendingFlags |= android.app.PendingIntent.FLAG_MUTABLE;
        android.app.PendingIntent pending = android.app.PendingIntent.getBroadcast(activity, id, result, pendingFlags);
        session.commit(pending.getIntentSender());
        session.close();
    }

    private static void download(String address, File file) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setInstanceFollowRedirects(true);c.setConnectTimeout(4000);c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent","AmbiGovee-TV/"+BuildConfig.VERSION_NAME);
        int status=c.getResponseCode();
        // GitHub/CDN redirects can occasionally need a manual follow.
        if(status>=300&&status<400){String loc=c.getHeaderField("Location");c.disconnect();if(loc==null)throw new IllegalStateException("Redirection GitHub invalide");download(loc,file);return;}
        if(status<200||status>=300)throw new IllegalStateException("HTTP "+status);
        try(InputStream in=new BufferedInputStream(c.getInputStream());FileOutputStream out=new FileOutputStream(file)){
            byte[] buffer=new byte[64*1024];int n;while((n=in.read(buffer))>0)out.write(buffer,0,n);
        } finally {c.disconnect();}
        if(file.length()<10_000)throw new IllegalStateException("APK incomplet");
    }

    private static boolean isNewer(String remote,String local){
        int[] r=parse(remote),l=parse(local);for(int i=0;i<Math.max(r.length,l.length);i++){int rv=i<r.length?r[i]:0,lv=i<l.length?l[i]:0;if(rv>lv)return true;if(rv<lv)return false;}return false;
    }
    private static int[] parse(String s){String clean=s==null?"":s.replaceFirst("^[vV]","").split("[-+]",2)[0];String[] p=clean.split("\\.");int[] out=new int[p.length];for(int i=0;i<p.length;i++){try{out[i]=Integer.parseInt(p[i].replaceAll("[^0-9]",""));}catch(Exception e){out[i]=0;}}return out;}
    private static String readAll(InputStream in)throws Exception{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))>0)out.write(b,0,n);return out.toString("UTF-8");}
    private static String shortMessage(Exception e){String s=e.getMessage();return s==null||s.trim().isEmpty()?e.getClass().getSimpleName():(s.length()>80?s.substring(0,80)+"…":s);}
}
