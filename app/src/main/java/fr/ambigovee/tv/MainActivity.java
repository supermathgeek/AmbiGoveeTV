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
 * Ecran principal Android TV.
 *
 * Interface pensée pour la télécommande :
 * - navigation D-pad explicite
 * - focus visible via TvButton
 * - aucune gestion manuelle de luminosité
 * - synchronisation Ambilight / Govee
 * - vérification des mises à jour
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

        // Vérifie GitHub au lancement.
        UpdateManager.checkOnLaunch(this);
    }

    /**
     * Construit l'interface principale.
     */
    private View buildUi() {

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                dp(52),
                dp(24),
                dp(52),
                dp(24)
        );
        root.setBackground(pageBackground());

        /*
         * HEADER
         */
        LinearLayout header = row(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);

        brand.addView(
                text(
                        "AmbiGovee",
                        34,
                        Color.WHITE,
                        true
                )
        );

        brand.addView(
                text(
                        "Ton Ambilight. Toute ta pièce.",
                        15,
                        0xff9fa8bd,
                        false
                )
        );

        header.addView(
                brand,
                new LinearLayout.LayoutParams(
                        0,
                        dp(62),
                        1
                )
        );

        TextView version = text(
                "v" + BuildConfig.VERSION_NAME + "  •  Android TV",
                14,
                0xff8f98ad,
                true
        );

        version.setGravity(
                Gravity.RIGHT | Gravity.CENTER_VERTICAL
        );

        header.addView(
                version,
                new LinearLayout.LayoutParams(
                        dp(240),
                        dp(62)
                )
        );

        root.addView(header);

        /*
         * ETAT PHILIPS / GOVEE
         */
        LinearLayout chips = row(Gravity.CENTER_VERTICAL);

        tvChip = chip(
                "●  PHILIPS  CONNEXION…"
        );

        goveeChip = chip(
                "●  GOVEE  CONNEXION…"
        );

        LinearLayout.LayoutParams chipParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(44),
                        1
                );

        chipParams.setMargins(
                dp(5),
                dp(8),
                dp(5),
                0
        );

        chips.addView(tvChip, chipParams);
        chips.addView(goveeChip, chipParams);

        root.addView(chips);

        /*
         * CARTE PRINCIPALE
         */
        LinearLayout hero = card();

        hero.setOrientation(
                LinearLayout.HORIZONTAL
        );

        hero.setGravity(
                Gravity.CENTER_VERTICAL
        );

        hero.setPadding(
                dp(24),
                dp(16),
                dp(24),
                dp(16)
        );

        LinearLayout.LayoutParams heroParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(110)
                );

        heroParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        root.addView(
                hero,
                heroParams
        );

        /*
         * APERCU COULEUR
         */
        colorPreview = new View(this);

        colorPreview.setBackground(
                roundColor(
                        0xff30284a,
                        24
                )
        );

        LinearLayout.LayoutParams previewParams =
                new LinearLayout.LayoutParams(
                        dp(72),
                        dp(72)
                );

        previewParams.setMargins(
                0,
                0,
                dp(20),
                0
        );

        hero.addView(
                colorPreview,
                previewParams
        );

        /*
         * TEXTE ETAT
         */
        LinearLayout heroText =
                new LinearLayout(this);

        heroText.setOrientation(
                LinearLayout.VERTICAL
        );

        status = text(
                "AmbiGovee démarre…",
                24,
                Color.WHITE,
                true
        );

        colorLabel = text(
                "En attente du flux Ambilight",
                14,
                0xff9fa8bd,
                false
        );

        heroText.addView(status);
        heroText.addView(colorLabel);

        hero.addView(
                heroText,
                new LinearLayout.LayoutParams(
                        0,
                        -2,
                        1
                )
        );

        /*
         * MODE COURANT
         */
        profileLabel = text(
                profileName(
                        ConfigStore.profile(this)
                ),
                15,
                0xffc7bbff,
                true
        );

        profileLabel.setGravity(
                Gravity.RIGHT
                        | Gravity.CENTER_VERTICAL
        );

        hero.addView(
                profileLabel,
                new LinearLayout.LayoutParams(
                        dp(230),
                        dp(54)
                )
        );

        /*
         * ACTIONS PRINCIPALES
         */
        LinearLayout actions =
                row(Gravity.CENTER);

        autoButton = tvButton(
                "⚡  SYNCHRO AUTOMATIQUE",
                0xff35255f,
                0xff7655ff,
                ID_AUTO
        );

        baseButton = tvButton(
                "☀  LUMIERE NORMALE",
                0xff171d2a,
                0xff4c5a78,
                ID_BASE
        );

        autoButton.setOnClickListener(
                v -> startAmbiService(
                        AmbiService.ACTION_AUTO
                )
        );

        baseButton.setOnClickListener(
                v -> startAmbiService(
                        AmbiService.ACTION_BASE
                )
        );

        LinearLayout.LayoutParams actionParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(62),
                        1
                );

        actionParams.setMargins(
                dp(6),
                dp(12),
                dp(6),
                0
        );

        actions.addView(
                autoButton,
                actionParams
        );

        actions.addView(
                baseButton,
                actionParams
        );

        root.addView(actions);

        /*
         * MODES DE REACTION
         */
        LinearLayout modeCard = card();

        modeCard.setOrientation(
                LinearLayout.HORIZONTAL
        );

        modeCard.setGravity(
                Gravity.CENTER_VERTICAL
        );

        modeCard.setPadding(
                dp(18),
                dp(8),
                dp(18),
                dp(8)
        );

        LinearLayout.LayoutParams modeCardParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(72)
                );

        modeCardParams.setMargins(
                0,
                dp(12),
                0,
                0
        );

        root.addView(
                modeCard,
                modeCardParams
        );

        TextView modeTitle = text(
                "REACTION",
                13,
                0xff7f899f,
                true
        );

        modeTitle.setGravity(
                Gravity.CENTER_VERTICAL
        );

        modeCard.addView(
                modeTitle,
                new LinearLayout.LayoutParams(
                        dp(112),
                        dp(54)
                )
        );

        directButton = tvButton(
                "DIRECT",
                0xff171d2a,
                0xff7655ff,
                ID_DIRECT
        );

        cinemaButton = tvButton(
                "CINEMA",
                0xff171d2a,
                0xff7655ff,
                ID_CINEMA
        );

        douxButton = tvButton(
                "DOUX",
                0xff171d2a,
                0xff7655ff,
                ID_DOUX
        );

        directButton.setOnClickListener(
                v -> setProfile(
                        ConfigStore.PROFILE_DIRECT
                )
        );

        cinemaButton.setOnClickListener(
                v -> setProfile(
                        ConfigStore.PROFILE_CINEMA
                )
        );

        douxButton.setOnClickListener(
                v -> setProfile(
                        ConfigStore.PROFILE_DOUX
                )
        );

        LinearLayout.LayoutParams profileParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(54),
                        1
                );

        profileParams.setMargins(
                dp(5),
                0,
                dp(5),
                0
        );

        modeCard.addView(
                directButton,
                profileParams
        );

        modeCard.addView(
                cinemaButton,
                profileParams
        );

        modeCard.addView(
                douxButton,
                profileParams
        );

        /*
         * GESTION APPAREILS / UPDATE
         */
        LinearLayout bottom =
                row(Gravity.CENTER);

        devicesButton = tvButton(
                "⌁  GERER LES APPAREILS",
                0xff171d2a,
                0xff4c5a78,
                ID_DEVICES
        );

        updateButton = tvButton(
                "↻  VERIFIER LES MISES A JOUR",
                0xff171d2a,
                0xff4c5a78,
                ID_UPDATE
        );

        devicesButton.setOnClickListener(
                v -> startActivity(
                        new Intent(
                                this,
                                SetupActivity.class
                        )
                                .putExtra(
                                        "edit",
                                        true
                                )
                                .putExtra(
                                        "start_step",
                                        2
                                )
                )
        );

        updateButton.setOnClickListener(
                v -> UpdateManager.checkNow(this)
        );

        LinearLayout.LayoutParams bottomParams =
                new LinearLayout.LayoutParams(
                        0,
                        dp(58),
                        1
                );

        bottomParams.setMargins(
                dp(6),
                dp(12),
                dp(6),
                0
        );

        bottom.addView(
                devicesButton,
                bottomParams
        );

        bottom.addView(
                updateButton,
                bottomParams
        );

        root.addView(bottom);

        /*
         * NOTE
         */
        TextView note = text(
                "Le rendu s'adapte automatiquement à l'Ambilight. "
                        + "Les appareils déconnectés sont ignorés.",
                13,
                0xff818ba0,
                false
        );

        note.setGravity(
                Gravity.CENTER
        );

        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(36)
                );

        noteParams.setMargins(
                0,
                dp(6),
                0,
                0
        );

        root.addView(
                note,
                noteParams
        );

        /*
         * NAVIGATION TELECOMMANDE
         */
        wireFocus();

        /*
         * AFFICHAGE MODE SELECTIONNE
         */
        refreshProfileButtons();

        return root;
    }

    /**
     * Navigation D-pad explicite.
     */
    private void wireFocus() {

        /*
         * Ligne principale.
         */
        autoButton.setNextFocusRightId(
                ID_BASE
        );

        autoButton.setNextFocusDownId(
                ID_DIRECT
        );

        baseButton.setNextFocusLeftId(
                ID_AUTO
        );

        baseButton.setNextFocusDownId(
                ID_DOUX
        );

        /*
         * Modes.
         */
        directButton.setNextFocusUpId(
                ID_AUTO
        );

        directButton.setNextFocusRightId(
                ID_CINEMA
        );

        directButton.setNextFocusDownId(
                ID_DEVICES
        );

        cinemaButton.setNextFocusUpId(
                ID_AUTO
        );

        cinemaButton.setNextFocusLeftId(
                ID_DIRECT
        );

        cinemaButton.setNextFocusRightId(
                ID_DOUX
        );

        cinemaButton.setNextFocusDownId(
                ID_DEVICES
        );

        douxButton.setNextFocusUpId(
                ID_BASE
        );

        douxButton.setNextFocusLeftId(
                ID_CINEMA
        );

        douxButton.setNextFocusDownId(
                ID_UPDATE
        );

        /*
         * Ligne inférieure.
         */
        devicesButton.setNextFocusUpId(
                ID_DIRECT
        );

        devicesButton.setNextFocusRightId(
                ID_UPDATE
        );

        updateButton.setNextFocusUpId(
                ID_DOUX
        );

        updateButton.setNextFocusLeftId(
                ID_DEVICES
        );
    }

    /**
     * Change le profil de réaction.
     */
    private void setProfile(String profile) {

        ConfigStore.setProfile(
                this,
                profile
        );

        if (profileLabel != null) {
            profileLabel.setText(
                    profileName(profile)
            );
        }

        refreshProfileButtons();

        startAmbiService(
                AmbiService.ACTION_RELOAD
        );
    }

    /**
     * Met visuellement en avant
     * le mode actuellement actif.
     */
    private void refreshProfileButtons() {

        if (directButton == null
                || cinemaButton == null
                || douxButton == null) {
            return;
        }

        String profile =
                ConfigStore.profile(this);

        directButton.setSelectedStyle(
                ConfigStore.PROFILE_DIRECT.equals(
                        profile
                )
        );

        cinemaButton.setSelectedStyle(
                ConfigStore.PROFILE_CINEMA.equals(
                        profile
                )
        );

        douxButton.setSelectedStyle(
                ConfigStore.PROFILE_DOUX.equals(
                        profile
                )
        );
    }

    /**
     * Crée une ligne horizontale.
     */
    private LinearLayout row(int gravity) {

        LinearLayout view =
                new LinearLayout(this);

        view.setOrientation(
                LinearLayout.HORIZONTAL
        );

        view.setGravity(gravity);

        return view;
    }

    /**
     * Carte graphique.
     */
    private LinearLayout card() {

        LinearLayout view =
                new LinearLayout(this);

        view.setBackground(
                roundGradient(
                        0xff151a27,
                        0xff101521,
                        20,
                        0xff293144
                )
        );

        return view;
    }

    /**
     * Bouton optimisé TV.
     */
    private TvButton tvButton(
            String label,
            int normal,
            int focused,
            int id
    ) {

        TvButton button =
                new TvButton(this);

        button.setId(id);
        button.setText(label);

        button.colors(
                normal,
                focused
        );

        return button;
    }

    /**
     * Badge d'état.
     */
    private TextView chip(String label) {

        TextView view = text(
                label,
                13,
                0xffa2aabe,
                true
        );

        view.setGravity(
                Gravity.CENTER
        );

        view.setBackground(
                roundGradient(
                        0xff151a27,
                        0xff111621,
                        16,
                        0xff293144
                )
        );

        return view;
    }

    /**
     * Création rapide d'un texte.
     */
    private TextView text(
            String value,
            int size,
            int color,
            boolean bold
    ) {

        TextView view =
                new TextView(this);

        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT_BOLD
            );
        }

        return view;
    }

    /**
     * Fond général.
     */
    private GradientDrawable pageBackground() {

        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        0xff070a10,
                        0xff0d1019,
                        0xff170f25
                }
        );
    }

    /**
     * Fond dégradé avec bordure.
     */
    private GradientDrawable roundGradient(
            int colorA,
            int colorB,
            int radius,
            int strokeColor
    ) {

        GradientDrawable drawable =
                new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{
                                colorA,
                                colorB
                        }
                );

        drawable.setCornerRadius(
                dp(radius)
        );

        drawable.setStroke(
                dp(1),
                strokeColor
        );

        return drawable;
    }

    /**
     * Fond couleur simple.
     */
    private GradientDrawable roundColor(
            int color,
            int radius
    ) {

        GradientDrawable drawable =
                new GradientDrawable();

        drawable.setColor(color);

        drawable.setCornerRadius(
                dp(radius)
        );

        drawable.setStroke(
                dp(2),
                0xff4a425e
        );

        return drawable;
    }

    /**
     * Nom utilisateur du profil.
     */
    private String profileName(String id) {

        if (ConfigStore.PROFILE_CINEMA.equals(id)) {
            return "Cinema  •  fluide";
        }

        if (ConfigStore.PROFILE_DOUX.equals(id)) {
            return "Doux  •  ambiance";
        }

        return "Direct  •  réactif";
    }

    /**
     * Lance / commande le moteur AmbiGovee.
     */
    private void startAmbiService(String action) {

        Intent intent =
                new Intent(
                        this,
                        AmbiService.class
                );

        if (action != null) {
            intent.setAction(action);
        }

        if (Build.VERSION.SDK_INT
                >= Build.VERSION_CODES.O) {

            startForegroundService(intent);

        } else {

            startService(intent);
        }
    }

    /**
     * Ecoute l'état du moteur AmbiGovee.
     */
    private void registerStatusReceiver() {

        statusReceiver =
                new BroadcastReceiver() {

                    @Override
                    public void onReceive(
                            Context context,
                            Intent intent
                    ) {

                        String currentStatus =
                                intent.getStringExtra(
                                        AmbiService.EXTRA_STATUS
                                );

                        if (currentStatus != null
                                && status != null) {

                            status.setText(
                                    currentStatus
                            );
                        }

                        boolean tvActive =
                                intent.getBooleanExtra(
                                        AmbiService.EXTRA_TV,
                                        true
                                );

                        boolean goveeReachable =
                                intent.getBooleanExtra(
                                        AmbiService.EXTRA_GOVEE,
                                        false
                                );

                        boolean syncing =
                                intent.getBooleanExtra(
                                        AmbiService.EXTRA_SYNC,
                                        false
                                );

                        /*
                         * PHILIPS
                         */
                        boolean philipsPaired =
                                !ConfigStore.tvUser(
                                        MainActivity.this
                                ).isEmpty()
                                        &&
                                        !ConfigStore.tvKey(
                                                MainActivity.this
                                        ).isEmpty();

                        if (!philipsPaired) {

                            tvChip.setText(
                                    "○  PHILIPS  DÉCONNECTÉE"
                            );

                            tvChip.setTextColor(
                                    0xff9099ad
                            );

                        } else {

                            tvChip.setText(
                                    tvActive
                                            ? "●  PHILIPS  ACTIVE"
                                            : "●  PHILIPS  VEILLE"
                            );

                            tvChip.setTextColor(
                                    tvActive
                                            ? 0xff76e2ad
                                            : 0xff9099ad
                            );
                        }

                        /*
                         * GOVEE
                         */
                        int activeConfigured =
                                ConfigStore.enabledGoveeCount(
                                        MainActivity.this
                                );

                        int savedConfigured =
                                ConfigStore.goveeLights(
                                        MainActivity.this
                                ).size();

                        int lightsOn =
                                intent.getIntExtra(
                                        AmbiService.EXTRA_LIGHTS_ON,
                                        0
                                );

                        int lightsSync =
                                intent.getIntExtra(
                                        AmbiService.EXTRA_LIGHTS_SYNC,
                                        0
                                );

                        if (lightsSync > 0) {

                            goveeChip.setText(
                                    "●  GOVEE  "
                                            + lightsSync
                                            + "/"
                                            + activeConfigured
                                            + " EN SYNC"
                            );

                        } else if (lightsOn > 0) {

                            goveeChip.setText(
                                    "●  GOVEE  "
                                            + lightsOn
                                            + "/"
                                            + activeConfigured
                                            + " ALLUMÉ"
                                            + (
                                            lightsOn > 1
                                                    ? "S"
                                                    : ""
                                    )
                            );

                        } else if (
                                activeConfigured == 0
                                        && savedConfigured > 0
                        ) {

                            goveeChip.setText(
                                    "●  GOVEE  TOUS DÉCONNECTÉS"
                            );

                        } else {

                            goveeChip.setText(
                                    "●  GOVEE  "
                                            + activeConfigured
                                            + " CONNECTÉ"
                                            + (
                                            activeConfigured > 1
                                                    ? "S"
                                                    : ""
                                    )
                                            + " • EN ATTENTE"
                            );
                        }

                        goveeChip.setTextColor(
                                lightsSync > 0
                                        ? 0xff76e2ad
                                        : (
                                        goveeReachable
                                                ? 0xffffc36b
                                                : 0xff9099ad
                                )
                        );

                        /*
                         * STATUS
                         */
                        if (status != null) {
                            status.setTextColor(
                                    syncing
                                            ? 0xffd0c5ff
                                            : Color.WHITE
                            );
                        }

                        /*
                         * COULEUR AMBILIGHT
                         */
                        int red =
                                intent.getIntExtra(
                                        AmbiService.EXTRA_R,
                                        -1
                                );

                        int green =
                                intent.getIntExtra(
                                        AmbiService.EXTRA_G,
                                        -1
                                );

                        int blue =
                                intent.getIntExtra(
                                        AmbiService.EXTRA_B,
                                        -1
                                );

                        if (red >= 0
                                && green >= 0
                                && blue >= 0) {

                            if (colorPreview != null) {

                                colorPreview.setBackground(
                                        roundColor(
                                                Color.rgb(
                                                        red,
                                                        green,
                                                        blue
                                                ),
                                                24
                                        )
                                );
                            }

                            if (colorLabel != null) {

                                colorLabel.setText(
                                        syncing
                                                ? "Couleur Ambilight en direct"
                                                : "Dernière couleur Ambilight"
                                );
                            }
                        }
                    }
                };

        IntentFilter filter =
                new IntentFilter(
                        AmbiService.ACTION_STATUS
                );

        if (Build.VERSION.SDK_INT >= 33) {

            registerReceiver(
                    statusReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
            );

        } else {

            registerReceiver(
                    statusReceiver,
                    filter
            );
        }
    }

    /**
     * Une seule méthode onResume.
     *
     * Elle fait les deux choses :
     * - reprend le système de mise à jour
     * - restaure correctement le focus TV
     */
    @Override
    protected void onResume() {
        super.onResume();

        UpdateManager.onActivityResumed(this);

        if (autoButton != null) {

            refreshProfileButtons();

            autoButton.postDelayed(
                    () -> {

                        if (isFinishing()
                                || isDestroyed()) {
                            return;
                        }

                        View current =
                                getCurrentFocus();

                        if (current == null
                                && autoButton != null) {

                            autoButton.requestFocus();
                        }

                    },
                    120
            );
        }
    }

    /**
     * Nettoyage du BroadcastReceiver.
     */
    @Override
    protected void onDestroy() {

        if (statusReceiver != null) {

            try {

                unregisterReceiver(
                        statusReceiver
                );

            } catch (Exception ignored) {
                // Receiver déjà retiré.
            }

            statusReceiver = null;
        }

        super.onDestroy();
    }

    /**
     * Conversion dp -> pixels.
     */
    private int dp(int value) {

        return (int) (
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
                        + 0.5f
        );
    }
}
