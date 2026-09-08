package fr.ambigovee.tv;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Ecran principal TV : peu de boutons, navigation D-pad explicite et aucune
 * option technique inutile. La luminosite est geree automatiquement par le moteur.
 */
public class MainActivity extends Activity {
    private static final int ID_AUTO = 1001;
    private static final int ID_BASE = 1002;
    private static final int ID_DIRECT = 1003;
    private static final int ID_CINEMA = 1004;
    private static final int ID_DOUX = 1005;
    private static final int ID_DEVICES = 1006;
    private static final int ID_UPDATE = 1007;

    private TextView status, tvChip, goveeChip, profileLabel, colorLabel;
    private View colorPreview;
    private TvButton autoButton, baseButton, directButton, cinemaButton, douxButton, devicesButton, updateButton;
    private BroadcastReceiver statusReceiver;

    @Override protected void onResume() {
        super.onResume();
        UpdateManager.onActivityResumed(this);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigStore.seedDefaults(this);
        boolean configured = ConfigStore.isConfigured(this);
        if (configured && !ConfigStore.setupCompleted(this)) ConfigStore.markSetupCompleted(this);
        if (!configured && !ConfigStore.setupCompleted(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        setContentView(buildUi());
        registerStatusReceiver();
        startAmbiService(null);
        UpdateManager.checkOnLaunch(this);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(52), dp(24), dp(52), dp(24));
        root.setBackground(pageBackground());

        LinearLayout header = row(Gravity.CENTER_VERTICAL);
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(text("AmbiGovee", 34, Color.WHITE, true));
        brand.addView(text("Ton Ambilight. Toute ta piece.", 15, 0xff9fa8bd, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(62), 1));
        TextView version = text("v" + BuildConfig.VERSION_NAME + "  •  Android TV", 14, 0xff8f98ad, true);
        version.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(version, new LinearLayout.LayoutParams(dp(240), dp(62)));
        root.addView(header);

        LinearLayout chips = row(Gravity.CENTER_VERTICAL);
        tvChip = chip("●  PHILIPS  CONNEXION…");
        goveeChip = chip("●  GOVEE  CONNEXION…");
        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        chipParams.setMargins(dp(5), dp(8), dp(5), 0);
        chips.addView(tvChip, chipParams);
        chips.addView(goveeChip, chipParams);
        root.addView(chips);

        LinearLayout hero = card();
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(24), dp(16), dp(24), dp(16));
        LinearLayout.LayoutParams heroP = new LinearLayout.LayoutParams(-1, dp(110));
        heroP.setMargins(0, dp(12), 0, 0);
        root.addView(hero, heroP);

        colorPreview = new View(this);
        colorPreview.setBackground(roundColor(0xff30284a, 24));
        LinearLayout.LayoutParams previewP = new LinearLayout.LayoutParams(dp(72), dp(72));
        previewP.setMargins(0, 0, dp(20), 0);
        hero.addView(colorPreview, previewP);

        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        status = text("AmbiGovee demarre…", 24, Color.WHITE, true);
        colorLabel = text("En attente du flux Ambilight", 14, 0xff9fa8bd, false);
        heroText.addView(status);
        heroText.addView(colorLabel);
        hero.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1));

        profileLabel = text(profileName(ConfigStore.profile(this)), 15, 0xffc7bbff, true);
        profileLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        hero.addView(profileLabel, new LinearLayout.LayoutParams(dp(230), dp(54)));

        LinearLayout actions = row(Gravity.CENTER);
        autoButton = tvButton("⚡  SYNCHRO AUTOMATIQUE", 0xff35255f, 0xff7655ff, ID_AUTO);
        baseButton = tvButton("☀  LUMIERE NORMALE", 0xff171d2a, 0xff4c5a78, ID_BASE);
        autoButton.setOnClickListener(v -> startAmbiService(AmbiService.ACTION_AUTO));
        baseButton.setOnClickListener(v -> startAmbiService(AmbiService.ACTION_BASE));
        LinearLayout.LayoutParams actionP = new LinearLayout.LayoutParams(0, dp(62), 1);
        actionP.setMargins(dp(6), dp(12), dp(6), 0);
        actions.addView(autoButton, actionP);
        actions.addView(baseButton, actionP);
        root.addView(actions);

        LinearLayout modeCard = card();
        modeCard.setOrientation(LinearLayout.HORIZONTAL);
        modeCard.setGravity(Gravity.CENTER_VERTICAL);
        modeCard.setPadding(dp(18), dp(8), dp(18), dp(8));
        LinearLayout.LayoutParams modeCardP = new LinearLayout.LayoutParams(-1, dp(72));
        modeCardP.setMargins(0, dp(12), 0, 0);
        root.addView(modeCard, modeCardP);

        TextView modeTitle = text("REACTION", 13, 0xff7f899f, true);
        modeTitle.setGravity(Gravity.CENTER_VERTICAL);
        modeCard.addView(modeTitle, new LinearLayout.LayoutParams(dp(112), dp(54)));
        directButton = tvButton("DIRECT", 0xff171d2a, 0xff7655ff, ID_DIRECT);
        cinemaButton = tvButton("CINEMA", 0xff171d2a, 0xff7655ff, ID_CINEMA);
        douxButton = tvButton("DOUX", 0xff171d2a, 0xff7655ff, ID_DOUX);
        directButton.setOnClickListener(v -> setProfile(ConfigStore.PROFILE_DIRECT));
        cinemaButton.setOnClickListener(v -> setProfile(ConfigStore.PROFILE_CINEMA));
        douxButton.setOnClickListener(v -> setProfile(ConfigStore.PROFILE_DOUX));
        LinearLayout.LayoutParams profileP = new LinearLayout.LayoutParams(0, dp(54), 1);
        profileP.setMargins(dp(5), 0, dp(5), 0);
        modeCard.addView(directButton, profileP);
        modeCard.addView(cinemaButton, profileP);
        modeCard.addView(douxButton, profileP);

        LinearLayout bottom = row(Gravity.CENTER);
        devicesButton = tvButton("⌁  GERER LES APPAREILS", 0xff171d2a, 0xff4c5a78, ID_DEVICES);
        updateButton = tvButton("↻  VERIFIER LES MISES A JOUR", 0xff171d2a, 0xff4c5a78, ID_UPDATE);
        devicesButton.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)
                .putExtra("edit", true).putExtra("start_step", 2)));
        updateButton.setOnClickListener(v -> UpdateManager.checkNow(this));
        LinearLayout.LayoutParams bottomP = new LinearLayout.LayoutParams(0, dp(58), 1);
        bottomP.setMargins(dp(6), dp(12), dp(6), 0);
        bottom.addView(devicesButton, bottomP);
        bottom.addView(updateButton, bottomP);
        root.addView(bottom);

        TextView note = text("Le rendu s'adapte automatiquement a l'Ambilight. Les appareils deconnectes sont ignores.", 13, 0xff818ba0, false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteP = new LinearLayout.LayoutParams(-1, dp(36));
        noteP.setMargins(0, dp(6), 0, 0);
        root.addView(note, noteP);

        wireFocus();
        refreshProfileButtons();
        return root;
    }

    private void wireFocus() {
        autoButton.setNextFocusRightId(ID_BASE);
        autoButton.setNextFocusDownId(ID_DIRECT);
        baseButton.setNextFocusLeftId(ID_AUTO);
        baseButton.setNextFocusDownId(ID_DOUX);

        directButton.setNextFocusUpId(ID_AUTO);
        directButton.setNextFocusRightId(ID_CINEMA);
        directButton.setNextFocusDownId(ID_DEVICES);
        cinemaButton.setNextFocusUpId(ID_AUTO);
        cinemaButton.setNextFocusLeftId(ID_DIRECT);
        cinemaButton.setNextFocusRightId(ID_DOUX);
        cinemaButton.setNextFocusDownId(ID_DEVICES);
        douxButton.setNextFocusUpId(ID_BASE);
        douxButton.setNextFocusLeftId(ID_CINEMA);
        douxButton.setNextFocusDownId(ID_UPDATE);

        devicesButton.setNextFocusUpId(ID_DIRECT);
        devicesButton.setNextFocusRightId(ID_UPDATE);
        updateButton.setNextFocusUpId(ID_DOUX);
        updateButton.setNextFocusLeftId(ID_DEVICES);
    }

    private void setProfile(String profile) {
        ConfigStore.setProfile(this, profile);
        profileLabel.setText(profileName(profile));
        refreshProfileButtons();
        startAmbiService(AmbiService.ACTION_RELOAD);
    }

    private void refreshProfileButtons() {
        if (directButton == null) return;
        String p = ConfigStore.profile(this);
        directButton.setSelectedStyle(ConfigStore.PROFILE_DIRECT.equals(p));
        cinemaButton.setSelectedStyle(ConfigStore.PROFILE_CINEMA.equals(p));
        douxButton.setSelectedStyle(ConfigStore.PROFILE_DOUX.equals(p));
    }

    private LinearLayout row(int gravity) {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(gravity);
        return v;
    }

    private LinearLayout card() {
        LinearLayout v = new LinearLayout(this);
        v.setBackground(roundGradient(0xff151a27, 0xff101521, 20, 0xff293144));
        return v;
    }

    private TvButton tvButton(String label, int normal, int focused, int id) {
        TvButton b = new TvButton(this);
        b.setId(id);
        b.setText(label);
        b.colors(normal, focused);
        return b;
    }

    private TextView chip(String label) {
        TextView t = text(label, 13, 0xffa2aabe, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(roundGradient(0xff151a27, 0xff111621, 16, 0xff293144));
        return t;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable pageBackground() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xff070a10, 0xff0d1019, 0xff170f25});
    }

    private GradientDrawable roundGradient(int a, int b, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a, b});
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private GradientDrawable roundColor(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(2), 0xff4a425e);
        return g;
    }

    private String profileName(String id) {
        if (ConfigStore.PROFILE_CINEMA.equals(id)) return "Cinema  •  fluide";
        if (ConfigStore.PROFILE_DOUX.equals(id)) return "Doux  •  ambiance";
        return "Direct  •  reactif";
    }

    private void startAmbiService(String action) {
        Intent i = new Intent(this, AmbiService.class);
        if (action != null) i.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i); else startService(i);
    }

    private void registerStatusReceiver() {
        statusReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                String s = i.getStringExtra(AmbiService.EXTRA_STATUS);
                if (s != null) status.setText(s);
                boolean tv = i.getBooleanExtra(AmbiService.EXTRA_TV, true);
                boolean g = i.getBooleanExtra(AmbiService.EXTRA_GOVEE, false);
                boolean sync = i.getBooleanExtra(AmbiService.EXTRA_SYNC, false);
                boolean philipsPaired = !ConfigStore.tvUser(MainActivity.this).isEmpty() && !ConfigStore.tvKey(MainActivity.this).isEmpty();
                if (!philipsPaired) {
                    tvChip.setText("○  PHILIPS  DÉCONNECTÉE");
                    tvChip.setTextColor(0xff9099ad);
                } else {
                    tvChip.setText(tv ? "●  PHILIPS  ACTIVE" : "●  PHILIPS  VEILLE");
                    tvChip.setTextColor(tv ? 0xff76e2ad : 0xff9099ad);
                }

                int activeConfigured = ConfigStore.enabledGoveeCount(MainActivity.this);
                int savedConfigured = ConfigStore.goveeLights(MainActivity.this).size();
                int on = i.getIntExtra(AmbiService.EXTRA_LIGHTS_ON, 0);
                int inSync = i.getIntExtra(AmbiService.EXTRA_LIGHTS_SYNC, 0);
                if (inSync > 0) goveeChip.setText("●  GOVEE  " + inSync + "/" + activeConfigured + " EN SYNC");
                else if (on > 0) goveeChip.setText("●  GOVEE  " + on + "/" + activeConfigured + " ALLUME" + (on > 1 ? "S" : ""));
                else if (activeConfigured == 0 && savedConfigured > 0) goveeChip.setText("●  GOVEE  TOUS DECONNECTES");
                else goveeChip.setText("●  GOVEE  " + activeConfigured + " CONNECTE" + (activeConfigured > 1 ? "S" : "") + " • EN ATTENTE");
                goveeChip.setTextColor(inSync > 0 ? 0xff76e2ad : (g ? 0xffffc36b : 0xff9099ad));
                status.setTextColor(sync ? 0xffd0c5ff : Color.WHITE);

                int r = i.getIntExtra(AmbiService.EXTRA_R, -1);
                int gg = i.getIntExtra(AmbiService.EXTRA_G, -1);
                int b = i.getIntExtra(AmbiService.EXTRA_B, -1);
                if (r >= 0 && gg >= 0 && b >= 0) {
                    colorPreview.setBackground(roundColor(Color.rgb(r, gg, b), 24));
                    colorLabel.setText(sync ? "Couleur Ambilight en direct" : "Derniere couleur Ambilight");
                }
            }
        };
        IntentFilter f = new IntentFilter(AmbiService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(statusReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(statusReceiver, f);
    }

    @Override protected void onResume() {
        super.onResume();
        if (autoButton != null) {
            refreshProfileButtons();
            autoButton.postDelayed(() -> {
                View current = getCurrentFocus();
                if (current == null) autoButton.requestFocus();
            }, 120);
        }
    }

    @Override protected void onDestroy() {
        try { if (statusReceiver != null) unregisterReceiver(statusReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
}
