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
 * Écran principal simple et lisible à la télécommande.
 */
public class MainActivity extends Activity {

    private static final int ID_AUTO = 1001;
    private static final int ID_BASE = 1002;
    private static final int ID_DIRECT = 1003;
    private static final int ID_CINEMA = 1004;
    private static final int ID_DOUX = 1005;
    private static final int ID_DEVICES = 1006;
    private static final int ID_UPDATE = 1007;

    private TextView status;
    private TextView tvChip;
    private TextView goveeChip;
    private TextView profileLabel;
    private TextView colorLabel;
    private View colorPreview;

    private TvButton autoButton;
    private TvButton baseButton;
    private TvButton directButton;
    private TvButton cinemaButton;
    private TvButton douxButton;
    private TvButton devicesButton;
    private TvButton updateButton;

    private BroadcastReceiver statusReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigStore.seedDefaults(this);

        boolean configured = ConfigStore.isConfigured(this);
        if (configured && !ConfigStore.setupCompleted(this)) {
            ConfigStore.markSetupCompleted(this);
        }

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
        root.setPadding(dp(50), dp(24), dp(50), dp(24));
        root.setBackground(pageBackground());

        LinearLayout header = row(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(text("AmbiGovee", 36, Color.WHITE, true));
        brand.addView(text("Ambilight + Govee, simplement.", 15, 0xff9099ad, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(66), 1));

        TextView version = text("v" + BuildConfig.VERSION_NAME, 14, 0xff7e879b, true);
        version.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(version, new LinearLayout.LayoutParams(dp(120), dp(66)));
        root.addView(header);

        LinearLayout deviceRow = row(Gravity.CENTER_VERTICAL);
        tvChip = chip("○  PHILIPS");
        goveeChip = chip("○  GOVEE");

        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        chipParams.setMargins(dp(5), dp(8), dp(5), 0);
        deviceRow.addView(tvChip, chipParams);
        deviceRow.addView(goveeChip, chipParams);
        root.addView(deviceRow);

        LinearLayout hero = card();
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(24), dp(18), dp(24), dp(18));

        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(128));
        heroParams.setMargins(0, dp(14), 0, 0);
        root.addView(hero, heroParams);

        colorPreview = new View(this);
        colorPreview.setBackground(roundColor(0xff30284a, 24));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(dp(78), dp(78));
        previewParams.setMargins(0, 0, dp(22), 0);
        hero.addView(colorPreview, previewParams);

        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);

        status = text("Démarrage…", 27, Color.WHITE, true);
        colorLabel = text("En attente de l'Ambilight", 14, 0xff929caf, false);
        heroText.addView(status);

        LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(-1, -2);
        colorParams.setMargins(0, dp(6), 0, 0);
        heroText.addView(colorLabel, colorParams);

        hero.addView(heroText, new LinearLayout.LayoutParams(0, -2, 1));

        profileLabel = text(profileName(ConfigStore.profile(this)), 14, 0xffcbbfff, true);
        profileLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        hero.addView(profileLabel, new LinearLayout.LayoutParams(dp(210), dp(54)));

        LinearLayout syncActions = row(Gravity.CENTER);

        autoButton = tvButton("▶  ACTIVER LA SYNCHRO", 0xff35255f, 0xff7655ff, ID_AUTO);
        baseButton = tvButton("■  LUMIÈRE NORMALE", 0xff171d2a, 0xff46526b, ID_BASE);

        autoButton.setOnClickListener(v -> startAmbiService(AmbiService.ACTION_AUTO));
        baseButton.setOnClickListener(v -> startAmbiService(AmbiService.ACTION_BASE));

        LinearLayout.LayoutParams syncParams = new LinearLayout.LayoutParams(0, dp(66), 1);
        syncParams.setMargins(dp(6), dp(14), dp(6), 0);
        syncActions.addView(autoButton, syncParams);
        syncActions.addView(baseButton, syncParams);
        root.addView(syncActions);

        LinearLayout modeCard = card();
        modeCard.setOrientation(LinearLayout.HORIZONTAL);
        modeCard.setGravity(Gravity.CENTER_VERTICAL);
        modeCard.setPadding(dp(18), dp(8), dp(18), dp(8));

        LinearLayout.LayoutParams modeCardParams = new LinearLayout.LayoutParams(-1, dp(76));
        modeCardParams.setMargins(0, dp(14), 0, 0);
        root.addView(modeCard, modeCardParams);

        TextView modeTitle = text("MODE", 13, 0xff7d879b, true);
        modeTitle.setGravity(Gravity.CENTER_VERTICAL);
        modeCard.addView(modeTitle, new LinearLayout.LayoutParams(dp(90), dp(56)));

        directButton = tvButton("DIRECT", 0xff171d2a, 0xff7655ff, ID_DIRECT);
        cinemaButton = tvButton("CINÉMA", 0xff171d2a, 0xff7655ff, ID_CINEMA);
        douxButton = tvButton("DOUX", 0xff171d2a, 0xff7655ff, ID_DOUX);

        directButton.setOnClickListener(v -> setProfile(ConfigStore.PROFILE_DIRECT));
        cinemaButton.setOnClickListener(v -> setProfile(ConfigStore.PROFILE_CINEMA));
        douxButton.setOnClickListener(v -> setProfile(ConfigStore.PROFILE_DOUX));

        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(0, dp(56), 1);
        profileParams.setMargins(dp(5), 0, dp(5), 0);
        modeCard.addView(directButton, profileParams);
        modeCard.addView(cinemaButton, profileParams);
        modeCard.addView(douxButton, profileParams);

        LinearLayout bottom = row(Gravity.CENTER);

        devicesButton = tvButton("⚙  MES APPAREILS", 0xff171d2a, 0xff46526b, ID_DEVICES);
        updateButton = tvButton("↻  MISE À JOUR", 0xff171d2a, 0xff46526b, ID_UPDATE);

        devicesButton.setOnClickListener(v -> startActivity(
                new Intent(this, SetupActivity.class)
                        .putExtra("edit", true)
                        .putExtra("start_step", 1)
        ));

        updateButton.setOnClickListener(v -> UpdateManager.checkNow(this));

        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(0, dp(60), 1);
        bottomParams.setMargins(dp(6), dp(14), dp(6), 0);
        bottom.addView(devicesButton, bottomParams);
        bottom.addView(updateButton, bottomParams);
        root.addView(bottom);

        TextView note = text(
                "AmbiGovee ne rallume jamais une lumière que tu as éteinte.",
                13,
                0xff768095,
                false
        );
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, dp(34));
        noteParams.setMargins(0, dp(7), 0, 0);
        root.addView(note, noteParams);

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
        if (directButton == null || cinemaButton == null || douxButton == null) return;

        String profile = ConfigStore.profile(this);
        directButton.setSelectedStyle(ConfigStore.PROFILE_DIRECT.equals(profile));
        cinemaButton.setSelectedStyle(ConfigStore.PROFILE_CINEMA.equals(profile));
        douxButton.setSelectedStyle(ConfigStore.PROFILE_DOUX.equals(profile));
    }

    private LinearLayout row(int gravity) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(gravity);
        return view;
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setBackground(roundGradient(0xff141a26, 0xff10141f, 20, 0xff293144));
        return view;
    }

    private TvButton tvButton(String label, int normal, int focused, int id) {
        TvButton button = new TvButton(this);
        button.setId(id);
        button.setText(label);
        button.colors(normal, focused);
        return button;
    }

    private TextView chip(String label) {
        TextView view = text(label, 13, 0xff9ca6b9, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(roundGradient(0xff141a26, 0xff10141f, 15, 0xff293144));
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable pageBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xff06080d, 0xff0b0f18, 0xff160d24}
        );
    }

    private GradientDrawable roundGradient(int colorA, int colorB, int radius, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{colorA, colorB}
        );
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private GradientDrawable roundColor(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(2), 0xff4a425e);
        return drawable;
    }

    private String profileName(String id) {
        if (ConfigStore.PROFILE_CINEMA.equals(id)) return "Cinéma  •  fluide";
        if (ConfigStore.PROFILE_DOUX.equals(id)) return "Doux  •  ambiance";
        return "Direct  •  réactif";
    }

    private void startAmbiService(String action) {
        Intent intent = new Intent(this, AmbiService.class);
        if (action != null) intent.setAction(action);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
        else startService(intent);
    }

    private void registerStatusReceiver() {
        statusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String currentStatus = intent.getStringExtra(AmbiService.EXTRA_STATUS);
                if (currentStatus != null && status != null) status.setText(currentStatus);

                boolean tvActive = intent.getBooleanExtra(AmbiService.EXTRA_TV, true);
                boolean goveeReachable = intent.getBooleanExtra(AmbiService.EXTRA_GOVEE, false);
                boolean syncing = intent.getBooleanExtra(AmbiService.EXTRA_SYNC, false);

                boolean philipsPaired = !ConfigStore.tvUser(MainActivity.this).isEmpty()
                        && !ConfigStore.tvKey(MainActivity.this).isEmpty();

                if (!philipsPaired) {
                    tvChip.setText("○  PHILIPS  DÉCONNECTÉE");
                    tvChip.setTextColor(0xff9099ad);
                } else {
                    tvChip.setText(tvActive ? "●  PHILIPS  ACTIVE" : "●  PHILIPS  VEILLE");
                    tvChip.setTextColor(tvActive ? 0xff76e2ad : 0xff9099ad);
                }

                int activeConfigured = ConfigStore.enabledGoveeCount(MainActivity.this);
                int savedConfigured = ConfigStore.goveeLights(MainActivity.this).size();
                int lightsOn = intent.getIntExtra(AmbiService.EXTRA_LIGHTS_ON, 0);
                int lightsSync = intent.getIntExtra(AmbiService.EXTRA_LIGHTS_SYNC, 0);

                if (lightsSync > 0) {
                    goveeChip.setText("●  GOVEE  " + lightsSync + "/" + activeConfigured + " EN SYNC");
                } else if (lightsOn > 0) {
                    goveeChip.setText("●  GOVEE  " + lightsOn + "/" + activeConfigured + " ALLUMÉ" + (lightsOn > 1 ? "S" : ""));
                } else if (activeConfigured == 0 && savedConfigured > 0) {
                    goveeChip.setText("○  GOVEE  EN PAUSE");
                } else {
                    goveeChip.setText("○  GOVEE  " + activeConfigured + " PRÊT" + (activeConfigured > 1 ? "S" : ""));
                }

                goveeChip.setTextColor(
                        lightsSync > 0 ? 0xff76e2ad : (goveeReachable ? 0xffffc36b : 0xff9099ad)
                );

                status.setTextColor(syncing ? 0xffd0c5ff : Color.WHITE);
                autoButton.setSelectedStyle(syncing);
                baseButton.setSelectedStyle(!syncing);

                int red = intent.getIntExtra(AmbiService.EXTRA_R, -1);
                int green = intent.getIntExtra(AmbiService.EXTRA_G, -1);
                int blue = intent.getIntExtra(AmbiService.EXTRA_B, -1);

                if (red >= 0 && green >= 0 && blue >= 0) {
                    colorPreview.setBackground(roundColor(Color.rgb(red, green, blue), 24));
                    colorLabel.setText(syncing
                            ? "Couleur Ambilight en direct"
                            : "Dernière couleur Ambilight");
                }
            }
        };

        IntentFilter filter = new IntentFilter(AmbiService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, filter);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateManager.onActivityResumed(this);

        if (autoButton != null) {
            refreshProfileButtons();
            autoButton.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (getCurrentFocus() == null) autoButton.requestFocus();
            }, 120);
        }
    }

    @Override
    protected void onDestroy() {
        if (statusReceiver != null) {
            try {
                unregisterReceiver(statusReceiver);
            } catch (Exception ignored) {}
            statusReceiver = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
