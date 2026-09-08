package fr.ambigovee.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Assistant de configuration simplifié pour Android TV.
 *
 * 3 étapes seulement : Philips -> Govee -> Terminé.
 * La vérification de mise à jour fonctionne aussi depuis cet écran.
 */
public class SetupActivity extends Activity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private int step = 0;
    private LinearLayout content;
    private TextView stepLabel;
    private TextView message;
    private TvButton primary;
    private TvButton back;
    private TvButton next;
    private PhilipsPairer.Pending pending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ConfigStore.seedDefaults(this);
        stopService(new Intent(this, AmbiService.class));

        buildShell();

        int initialStep = Math.max(0, Math.min(2,
                getIntent().getIntExtra("start_step", 0)));
        showStep(initialStep);

        // Important : même si l'utilisateur n'a pas encore fini la configuration,
        // AmbiGovee peut maintenant recevoir les futures mises à jour.
        UpdateManager.checkOnLaunch(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UpdateManager.onActivityResumed(this);
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(54), dp(26), dp(54), dp(24));
        root.setBackground(pageBackground());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.addView(text("AmbiGovee", 32, Color.WHITE, true));
        brand.addView(text("Configuration rapide", 15, 0xff929caf, false));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(64), 1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        stepLabel = text("1 / 3", 15, 0xffcbbfff, true);
        stepLabel.setGravity(Gravity.RIGHT);
        right.addView(stepLabel);

        TextView version = text("v" + BuildConfig.VERSION_NAME, 12, 0xff727c91, false);
        version.setGravity(Gravity.RIGHT);
        right.addView(version);

        header.addView(right, new LinearLayout.LayoutParams(dp(150), dp(64)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.setMargins(0, dp(12), 0, dp(12));
        root.addView(scroll, scrollParams);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);

        back = button("←  RETOUR", 0xff171d2a, 0xff46526b);
        back.setOnClickListener(v -> {
            if (step == 0) finish();
            else showStep(step - 1);
        });

        next = button("CONTINUER  →", 0xff35255f, 0xff7655ff);
        next.setOnClickListener(v -> showStep(Math.min(2, step + 1)));

        nav.addView(back, new LinearLayout.LayoutParams(dp(220), dp(58)));
        nav.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        nav.addView(next, new LinearLayout.LayoutParams(dp(270), dp(58)));

        back.setNextFocusRightId(next.getId());
        next.setNextFocusLeftId(back.getId());

        root.addView(nav);
        setContentView(root);
    }

    private void showStep(int newStep) {
        step = newStep;
        content.removeAllViews();
        stepLabel.setText((step + 1) + " / 3");

        next.setVisibility(View.VISIBLE);
        next.setEnabled(true);
        next.setText("CONTINUER  →");
        back.setText(step == 0 ? "←  FERMER" : "←  RETOUR");

        if (step == 0) showPhilips();
        else if (step == 1) showGovee();
        else showFinish();
    }

    private void showPhilips() {
        boolean paired = !ConfigStore.tvUser(this).isEmpty()
                && !ConfigStore.tvKey(this).isEmpty();

        content.addView(kicker("1  •  PHILIPS AMBILIGHT"));
        content.addView(title(paired ? "TV Philips connectée ✓" : "Connecte ta TV Philips"));
        content.addView(body(
                paired
                        ? "AmbiGovee est déjà autorisé à lire les couleurs Ambilight."
                        : "Appuie sur le bouton ci-dessous. La TV affiche un PIN : entre seulement ce code."
        ));

        LinearLayout status = statusCard(
                paired ? "✓" : "TV",
                paired ? "Philips prête" : "En attente d'association",
                paired ? "Tu peux continuer vers les lumières Govee."
                        : "Le PIN reste sur la TV quelques instants."
        );
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, dp(132));
        statusParams.setMargins(0, dp(22), 0, dp(18));
        content.addView(status, statusParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        primary = button(
                paired ? "RÉASSOCIER LA TV" : "CONNECTER LA TV",
                0xff35255f,
                0xff7655ff
        );
        primary.setOnClickListener(v -> beginPair());
        actions.addView(primary, new LinearLayout.LayoutParams(dp(390), dp(62)));

        if (paired) {
            TvButton disconnect = button("DÉCONNECTER", 0xff211820, 0xff7d3b50);
            disconnect.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Déconnecter la Philips ?")
                    .setMessage("Les lumières Govee resteront enregistrées.")
                    .setNegativeButton("ANNULER", null)
                    .setPositiveButton("DÉCONNECTER", (d, w) -> {
                        ConfigStore.disconnectPhilips(this);
                        showStep(0);
                    })
                    .show());

            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(dp(240), dp(62));
            dp.setMargins(dp(12), 0, 0, 0);
            actions.addView(disconnect, dp);

            primary.setNextFocusRightId(disconnect.getId());
            disconnect.setNextFocusLeftId(primary.getId());
            disconnect.setNextFocusDownId(next.getId());
        }

        content.addView(actions);

        message = body(paired
                ? "Tout est bon côté Philips."
                : "Aucune adresse IP, clé ou identifiant à saisir à la main.");
        message.setTextColor(paired ? 0xff79dfaa : 0xff9da7ba);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.setMargins(0, dp(16), 0, 0);
        content.addView(message, mp);

        next.setEnabled(paired);
        primary.setNextFocusDownId(next.getId());
        primary.post(() -> primary.requestFocus());
    }

    private void beginPair() {
        primary.setEnabled(false);
        message.setText("Demande envoyée… regarde le PIN affiché par la TV.");
        message.setTextColor(0xffcbbfff);

        executor.execute(() -> {
            try {
                PhilipsPairer pairer = new PhilipsPairer();
                pending = pairer.begin();
                runOnUiThread(() -> askPin(pairer));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    primary.setEnabled(true);
                    message.setText("Impossible de démarrer l'association : " + shortError(e));
                    message.setTextColor(0xffff8c8c);
                    primary.requestFocus();
                });
            }
        });
    }

    private void askPin(PhilipsPairer pairer) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0xff747f94);
        input.setHint("PIN affiché sur la TV");
        input.setSingleLine(true);
        input.setTextSize(26);
        input.setPadding(dp(18), dp(14), dp(18), dp(14));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("PIN Philips")
                .setMessage("Recopie le code affiché par la TV.")
                .setView(input)
                .setNegativeButton("ANNULER", (d, w) -> {
                    primary.setEnabled(true);
                    primary.requestFocus();
                })
                .setPositiveButton("CONNECTER", null)
                .create();

        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String pin = input.getText().toString().trim();
                if (pin.length() < 4) {
                    input.setError("Entre le PIN complet");
                    input.requestFocus();
                    return;
                }

                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                message.setText("Validation de la TV…");
                message.setTextColor(0xffcbbfff);

                executor.execute(() -> {
                    try {
                        PhilipsPairer.Result result = pairer.grant(pending, pin);
                        ConfigStore.savePhilips(this, result.ip, result.user, result.key);

                        runOnUiThread(() -> {
                            dialog.dismiss();
                            // Passage automatique à l'étape suivante : moins de clics à la télécommande.
                            showStep(1);
                        });
                    } catch (Exception e) {
                        String error = shortError(e);
                        runOnUiThread(() -> {
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            input.setError("Association refusée");
                            input.requestFocus();
                            message.setText("Échec Philips : " + error);
                            message.setTextColor(0xffff8c8c);
                        });
                    }
                });
            });

            input.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                );
            }
        });

        dialog.show();
    }

    private void showGovee() {
        List<GoveeConfig> configured = ConfigStore.goveeLights(this);
        int enabledCount = ConfigStore.enabledGoveeCount(this);

        content.addView(kicker("2  •  LUMIÈRES GOVEE"));
        content.addView(title(configured.isEmpty()
                ? "Ajoute tes lumières"
                : configured.size() + " lumière" + (configured.size() > 1 ? "s" : "") + " enregistrée" + (configured.size() > 1 ? "s" : "")));
        content.addView(body(
                "Active Contrôle LAN dans Govee Home, puis lance la recherche. AmbiGovee trouve les appareils automatiquement."
        ));

        TvButton firstRow = null;
        TvButton lastRow = null;

        for (GoveeConfig g : configured) {
            String state = g.enabled ? "ACTIF" : "PAUSE";
            String icon = g.enabled ? "●" : "○";

            TvButton row = button(
                    icon + "  " + g.displayName() + "   •   " + g.positionLabel() + "   •   " + state,
                    g.enabled ? 0xff151c29 : 0xff111620,
                    g.enabled ? 0xff7655ff : 0xff566078
            );
            row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            row.setPadding(dp(22), 0, dp(18), 0);
            row.setOnClickListener(v -> editGovee(g));

            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(58));
            rp.setMargins(0, dp(7), 0, 0);
            content.addView(row, rp);

            if (firstRow == null) firstRow = row;
            if (lastRow != null) {
                lastRow.setNextFocusDownId(row.getId());
                row.setNextFocusUpId(lastRow.getId());
            }
            lastRow = row;
        }

        primary = button("＋  RECHERCHER UNE LUMIÈRE", 0xff35255f, 0xff7655ff);
        primary.setOnClickListener(v -> scanGovee());

        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(500), dp(62));
        pp.setMargins(0, dp(18), 0, 0);
        content.addView(primary, pp);

        if (lastRow != null) {
            lastRow.setNextFocusDownId(primary.getId());
            primary.setNextFocusUpId(lastRow.getId());
        }
        primary.setNextFocusDownId(next.getId());

        int paused = configured.size() - enabledCount;
        message = body(configured.isEmpty()
                ? "Aucune lumière enregistrée pour l'instant."
                : enabledCount + " active" + (enabledCount > 1 ? "s" : "")
                    + (paused > 0 ? " • " + paused + " en pause" : "") + ".");
        message.setTextColor(configured.isEmpty() ? 0xff9da7ba : 0xff79dfaa);
        content.addView(message);

        next.setEnabled(!configured.isEmpty());
        TvButton focusTarget = firstRow != null ? firstRow : primary;
        focusTarget.post(() -> focusTarget.requestFocus());
    }

    private void scanGovee() {
        primary.setEnabled(false);
        message.setText("Recherche sur le réseau local…");
        message.setTextColor(0xffcbbfff);
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
                    message.setTextColor(0xffff8c8c);
                    primary.requestFocus();
                });
            }
        });
    }

    private void showGoveeDevices(List<GoveeLan.Device> devices) {
        primary.setEnabled(true);

        if (devices == null || devices.isEmpty()) {
            message.setText("Aucun Govee LAN trouvé. Vérifie Contrôle LAN et le Wi-Fi.");
            message.setTextColor(0xffffc56d);
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
            labels[i] = d.toString() + (already ? "   ✓ déjà ajouté" : "");
        }

        new AlertDialog.Builder(this)
                .setTitle("Choisis une lumière")
                .setItems(labels, (dialog, which) -> choosePosition(devices.get(which)))
                .setNegativeButton("ANNULER", null)
                .show();
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

        new AlertDialog.Builder(this)
                .setTitle("Où se trouve la lumière ?")
                .setItems(labels, (dialog, which) -> {
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
                    message.setTextColor(0xff79dfaa);
                })
                .setNegativeButton("ANNULER", null)
                .show();
    }

    private void editGovee(GoveeConfig g) {
        String toggle = g.enabled ? "METTRE EN PAUSE" : "RÉACTIVER";
        String[] labels = {
                toggle,
                "Pièce entière / plafond",
                "Gauche de la TV",
                "Au-dessus de la TV",
                "Droite de la TV",
                "Sous la TV",
                "SUPPRIMER CET APPAREIL"
        };

        String[] values = {
                GoveeConfig.POSITION_ROOM,
                GoveeConfig.POSITION_LEFT,
                GoveeConfig.POSITION_TOP,
                GoveeConfig.POSITION_RIGHT,
                GoveeConfig.POSITION_BOTTOM
        };

        new AlertDialog.Builder(this)
                .setTitle(g.displayName())
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        ConfigStore.setGoveeEnabled(this, g.identity(), !g.enabled);
                        showStep(1);
                        message.setText(g.enabled
                                ? g.displayName() + " mise en pause."
                                : g.displayName() + " réactivée ✓");
                    } else if (which == 6) {
                        ConfigStore.removeGovee(this, g.identity());
                        showStep(1);
                        message.setText(g.displayName() + " supprimée.");
                    } else {
                        ConfigStore.upsertGovee(this, g.withPosition(values[which - 1]));
                        showStep(1);
                        message.setText("Position mise à jour ✓");
                    }
                })
                .setNegativeButton("ANNULER", null)
                .show();
    }

    private void showFinish() {
        next.setVisibility(View.GONE);

        content.addView(kicker("3  •  TERMINÉ"));
        content.addView(title("AmbiGovee est prêt"));
        content.addView(body(
                "On peut faire un test rapide avant de démarrer. La synchronisation n'allume jamais une lumière Govee qui est éteinte."
        ));

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        results.setPadding(dp(22), dp(18), dp(22), dp(18));
        results.setBackground(roundGradient(0xff151a27, 0xff101521, 20, 0xff293144));

        TextView philipsResult = text("Philips  •  prêt à tester", 18, 0xffaab2c4, true);
        TextView goveeResult = text("Govee  •  prêt à tester", 18, 0xffaab2c4, true);
        results.addView(philipsResult);

        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2);
        gp.setMargins(0, dp(10), 0, 0);
        results.addView(goveeResult, gp);

        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(130));
        rp.setMargins(0, dp(22), 0, dp(18));
        content.addView(results, rp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        primary = button("TESTER", 0xff171d2a, 0xff46526b);
        TvButton finish = button("DÉMARRER AMBIGOVEE", 0xff35255f, 0xff7655ff);

        boolean configured = ConfigStore.isConfigured(this);
        primary.setEnabled(configured);
        finish.setEnabled(configured);

        primary.setOnClickListener(v -> runCompatibilityTest(philipsResult, goveeResult));
        finish.setOnClickListener(v -> finishSetup());

        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(64), 1);
        a.setMargins(dp(6), 0, dp(6), 0);
        actions.addView(primary, a);
        actions.addView(finish, a);
        content.addView(actions);

        primary.setNextFocusRightId(finish.getId());
        finish.setNextFocusLeftId(primary.getId());
        primary.post(() -> primary.requestFocus());
    }

    private void runCompatibilityTest(TextView philipsResult, TextView goveeResult) {
        primary.setEnabled(false);
        philipsResult.setText("Philips  •  lecture Ambilight…");
        goveeResult.setText("Govee  •  vérification LAN…");

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
                        ? "Philips  •  Ambilight OK ✓"
                        : "Philips  •  flux Ambilight non détecté";
            } catch (Exception e) {
                philipsText = "Philips  •  échec : " + shortError(e);
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
                    ? "Govee  •  " + goveeOk + "/" + total + " OK ✓"
                    : "Govee  •  " + goveeOk + "/" + total + " disponibles";

            runOnUiThread(() -> {
                philipsResult.setText(pText);
                philipsResult.setTextColor(pOk ? 0xff79dfaa : 0xffff8c8c);
                goveeResult.setText(gText);
                goveeResult.setTextColor(
                        goveeOk == total && total > 0 ? 0xff79dfaa : 0xffffc56d
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

    private LinearLayout statusCard(String icon, String heading, String detail) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(22), dp(16), dp(22), dp(16));
        box.setBackground(roundGradient(0xff151a27, 0xff101521, 20, 0xff293144));

        TextView badge = text(icon, 20, 0xffc7bbff, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(roundGradient(0xff282038, 0xff1e1930, 16, 0xff4b3e6c));
        box.addView(badge, new LinearLayout.LayoutParams(dp(70), dp(70)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(20), 0, 0, 0);
        texts.addView(text(heading, 20, Color.WHITE, true));
        TextView detailView = text(detail, 14, 0xff949db1, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.setMargins(0, dp(6), 0, 0);
        texts.addView(detailView, detailParams);

        box.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        return box;
    }

    private TextView kicker(String value) {
        TextView t = text(value, 13, 0xff9e8fff, true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private TextView title(String value) {
        TextView t = text(value, 30, Color.WHITE, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(9), 0, 0);
        t.setLayoutParams(p);
        return t;
    }

    private TextView body(String value) {
        TextView t = text(value, 16, 0xffa5aec1, false);
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
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private GradientDrawable pageBackground() {
        return new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xff06080d, 0xff0b0f18, 0xff160d24}
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
        String value = e.getMessage();
        if (value == null || value.trim().isEmpty()) {
            return e.getClass().getSimpleName();
        }
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 120 ? value.substring(0, 120) + "…" : value;
    }

    @Override
    protected void onDestroy() {
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
