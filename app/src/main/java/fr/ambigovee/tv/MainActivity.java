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
 * Tableau de bord Android TV.
 *
 * L'interface est volontairement "10-foot UI" :
 * - peu de texte ;
 * - gros éléments ;
 * - focus très visible ;
 * - aucune action essentielle cachée ;
 * - navigation D-pad déterministe.
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
        root.setPadding(dp(44), dp(26), dp(44), dp(28));
        root.setBackground(pageBackground());

        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, dp(74)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.TOP);

        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, 0, 1);
        bodyParams.setMargins(0, dp(12), 0, 0);
        root.addView(body, bodyParams);

        LinearLayout left = buildStatusPanel();
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(dp(350), -1);
        leftParams.setMargins(0, 0, dp(18), 0);
        body.addView(left, leftParams);

        LinearLayout controls = buildControls();
        body.addView(controls, new LinearLayout.LayoutParams(0, -1, 1));

        wireFocus();
        refreshProfileButtons();

        return root;
    }

    private View buildHeader() {
        LinearLayout header = row(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);

        TextView app = text("AmbiGovee", 36, 0xfff5f7fb, true);
        TextView sub = text("AMBILIGHT  ×  GOVEE", 12, 0xff8c96a8, true);
        sub.setLetterSpacing(0.12f);

        brand.addView(app);
        brand.addView(sub);

        header.addView(brand, new LinearLayout.LayoutParams(0, dp(70), 1));

        TextView version = text("v" + BuildConfig.VERSION_NAME, 13, 0xff6f7a8d, true);
        version.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(version, new LinearLayout.LayoutParams(dp(120), dp(70)));

        return header;
    }

    private LinearLayout buildStatusPanel() {
        LinearLayout panel = card(26);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24), dp(24), dp(24), dp(22));

        TextView section = text("ÉTAT", 12, 0xff8d7fff, true);
        section.setLetterSpacing(0.12f);
        panel.addView(section);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, dp(132));
        heroParams.setMargins(0, dp(10), 0, 0);
        panel.addView(hero, heroParams);

        colorPreview = new View(this);
        colorPreview.setBackground(roundColor(0xff332b4b, 28));
        LinearLayout.LayoutParams colorParams = new LinearLayout.LayoutParams(dp(104), dp(104));
        colorParams.setMargins(0, 0, dp(18), 0);
        hero.addView(colorPreview, colorParams);

        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.setGravity(Gravity.CENTER_VERTICAL);

        status = text("Démarrage…", 24, 0xfff5f7fb, true);
        status.setMaxLines(2);
        heroText.addView(status);

        colorLabel = text("En attente de l'Ambilight", 13, 0xff8c96a8, false);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(0, dp(6), 0, 0);
        heroText.addView(colorLabel, cp);

        profileLabel = pill(profileName(ConfigStore.profile(this)), 0xffd4caff, 0xff251d42);
        LinearLayout.LayoutParams profileParams = new LinearLayout.LayoutParams(-2, dp(34));
        profileParams.setMargins(0, dp(10), 0, 0);
        heroText.addView(profileLabel, profileParams);

        hero.addView(heroText, new LinearLayout.LayoutParams(0, -1, 1));

        TextView devicesTitle = text("APPAREILS", 11, 0xff717c8f, true);
        devicesTitle.setLetterSpacing(0.10f);
        LinearLayout.LayoutParams dtp = new LinearLayout.LayoutParams(-1, -2);
        dtp.setMargins(0, dp(18), 0, dp(8));
        panel.addView(devicesTitle, dtp);

        tvChip = statusRow("○", "PHILIPS", "Connexion…");
        goveeChip = statusRow("○", "GOVEE", "Connexion…");

        LinearLayout.LayoutParams srp = new LinearLayout.LayoutParams(-1, dp(58));
        srp.setMargins(0, dp(6), 0, 0);
        panel.addView(tvChip, srp);

        LinearLayout.LayoutParams srp2 = new LinearLayout.LayoutParams(-1, dp(58));
        srp2.setMargins(0, dp(7), 0, 0);
        panel.addView(goveeChip, srp2);

        TextView privacy = text(
                "Local • aucune caméra • aucune commande d'allumage forcée",
                11,
                0xff626d80,
                false
        );
        privacy.setGravity(Gravity.LEFT | Gravity.BOTTOM);
        privacy.setMaxLines(2);

        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, 0, 1);
        pp.setMargins(0, dp(18), 0, 0);
        panel.addView(privacy, pp);

        return panel;
    }

    private LinearLayout buildControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);

        TextView controlTitle = text("CONTRÔLE", 12, 0xff8d7fff, true);
        controlTitle.setLetterSpacing(0.12f);
        controls.addView(controlTitle);

        LinearLayout actions = row(Gravity.CENTER_VERTICAL);

        autoButton = tvButton("▶  ACTIVER LA SYNCHRO", 0xff2d2252, 0xff7357ff, ID_AUTO);
        baseButton = tvButton("■  LUMIÈRE NORMALE", 0xff151b27, 0xff3c4960, ID_BASE);

        autoButton.setOnClickListener(v -> startAmbiService(AmbiService.ACTION_AUTO));
        baseButton.setOnClickListener(v -> startAmbiService(AmbiService.ACTION_BASE));

        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(82), 1);
        actionParams.setMargins(dp(5), dp(10), dp(5), 0);
        actions.addView(autoButton, actionParams);
        actions.addView(baseButton, actionParams);
        controls.addView(actions);

        LinearLayout modePanel = card(22);
        modePanel.setOrientation(LinearLayout.VERTICAL);
        modePanel.setPadding(dp(18), dp(15), dp(18), dp(16));

        LinearLayout.LayoutParams modePanelParams = new LinearLayout.LayoutParams(-1, dp(144));
        modePanelParams.setMargins(dp(5), dp(14), dp(5), 0);
        controls.addView(modePanel, modePanelParams);

        LinearLayout modeHeader = row(Gravity.CENTER_VERTICAL);
        modeHeader.addView(text("Ambiance", 18, 0xfff5f7fb, true), new LinearLayout.LayoutParams(0, dp(32), 1));

        TextView hint = text("Choisis le niveau de réactivité", 12, 0xff7d879a, false);
        hint.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        modeHeader.addView(hint, new LinearLayout.LayoutParams(dp(310), dp(32)));
        modePanel.addView(modeHeader);

        LinearLayout modeRow = row(Gravity.CENTER_VERTICAL);

        directButton = tvButton("DIRECT", 0xff151b27, 0xff7357ff, ID_DIRECT);
        cinemaButton = tvButton("CINÉMA", 0xff151b27, 0xff7357ff, ID_CINEMA);
        douxButton = tvButton("DOUX", 0xff151b27, 0xff7357ff, ID_DOUX);

        directButton.setOnClickListener(v -> setProfile(ConfigStore.PROFILE_DIRECT));
        cinemaButton.setOnClickListener(v -> setProfile(ConfigStore.PROFILE_CINEMA));
        douxButton.setOnClickListener(v -> setProfile(ConfigStore.PROFILE_DOUX));

        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(0, dp(68), 1);
        modeParams.setMargins(dp(4), dp(10), dp(4), 0);
        modeRow.addView(directButton, modeParams);
        modeRow.addView(cinemaButton, modeParams);
        modeRow.addView(douxButton, modeParams);
        modePanel.addView(modeRow);

        TextView toolsTitle = text("RÉGLAGES", 12, 0xff8d7fff, true);
        toolsTitle.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams toolTitleParams = new LinearLayout.LayoutParams(-1, -2);
        toolTitleParams.setMargins(0, dp(18), 0, 0);
        controls.addView(toolsTitle, toolTitleParams);

        LinearLayout utilityRow = row(Gravity.CENTER_VERTICAL);

        devicesButton = tvButton("⚙  APPAREILS", 0xff151b27, 0xff3c4960, ID_DEVICES);
        updateButton = tvButton("↻  MISE À JOUR", 0xff151b27, 0xff3c4960, ID_UPDATE);

        devicesButton.setOnClickListener(v -> startActivity(
                new Intent(this, SetupActivity.class)
                        .putExtra("edit", true)
                        .putExtra("start_step", 1)
        ));
        updateButton.setOnClickListener(v -> UpdateManager.checkNow(this));

        LinearLayout.LayoutParams utilityParams = new LinearLayout.LayoutParams(0, dp(68), 1);
        utilityParams.setMargins(dp(5), dp(10), dp(5), 0);
        utilityRow.addView(devicesButton, utilityParams);
        utilityRow.addView(updateButton, utilityParams);
        controls.addView(utilityRow);

        TextView footer = text(
                "AmbiGovee respecte toujours l'état réel de tes lumières.",
                12,
                0xff687386,
                false
        );
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, 0, 1);
        fp.setMargins(0, dp(16), 0, 0);
        controls.addView(footer, fp);

        return controls;
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

        if (profileLabel != null) profileLabel.setText(profileName(profile));
    }

    private LinearLayout row(int gravity) {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(gravity);
        return view;
    }

    private LinearLayout card(int radius) {
        LinearLayout view = new LinearLayout(this);
        view.setBackground(roundGradient(0xff121722, 0xff0e131d, radius, 0xff252d3d));
        return view;
    }

    private TvButton tvButton(String label, int normal, int focused, int id) {
        TvButton button = new TvButton(this);
        button.setId(id);
        button.setText(label);
        button.colors(normal, focused);
        return button;
    }

    private TextView statusRow(String icon, String title, String detail) {
        TextView view = text(icon + "  " + title + "   •   " + detail, 13, 0xffa1aabc, true);
        view.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        view.setPadding(dp(16), 0, dp(14), 0);
        view.setBackground(roundGradient(0xff111722, 0xff0d121b, 14, 0xff252d3d));
        return view;
    }

    private TextView pill(String label, int foreground, int background) {
        TextView view = text(label, 12, foreground, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(background);
        bg.setCornerRadius(dp(17));
        bg.setStroke(dp(1), 0xff493c70);
        view.setBackground(bg);
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable pageBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xff06080d, 0xff090d15, 0xff151020}
        );
    }

    private GradientDrawable roundGradient(int a, int b, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{a, b}
        );
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private GradientDrawable roundColor(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(2), 0xff51436f);
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
                    tvChip.setText("○  PHILIPS   •   DÉCONNECTÉE");
                    tvChip.setTextColor(0xff8c96a8);
                } else {
                    tvChip.setText(tvActive
                            ? "●  PHILIPS   •   ACTIVE"
                            : "●  PHILIPS   •   EN VEILLE");
                    tvChip.setTextColor(tvActive ? 0xff6fe3a6 : 0xff8c96a8);
                }

                int activeConfigured = ConfigStore.enabledGoveeCount(MainActivity.this);
                int savedConfigured = ConfigStore.goveeLights(MainActivity.this).size();
                int lightsOn = intent.getIntExtra(AmbiService.EXTRA_LIGHTS_ON, 0);
                int lightsSync = intent.getIntExtra(AmbiService.EXTRA_LIGHTS_SYNC, 0);

                if (lightsSync > 0) {
                    goveeChip.setText("●  GOVEE   •   " + lightsSync + "/" + activeConfigured + " EN SYNC");
                } else if (lightsOn > 0) {
                    goveeChip.setText("●  GOVEE   •   " + lightsOn + "/" + activeConfigured + " ALLUMÉ"
                            + (lightsOn > 1 ? "S" : ""));
                } else if (activeConfigured == 0 && savedConfigured > 0) {
                    goveeChip.setText("○  GOVEE   •   EN PAUSE");
                } else {
                    goveeChip.setText("○  GOVEE   •   " + activeConfigured + " PRÊT"
                            + (activeConfigured > 1 ? "S" : ""));
                }

                goveeChip.setTextColor(
                        lightsSync > 0 ? 0xff6fe3a6 : (goveeReachable ? 0xfff4c56a : 0xff8c96a8)
                );

                status.setTextColor(syncing ? 0xffd7d0ff : 0xfff5f7fb);
                autoButton.setSelectedStyle(syncing);
                baseButton.setSelectedStyle(!syncing);

                int red = intent.getIntExtra(AmbiService.EXTRA_R, -1);
                int green = intent.getIntExtra(AmbiService.EXTRA_G, -1);
                int blue = intent.getIntExtra(AmbiService.EXTRA_B, -1);

                if (red >= 0 && green >= 0 && blue >= 0) {
                    colorPreview.setBackground(roundColor(Color.rgb(red, green, blue), 28));
                    colorLabel.setText(syncing ? "Couleur Ambilight en direct" : "Dernière couleur Ambilight");
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
