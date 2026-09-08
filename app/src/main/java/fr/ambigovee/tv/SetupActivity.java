package fr.ambigovee.tv;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Assistant de configuration "TV-first".
 *
 * 3 étapes visibles en permanence à gauche.
 * Les choix utilisent des modales maison afin d'éviter les AlertDialog Android
 * qui cassent la direction artistique.
 */
public class SetupActivity extends Activity {

    private interface ChoiceHandler {
        void choose(int index);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int step = 0;
    private LinearLayout content;
    private LinearLayout stepRail;
    private TextView message;
    private TvButton primary;
    private TvButton back;
    private TvButton next;
    private PairingBridgeServer pairingBridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ConfigStore.seedDefaults(this);
        stopService(new Intent(this, AmbiService.class));

        buildShell();

        int initialStep = Math.max(0, Math.min(2,
                getIntent().getIntExtra("start_step", 0)));
        showStep(initialStep);

        UpdateManager.checkOnLaunch(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateManager.onActivityResumed(this);

        if (step == 0 && pairingBridge != null && isPhilipsPaired()) {
            showStep(0);
        }
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(dp(34), dp(28), dp(40), dp(28));
        root.setBackground(pageBackground());

        stepRail = buildStepRail();
        LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(260), -1);
        railParams.setMargins(0, 0, dp(28), 0);
        root.addView(stepRail, railParams);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(text("Configuration", 30, 0xfff5f7fb, true));

        TextView subtitle = text("Tout se fait depuis la TV et ton téléphone.", 13, 0xff8c96a8, false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.setMargins(0, dp(5), 0, 0);
        brand.addView(subtitle, sp);

        header.addView(brand, new LinearLayout.LayoutParams(0, dp(70), 1));

        TextView version = text("AmbiGovee  •  v" + BuildConfig.VERSION_NAME, 12, 0xff6f7a8d, true);
        version.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(version, new LinearLayout.LayoutParams(dp(240), dp(70)));

        right.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.setMargins(0, dp(8), 0, dp(14));
        right.addView(scroll, scrollParams);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);

        back = button("←  RETOUR", 0xff151b27, 0xff3c4960);
        back.setOnClickListener(v -> {
            if (step == 0) finish();
            else showStep(step - 1);
        });

        next = button("CONTINUER  →", 0xff2d2252, 0xff7357ff);
        next.setOnClickListener(v -> showStep(Math.min(2, step + 1)));

        nav.addView(back, new LinearLayout.LayoutParams(dp(210), dp(62)));
        nav.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        nav.addView(next, new LinearLayout.LayoutParams(dp(260), dp(62)));

        back.setNextFocusRightId(next.getId());
        next.setNextFocusLeftId(back.getId());

        right.addView(nav);

        root.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
        setContentView(root);
    }

    private LinearLayout buildStepRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(dp(20), dp(22), dp(20), dp(20));
        rail.setBackground(roundGradient(0xff101620, 0xff0c1119, 24, 0xff252d3d));

        TextView logo = text("AmbiGovee", 25, 0xfff5f7fb, true);
        rail.addView(logo);

        TextView setup = text("MISE EN ROUTE", 11, 0xff7768c9, true);
        setup.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams st = new LinearLayout.LayoutParams(-1, -2);
        st.setMargins(0, dp(6), 0, dp(24));
        rail.addView(setup, st);

        rail.addView(stepItem(0, "1", "Philips", "Associer la TV"));
        rail.addView(stepItem(1, "2", "Govee", "Ajouter les lumières"));
        rail.addView(stepItem(2, "3", "Terminé", "Tester et démarrer"));

        TextView note = text(
                "La configuration reste modifiable plus tard depuis « Appareils ».",
                11,
                0xff687386,
                false
        );
        note.setGravity(Gravity.LEFT | Gravity.BOTTOM);

        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, 0, 1);
        np.setMargins(0, dp(20), 0, 0);
        rail.addView(note, np);

        return rail;
    }

    private View stepItem(int index, String number, String title, String detail) {
        boolean active = index == step;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.setTag("step_" + index);
        box.setBackground(roundGradient(
                active ? 0xff2c2348 : 0xff111722,
                active ? 0xff211a38 : 0xff0f141d,
                16,
                active ? 0xff6653a3 : 0xff202838
        ));

        TextView badge = text(number, 14, active ? 0xffe2dcff : 0xff8b95a7, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundGradient(
                active ? 0xff7058d9 : 0xff1a2130,
                active ? 0xff5842be : 0xff151b26,
                13,
                active ? 0xff8e7aff : 0xff2c3547
        ));
        box.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        texts.addView(text(title, 15, active ? 0xfff5f7fb : 0xffa2abba, true));
        texts.addView(text(detail, 11, active ? 0xffaaa0d7 : 0xff677285, false));
        box.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(64));
        p.setMargins(0, 0, 0, dp(8));
        box.setLayoutParams(p);
        return box;
    }

    private void refreshStepRail() {
        for (int i = 0; i < 3; i++) {
            View old = stepRail.findViewWithTag("step_" + i);
            if (old != null) {
                int pos = stepRail.indexOfChild(old);
                stepRail.removeViewAt(pos);
                String title = i == 0 ? "Philips" : (i == 1 ? "Govee" : "Terminé");
                String detail = i == 0 ? "Associer la TV"
                        : (i == 1 ? "Ajouter les lumières" : "Tester et démarrer");
                View replacement = stepItem(i, String.valueOf(i + 1), title, detail);
                stepRail.addView(replacement, pos);
            }
        }
    }

    private void showStep(int newStep) {
        if (newStep != 0) {
            stopPairingBridge();
        }

        step = newStep;
        refreshStepRail();
        content.removeAllViews();

        next.setVisibility(View.VISIBLE);
        next.setEnabled(true);
        next.setText("CONTINUER  →");
        back.setText(step == 0 ? "←  FERMER" : "←  RETOUR");

        if (step == 0) showPhilips();
        else if (step == 1) showGovee();
        else showFinish();
    }

    private void showPhilips() {
        boolean paired = isPhilipsPaired();

        addKicker("TV PHILIPS");
        addTitle(paired ? "Philips connectée" : "Connecte ta TV Philips");
        addBody(paired
                ? "L'association est enregistrée localement sur cette TV."
                : "Scanne le QR avec ton téléphone. Quand Philips affiche son PIN, garde la fenêtre ouverte et saisis le code sur le téléphone.");

        if (paired) {
            LinearLayout ready = statusCard(
                    "✓",
                    "Philips prête",
                    "AmbiGovee peut lire les couleurs Ambilight."
            );
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(118));
            rp.setMargins(0, dp(20), 0, dp(16));
            content.addView(ready, rp);

            primary = button("DÉCONNECTER LA TV", 0xff211820, 0xff7d3b50);
            primary.setOnClickListener(v -> showConfirmModal(
                    "Déconnecter la Philips ?",
                    "Les lumières Govee resteront enregistrées.",
                    "DÉCONNECTER",
                    () -> {
                        ConfigStore.disconnectPhilips(this);
                        stopPairingBridge();
                        showStep(0);
                    }
            ));

            content.addView(primary, new LinearLayout.LayoutParams(dp(310), dp(60)));

            message = bodyText("Tout est bon. Continue vers tes lumières Govee.");
            message.setTextColor(0xff6fe3a6);
            content.addView(message);

            next.setEnabled(true);
            primary.setNextFocusDownId(next.getId());
            next.post(() -> next.requestFocus());

            if (pairingBridge != null) {
                content.postDelayed(this::stopPairingBridge, 1800);
            }
            return;
        }

        String localUrl = null;
        String bridgeError = null;

        try {
            ensurePairingBridge();
            localUrl = pairingBridge.url();
        } catch (Exception e) {
            bridgeError = shortError(e);
        }

        if (localUrl != null) {
            LinearLayout qrCard = card(24);
            qrCard.setOrientation(LinearLayout.HORIZONTAL);
            qrCard.setGravity(Gravity.CENTER_VERTICAL);
            qrCard.setPadding(dp(24), dp(22), dp(24), dp(22));

            ImageView qr = new ImageView(this);
            qr.setAdjustViewBounds(true);
            qr.setScaleType(ImageView.ScaleType.FIT_CENTER);

            try {
                qr.setImageBitmap(QrCode.create(localUrl, 520));
            } catch (Exception e) {
                qr.setBackgroundColor(Color.WHITE);
            }

            LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(dp(220), dp(220));
            qp.setMargins(0, 0, dp(30), 0);
            qrCard.addView(qr, qp);

            LinearLayout instructions = new LinearLayout(this);
            instructions.setOrientation(LinearLayout.VERTICAL);
            instructions.addView(text("Sur ton téléphone", 22, 0xfff5f7fb, true));
            instructions.addView(instruction("1", "Scanne le QR code"));
            instructions.addView(instruction("2", "Démarre l'association"));
            instructions.addView(instruction("3", "Entre le PIN affiché par Philips"));

            TextView important = bodyText("Ne ferme pas la fenêtre PIN sur la TV.");
            important.setTextColor(0xfff4c56a);
            instructions.addView(important);

            qrCard.addView(instructions, new LinearLayout.LayoutParams(0, -2, 1));

            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, dp(266));
            cardParams.setMargins(0, dp(20), 0, dp(14));
            content.addView(qrCard, cardParams);

            primary = button("↻  RÉGÉNÉRER LE QR", 0xff151b27, 0xff3c4960);
            primary.setOnClickListener(v -> {
                stopPairingBridge();
                showStep(0);
            });

            content.addView(primary, new LinearLayout.LayoutParams(dp(300), dp(58)));

            String bridgeState = pairingBridge.state();
            String bridgeMessage = pairingBridge.message();

            if (PairingBridgeServer.STATE_ERROR.equals(bridgeState)
                    || PairingBridgeServer.STATE_EXPIRED.equals(bridgeState)) {
                message = bodyText(bridgeMessage);
                message.setTextColor(PairingBridgeServer.STATE_EXPIRED.equals(bridgeState)
                        ? 0xfff4c56a : 0xffff8d9d);
            } else {
                message = bodyText(
                        "Le téléphone sert uniquement à la première association. Il doit être sur le même réseau que la TV."
                );
                message.setTextColor(0xff8c96a8);
            }

            content.addView(message);

            next.setEnabled(false);
            primary.setNextFocusDownId(back.getId());
            primary.post(() -> primary.requestFocus());
        } else {
            LinearLayout error = statusCard(
                    "!",
                    "QR indisponible",
                    bridgeError == null
                            ? "Vérifie la connexion réseau de la TV."
                            : bridgeError
            );

            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(118));
            ep.setMargins(0, dp(20), 0, dp(16));
            content.addView(error, ep);

            primary = button("RÉESSAYER", 0xff2d2252, 0xff7357ff);
            primary.setOnClickListener(v -> {
                stopPairingBridge();
                showStep(0);
            });

            content.addView(primary, new LinearLayout.LayoutParams(dp(260), dp(60)));

            message = bodyText("TV et téléphone doivent être sur le même réseau local.");
            message.setTextColor(0xfff4c56a);
            content.addView(message);

            next.setEnabled(false);
            primary.post(() -> primary.requestFocus());
        }
    }

    private View instruction(String number, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text(number, 12, 0xffe1dbff, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundGradient(0xff34275d, 0xff251d42, 10, 0xff6653a3));
        row.addView(badge, new LinearLayout.LayoutParams(dp(30), dp(30)));

        TextView value = text(label, 15, 0xffd8deea, false);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(0, -2, 1);
        vp.setMargins(dp(12), 0, 0, 0);
        row.addView(value, vp);

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(42));
        rp.setMargins(0, dp(5), 0, 0);
        row.setLayoutParams(rp);
        return row;
    }

    private void showGovee() {
        List<GoveeConfig> configured = ConfigStore.goveeLights(this);
        int enabledCount = ConfigStore.enabledGoveeCount(this);

        addKicker("LUMIÈRES GOVEE");
        addTitle(configured.isEmpty()
                ? "Ajoute tes lumières"
                : configured.size() + " lumière" + (configured.size() > 1 ? "s" : "") + " enregistrée"
                + (configured.size() > 1 ? "s" : ""));
        addBody("Active « Contrôle LAN » dans Govee Home, puis lance la recherche.");

        TvButton firstRow = null;
        TvButton lastRow = null;

        for (GoveeConfig g : configured) {
            String state = g.enabled ? "ACTIF" : "EN PAUSE";
            String icon = g.enabled ? "●" : "○";

            TvButton row = button(
                    icon + "  " + g.displayName() + "\n"
                            + g.positionLabel() + "   •   " + state,
                    g.enabled ? 0xff151c29 : 0xff111620,
                    g.enabled ? 0xff7357ff : 0xff4a566e
            );
            row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            row.setTextSize(14);
            row.setPadding(dp(22), dp(8), dp(18), dp(8));
            row.setOnClickListener(v -> editGovee(g));

            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(72));
            rp.setMargins(0, dp(8), 0, 0);
            content.addView(row, rp);

            if (firstRow == null) firstRow = row;
            if (lastRow != null) {
                lastRow.setNextFocusDownId(row.getId());
                row.setNextFocusUpId(lastRow.getId());
            }
            lastRow = row;
        }

        primary = button("＋  RECHERCHER UNE LUMIÈRE", 0xff2d2252, 0xff7357ff);
        primary.setOnClickListener(v -> scanGovee());

        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(430), dp(62));
        pp.setMargins(0, dp(18), 0, 0);
        content.addView(primary, pp);

        if (lastRow != null) {
            lastRow.setNextFocusDownId(primary.getId());
            primary.setNextFocusUpId(lastRow.getId());
        }
        primary.setNextFocusDownId(next.getId());

        int paused = configured.size() - enabledCount;
        message = bodyText(configured.isEmpty()
                ? "Aucune lumière enregistrée pour l'instant."
                : enabledCount + " active" + (enabledCount > 1 ? "s" : "")
                + (paused > 0 ? "   •   " + paused + " en pause" : ""));
        message.setTextColor(configured.isEmpty() ? 0xff8c96a8 : 0xff6fe3a6);
        content.addView(message);

        next.setEnabled(!configured.isEmpty());

        TvButton focusTarget = firstRow != null ? firstRow : primary;
        focusTarget.post(() -> focusTarget.requestFocus());
    }

    private void scanGovee() {
        primary.setEnabled(false);
        message.setText("Recherche des appareils Govee sur le réseau…");
        message.setTextColor(0xffd4caff);
        stopService(new Intent(this, AmbiService.class));

        executor.execute(() -> {
            try {
                Thread.sleep(220);
                List<GoveeLan.Device> devices = GoveeLan.discoverDevices(this, 3200);
                runOnUiThread(() -> showGoveeDevices(devices));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    primary.setEnabled(true);
                    message.setText("Recherche impossible : " + shortError(e));
                    message.setTextColor(0xffff8d9d);
                    primary.requestFocus();
                });
            }
        });
    }

    private void showGoveeDevices(List<GoveeLan.Device> devices) {
        primary.setEnabled(true);

        if (devices == null || devices.isEmpty()) {
            message.setText("Aucun Govee LAN trouvé. Vérifie « Contrôle LAN » et le réseau.");
            message.setTextColor(0xfff4c56a);
            primary.requestFocus();
            return;
        }

        List<GoveeConfig> existing = ConfigStore.goveeLights(this);
        String[] labels = new String[devices.size()];

        for (int i = 0; i < devices.size(); i++) {
            GoveeLan.Device d = devices.get(i);
            boolean already = false;

            for (GoveeConfig g : existing) {
                if (g.identity().equals(d.identity())) {
                    already = true;
                    break;
                }
            }

            labels[i] = d.toString() + (already ? "   •   déjà ajouté" : "");
        }

        showChoiceModal(
                "Choisis une lumière",
                "Appareils Govee détectés sur le réseau local",
                labels,
                which -> choosePosition(devices.get(which))
        );
    }

    private void choosePosition(GoveeLan.Device device) {
        final String[] labels = {
                "Pièce entière / plafond",
                "Gauche de la TV",
                "Au-dessus de la TV",
                "Droite de la TV",
                "Sous la TV"
        };

        final String[] values = {
                GoveeConfig.POSITION_ROOM,
                GoveeConfig.POSITION_LEFT,
                GoveeConfig.POSITION_TOP,
                GoveeConfig.POSITION_RIGHT,
                GoveeConfig.POSITION_BOTTOM
        };

        showChoiceModal(
                "Position de la lumière",
                device.toString(),
                labels,
                which -> {
                    GoveeConfig cfg = new GoveeConfig(
                            device.ip,
                            device.device,
                            device.sku,
                            values[which],
                            "",
                            true
                    );

                    ConfigStore.upsertGovee(this, cfg);
                    showStep(1);
                    message.setText(cfg.displayName() + " ajoutée ✓");
                    message.setTextColor(0xff6fe3a6);
                }
        );
    }

    private void editGovee(GoveeConfig g) {
        String toggle = g.enabled ? "Mettre en pause" : "Réactiver";

        String[] labels = {
                toggle,
                "Pièce entière / plafond",
                "Gauche de la TV",
                "Au-dessus de la TV",
                "Droite de la TV",
                "Sous la TV",
                "Supprimer cet appareil"
        };

        String[] values = {
                GoveeConfig.POSITION_ROOM,
                GoveeConfig.POSITION_LEFT,
                GoveeConfig.POSITION_TOP,
                GoveeConfig.POSITION_RIGHT,
                GoveeConfig.POSITION_BOTTOM
        };

        showChoiceModal(
                g.displayName(),
                "État, position ou suppression",
                labels,
                which -> {
                    if (which == 0) {
                        ConfigStore.setGoveeEnabled(this, g.identity(), !g.enabled);
                        showStep(1);
                        message.setText(g.enabled
                                ? g.displayName() + " mise en pause."
                                : g.displayName() + " réactivée ✓");
                        return;
                    }

                    if (which == 6) {
                        showConfirmModal(
                                "Supprimer " + g.displayName() + " ?",
                                "La lumière pourra être ajoutée à nouveau plus tard.",
                                "SUPPRIMER",
                                () -> {
                                    ConfigStore.removeGovee(this, g.identity());
                                    showStep(1);
                                    message.setText(g.displayName() + " supprimée.");
                                }
                        );
                        return;
                    }

                    ConfigStore.upsertGovee(this, g.withPosition(values[which - 1]));
                    showStep(1);
                    message.setText("Position mise à jour ✓");
                    message.setTextColor(0xff6fe3a6);
                }
        );
    }

    private void showFinish() {
        next.setVisibility(View.GONE);

        addKicker("TERMINÉ");
        addTitle("AmbiGovee est prêt");
        addBody("Teste rapidement la connexion, puis démarre l'application.");

        LinearLayout results = card(22);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(dp(22), dp(18), dp(22), dp(18));

        TextView philipsResult = text("Philips   •   prêt à tester", 17, 0xffa8b0bf, true);
        TextView goveeResult = text("Govee     •   prêt à tester", 17, 0xffa8b0bf, true);

        results.addView(philipsResult);

        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.setMargins(0, dp(12), 0, 0);
        results.addView(goveeResult, gp);

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(126));
        rp.setMargins(0, dp(20), 0, dp(18));
        content.addView(results, rp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        primary = button("TESTER", 0xff151b27, 0xff3c4960);
        TvButton finish = button("DÉMARRER AMBIGOVEE", 0xff2d2252, 0xff7357ff);

        boolean configured = ConfigStore.isConfigured(this);
        primary.setEnabled(configured);
        finish.setEnabled(configured);

        primary.setOnClickListener(v -> runCompatibilityTest(philipsResult, goveeResult));
        finish.setOnClickListener(v -> finishSetup());

        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(68), 1);
        ap.setMargins(dp(5), 0, dp(5), 0);
        actions.addView(primary, ap);
        actions.addView(finish, ap);
        content.addView(actions);

        TextView safety = bodyText("AmbiGovee ne rallume jamais une lumière que tu as éteinte.");
        safety.setTextColor(0xff748094);
        content.addView(safety);

        primary.setNextFocusRightId(finish.getId());
        finish.setNextFocusLeftId(primary.getId());
        primary.post(() -> primary.requestFocus());
    }

    private void runCompatibilityTest(TextView philipsResult, TextView goveeResult) {
        primary.setEnabled(false);
        philipsResult.setText("Philips   •   lecture Ambilight…");
        goveeResult.setText("Govee     •   vérification LAN…");

        executor.execute(() -> {
            boolean philipsOk = false;
            String philipsText;
            GoveeLan temp = null;

            int total = ConfigStore.enabledGoveeCount(this);
            int ok = 0;

            try {
                JSONObject measured = new PhilipsClient(this).getMeasured();
                philipsOk = measured.optJSONObject("layer1") != null;
                philipsText = philipsOk
                        ? "Philips   •   Ambilight OK ✓"
                        : "Philips   •   flux Ambilight non détecté";
            } catch (Exception e) {
                philipsText = "Philips   •   échec : " + shortError(e);
            }

            try {
                temp = new GoveeLan(this);
                for (GoveeConfig cfg : ConfigStore.goveeLights(this)) {
                    if (!cfg.enabled) continue;
                    GoveeLan.GoveeState state = temp.queryStatus(cfg);
                    if (state != null && state.colorCapable) ok++;
                }
            } catch (Exception ignored) {
            } finally {
                if (temp != null) temp.close();
            }

            final int goveeOk = ok;
            final boolean pOk = philipsOk;
            final String pText = philipsText;
            final String gText = goveeOk == total && total > 0
                    ? "Govee     •   " + goveeOk + "/" + total + " OK ✓"
                    : "Govee     •   " + goveeOk + "/" + total + " disponibles";

            runOnUiThread(() -> {
                philipsResult.setText(pText);
                philipsResult.setTextColor(pOk ? 0xff6fe3a6 : 0xffff8d9d);

                goveeResult.setText(gText);
                goveeResult.setTextColor(
                        goveeOk == total && total > 0 ? 0xff6fe3a6 : 0xfff4c56a
                );

                primary.setEnabled(true);
                primary.requestFocus();
            });
        });
    }

    private void finishSetup() {
        if (!ConfigStore.isConfigured(this)) return;

        ConfigStore.markSetupCompleted(this);

        Intent service = new Intent(this, AmbiService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
        else startService(service);

        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private void showChoiceModal(
            String title,
            String subtitle,
            String[] labels,
            ChoiceHandler handler
    ) {
        Dialog dialog = new Dialog(this);
        dialog.setCancelable(true);

        LinearLayout panel = modalPanel(title, subtitle);
        TvButton first = null;
        TvButton previous = null;

        for (int i = 0; i < labels.length; i++) {
            final int index = i;

            TvButton option = button(labels[i], 0xff151b27, 0xff7357ff);
            option.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            option.setPadding(dp(20), 0, dp(18), 0);
            option.setOnClickListener(v -> {
                dialog.dismiss();
                handler.choose(index);
            });

            LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, dp(56));
            op.setMargins(0, dp(7), 0, 0);
            panel.addView(option, op);

            if (first == null) first = option;
            if (previous != null) {
                previous.setNextFocusDownId(option.getId());
                option.setNextFocusUpId(previous.getId());
            }
            previous = option;
        }

        TvButton cancel = button("ANNULER", 0xff111722, 0xff3c4960);
        cancel.setOnClickListener(v -> dialog.dismiss());

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(210), dp(54));
        cp.setMargins(0, dp(14), 0, 0);
        panel.addView(cancel, cp);

        if (previous != null) {
            previous.setNextFocusDownId(cancel.getId());
            cancel.setNextFocusUpId(previous.getId());
        }

        dialog.setContentView(panel);
        styleDialog(dialog, dp(760));

        TvButton focus = first != null ? first : cancel;
        focus.post(() -> focus.requestFocus());
    }

    private void showConfirmModal(
            String title,
            String subtitle,
            String confirmLabel,
            Runnable confirm
    ) {
        Dialog dialog = new Dialog(this);
        dialog.setCancelable(true);

        LinearLayout panel = modalPanel(title, subtitle);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        TvButton cancel = button("ANNULER", 0xff151b27, 0xff3c4960);
        TvButton yes = button(confirmLabel, 0xff3a1f2b, 0xff8e3f58);

        cancel.setOnClickListener(v -> dialog.dismiss());
        yes.setOnClickListener(v -> {
            dialog.dismiss();
            confirm.run();
        });

        cancel.setNextFocusRightId(yes.getId());
        yes.setNextFocusLeftId(cancel.getId());

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(60), 1);
        bp.setMargins(dp(4), dp(18), dp(4), 0);
        actions.addView(cancel, bp);
        actions.addView(yes, bp);
        panel.addView(actions);

        dialog.setContentView(panel);
        styleDialog(dialog, dp(720));
        cancel.post(() -> cancel.requestFocus());
    }

    private LinearLayout modalPanel(String title, String subtitle) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(26), dp(24), dp(26), dp(24));
        panel.setBackground(roundGradient(0xff151b27, 0xff0e131d, 24, 0xff384257));

        panel.addView(text(title, 24, 0xfff5f7fb, true));

        if (subtitle != null && !subtitle.trim().isEmpty()) {
            TextView sub = text(subtitle, 13, 0xff8c96a8, false);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
            sp.setMargins(0, dp(7), 0, dp(8));
            panel.addView(sub, sp);
        }

        return panel;
    }

    private void styleDialog(Dialog dialog, int width) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.72f;
            window.setAttributes(attrs);
        }

        dialog.show();

        window = dialog.getWindow();
        if (window != null) {
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void ensurePairingBridge() throws Exception {
        if (pairingBridge != null) return;
        pairingBridge = PairingBridgeServer.start(this);
    }

    private void stopPairingBridge() {
        if (pairingBridge != null) {
            try {
                pairingBridge.close();
            } catch (Exception ignored) {}
            pairingBridge = null;
        }
    }

    private boolean isPhilipsPaired() {
        return !ConfigStore.tvUser(this).isEmpty()
                && !ConfigStore.tvKey(this).isEmpty();
    }

    private LinearLayout statusCard(String icon, String heading, String detail) {
        LinearLayout box = card(20);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(22), dp(16), dp(22), dp(16));

        TextView badge = text(icon, 20, 0xffd6ceff, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundGradient(0xff2c2348, 0xff211a38, 15, 0xff6653a3));
        box.addView(badge, new LinearLayout.LayoutParams(dp(66), dp(66)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(20), 0, 0, 0);
        texts.addView(text(heading, 19, 0xfff5f7fb, true));

        TextView detailView = text(detail, 13, 0xff8c96a8, false);
        LinearLayout.LayoutParams dpv = new LinearLayout.LayoutParams(-1, -2);
        dpv.setMargins(0, dp(6), 0, 0);
        texts.addView(detailView, dpv);

        box.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        return box;
    }

    private LinearLayout card(int radius) {
        LinearLayout view = new LinearLayout(this);
        view.setBackground(roundGradient(0xff121722, 0xff0e131d, radius, 0xff252d3d));
        return view;
    }

    private void addKicker(String value) {
        TextView t = text(value, 12, 0xff8d7fff, true);
        t.setLetterSpacing(0.12f);
        content.addView(t);
    }

    private void addTitle(String value) {
        TextView t = text(value, 31, 0xfff5f7fb, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(8), 0, 0);
        content.addView(t, p);
    }

    private void addBody(String value) {
        content.addView(bodyText(value));
    }

    private TextView bodyText(String value) {
        TextView t = text(value, 15, 0xff9da6b7, false);
        t.setLineSpacing(0, 1.12f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(10), 0, 0);
        t.setLayoutParams(p);
        return t;
    }

    private TvButton button(String label, int normal, int focus) {
        TvButton button = new TvButton(this);
        button.setId(View.generateViewId());
        button.setText(label);
        button.colors(normal, focus);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable pageBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xff06080d, 0xff090d15, 0xff151020}
        );
    }

    private GradientDrawable roundGradient(int a, int b, int radius, int stroke) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{a, b}
        );
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), stroke);
        return g;
    }

    private String shortError(Exception e) {
        String value = e == null ? null : e.getMessage();

        if (value == null || value.trim().isEmpty()) {
            return e == null ? "Erreur inconnue" : e.getClass().getSimpleName();
        }

        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 120 ? value.substring(0, 120) + "…" : value;
    }

    @Override
    protected void onDestroy() {
        stopPairingBridge();
        executor.shutdownNow();

        if (ConfigStore.isConfigured(this)) {
            try {
                Intent service = new Intent(this, AmbiService.class);
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
                else startService(service);
            } catch (Exception ignored) {}
        }

        super.onDestroy();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
