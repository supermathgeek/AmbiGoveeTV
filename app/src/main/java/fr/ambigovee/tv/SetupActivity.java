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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SetupActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int step = 0;
    private LinearLayout root, content;
    private TextView stepLabel, message;
    private TvButton primary, back, next;
    private PhilipsPairer.Pending pending;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfigStore.seedDefaults(this);
        stopService(new Intent(this, AmbiService.class));
        buildShell();
        int initialStep = Math.max(0, Math.min(3, getIntent().getIntExtra("start_step", 0)));
        showStep(initialStep);
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(58), dp(30), dp(58), dp(26));
        root.setBackground(pageBackground());

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        titleBox.addView(text("Configuration AmbiGovee", 31, Color.WHITE, true));
        titleBox.addView(text("Une étape à la fois, pensée pour la télécommande.", 15, 0xff9fa8bd, false));
        header.addView(titleBox, new LinearLayout.LayoutParams(0, dp(68), 1));
        stepLabel = text("ÉTAPE 1 / 4", 14, 0xffc7bbff, true);
        stepLabel.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        header.addView(stepLabel, new LinearLayout.LayoutParams(dp(190), dp(68)));
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, 0, 1);
        cp.setMargins(0, dp(12), 0, dp(12));
        root.addView(scroll, cp);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        back = button("←  RETOUR", 0xff1a2030, 0xff4c5a78);
        back.setOnClickListener(v -> { if (step == 0) finish(); else showStep(step - 1); });
        next = button("CONTINUER  →", 0xff35255f, 0xff7655ff);
        next.setOnClickListener(v -> showStep(Math.min(3, step + 1)));
        nav.addView(back, new LinearLayout.LayoutParams(dp(210), dp(56)));
        nav.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        nav.addView(next, new LinearLayout.LayoutParams(dp(260), dp(56)));
        back.setNextFocusRightId(next.getId());
        next.setNextFocusLeftId(back.getId());
        root.addView(nav);
        setContentView(root);
    }

    private void showStep(int newStep) {
        step = newStep;
        content.removeAllViews();
        stepLabel.setText("ÉTAPE " + (step + 1) + " / 4");
        next.setVisibility(View.VISIBLE);
        next.setEnabled(true);
        back.setText(step == 0 ? "←  FERMER" : "←  RETOUR");
        if (step == 0) showWelcome();
        else if (step == 1) showPhilips();
        else if (step == 2) showGovee();
        else showFinish();
    }

    private void showWelcome() {
        content.addView(kicker("BIENVENUE"));
        content.addView(title("On connecte la TV, puis toutes tes lumières."));
        content.addView(body("AmbiGovee fonctionne en local : il lit les couleurs Ambilight de la Philips et les envoie aux appareils Govee compatibles LAN. Aucune image de la TV n'est capturée."));

        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams c = new LinearLayout.LayoutParams(0, dp(166), 1);
        c.setMargins(dp(6), dp(20), dp(6), 0);
        cards.addView(infoCard("1", "Philips", "Association par le PIN affiché directement sur la TV."), c);
        cards.addView(infoCard("2", "Govee", "Scan automatique. Tu peux ajouter plusieurs lampes."), c);
        cards.addView(infoCard("3", "Position", "Chaque lampe suit la zone Ambilight correspondant à sa place."), c);
        content.addView(cards);

        message = body(ConfigStore.isConfigured(this)
                ? "Ta configuration actuelle est conservée. Tu peux ajouter, déplacer ou retirer des lampes."
                : "Tu n'auras pas à copier de clé Govee ni d'adresse MAC à la main.");
        message.setTextColor(ConfigStore.isConfigured(this) ? 0xff79dfaa : 0xffa8b0c3);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.setMargins(0, dp(22), 0, 0);
        content.addView(message, mp);
        next.setText("COMMENCER  →");
        next.post(() -> next.requestFocus());
    }

    private void showPhilips() {
        next.setText("CONTINUER  →");
        boolean paired = !ConfigStore.tvUser(this).isEmpty() && !ConfigStore.tvKey(this).isEmpty();
        String localIp = PhilipsClient.localIpv4();
        content.addView(kicker("PHILIPS AMBILIGHT"));
        content.addView(title(paired ? "TV déjà associée ✓" : "Associe AmbiGovee à la TV"));
        content.addView(body("Appuie sur Associer : la Philips affiche un PIN. Tu saisis uniquement ce PIN et AmbiGovee récupère le reste automatiquement."));

        LinearLayout statusCard = infoCard("TV", paired ? "Philips associée" : "Philips à associer",
                "Adresse détectée : " + (localIp == null ? "recherche en cours" : localIp));
        LinearLayout.LayoutParams sc = new LinearLayout.LayoutParams(-1, dp(145));
        sc.setMargins(0, dp(20), 0, dp(14));
        content.addView(statusCard, sc);

        LinearLayout philipsActions = new LinearLayout(this);
        philipsActions.setOrientation(LinearLayout.HORIZONTAL);
        primary = button(paired ? "RÉASSOCIER LA PHILIPS" : "ASSOCIER LA PHILIPS", 0xff35255f, 0xff7655ff);
        primary.setOnClickListener(v -> beginPair());
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(dp(430), dp(60));
        philipsActions.addView(primary, pa);
        TvButton disconnect = null;
        if (paired) {
            disconnect = button("DÉCONNECTER", 0xff241a22, 0xff8b4058);
            disconnect.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Déconnecter la Philips ?")
                    .setMessage("Les lampes Govee restent enregistrées. Il faudra réassocier la TV pour relancer la synchronisation.")
                    .setNegativeButton("ANNULER", null)
                    .setPositiveButton("DÉCONNECTER", (d,w) -> { ConfigStore.disconnectPhilips(this); showStep(1); })
                    .show());
            LinearLayout.LayoutParams da = new LinearLayout.LayoutParams(dp(250), dp(60)); da.setMargins(dp(12),0,0,0);
            philipsActions.addView(disconnect, da);
            primary.setNextFocusRightId(disconnect.getId());
            disconnect.setNextFocusLeftId(primary.getId());
            disconnect.setNextFocusDownId(next.getId());
        }
        content.addView(philipsActions);
        message = body(paired ? "Philips connectée. Tu peux la réassocier ou la déconnecter sans supprimer tes lampes." : "Le PIN est la seule action demandée pour la Philips.");
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.setMargins(0, dp(16), 0, 0);
        content.addView(message, mp);
        next.setEnabled(paired);
        primary.setNextFocusDownId(next.getId());
        primary.post(() -> primary.requestFocus());
    }

    private void beginPair() {
        primary.setEnabled(false);
        message.setText("Demande d'association en cours… regarde la TV.");
        executor.execute(() -> {
            try {
                PhilipsPairer pairer = new PhilipsPairer();
                pending = pairer.begin();
                runOnUiThread(() -> askPin(pairer));
            } catch (Exception e) {
                runOnUiThread(() -> { primary.setEnabled(true); message.setText("Impossible de joindre JointSpace : " + shortError(e)); });
            }
        });
    }

    private void askPin(PhilipsPairer pairer) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setTextColor(Color.WHITE); input.setHintTextColor(0xff7f899f);
        input.setHint("PIN affiché sur la TV"); input.setSingleLine(true); input.setTextSize(24);
        input.setPadding(dp(18), dp(14), dp(18), dp(14));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Code Philips")
                .setMessage("Entre le PIN affiché par la TV.")
                .setView(input)
                .setNegativeButton("Annuler", (d, w) -> { primary.setEnabled(true); primary.requestFocus(); })
                .setPositiveButton("Associer", null)
                .create();
        dialog.setOnShowListener(x -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String pin = input.getText().toString().trim();
                if (pin.length() < 4) { input.setError("PIN invalide"); input.requestFocus(); return; }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                message.setText("Validation du PIN…");
                executor.execute(() -> {
                    try {
                        PhilipsPairer.Result r = pairer.grant(pending, pin);
                        ConfigStore.savePhilips(this, r.ip, r.user, r.key);
                        runOnUiThread(() -> { dialog.dismiss(); showStep(1); message.setText("Philips associée automatiquement ✓"); });
                    } catch (Exception e) {
                        runOnUiThread(() -> { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); input.setError("PIN refusé"); message.setText("Association refusée : " + shortError(e)); });
                    }
                });
            });
            input.requestFocus();
            if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        });
        dialog.show();
    }

    private void showGovee() {
        next.setText("CONTINUER  →");
        List<GoveeConfig> configured = ConfigStore.goveeLights(this);
        int enabledCount = ConfigStore.enabledGoveeCount(this);
        content.addView(kicker("APPAREILS GOVEE"));
        content.addView(title(configured.isEmpty()
                ? "Connecte tes lumières"
                : configured.size() + " appareil" + (configured.size() > 1 ? "s" : "") + " enregistré" + (configured.size() > 1 ? "s" : "")));
        content.addView(body("Active Contrôle LAN dans Govee Home. Une lampe déconnectée reste enregistrée dans AmbiGovee mais n'est plus pilotée."));

        TvButton firstRow = null;
        TvButton lastRow = null;
        if (!configured.isEmpty()) {
            TextView lab = kicker("MES APPAREILS — OK pour gérer");
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMargins(0, dp(18), 0, dp(8));
            content.addView(lab, lp);

            TvButton previous = null;
            for (GoveeConfig g : configured) {
                String state = g.enabled ? "CONNECTÉE" : "DÉCONNECTÉE";
                String icon = g.enabled ? "●" : "○";
                TvButton row = button(icon + "  " + g.displayName() + "   •   " + g.positionLabel() + "   •   " + state,
                        g.enabled ? 0xff171d2a : 0xff121620,
                        g.enabled ? 0xff7655ff : 0xff596179);
                row.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
                row.setPadding(dp(22), 0, dp(18), 0);
                row.setOnClickListener(v -> editGovee(g));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(58));
                rp.setMargins(0, dp(5), 0, 0);
                content.addView(row, rp);
                if (firstRow == null) firstRow = row;
                if (previous != null) {
                    previous.setNextFocusDownId(row.getId());
                    row.setNextFocusUpId(previous.getId());
                }
                previous = row;
                lastRow = row;
            }
        }

        primary = button("＋  RECHERCHER / AJOUTER UN GOVEE", 0xff35255f, 0xff7655ff);
        primary.setOnClickListener(v -> scanGovee());
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(520), dp(60));
        pp.setMargins(0, dp(16), 0, 0);
        content.addView(primary, pp);

        if (lastRow != null) {
            lastRow.setNextFocusDownId(primary.getId());
            primary.setNextFocusUpId(lastRow.getId());
        }
        primary.setNextFocusDownId(next.getId());

        int disconnected = configured.size() - enabledCount;
        message = body(configured.isEmpty()
                ? "AmbiGovee va scanner automatiquement les appareils Govee LAN disponibles sur ton réseau."
                : enabledCount + " connecté" + (enabledCount > 1 ? "s" : "") + " • "
                    + disconnected + " déconnecté" + (disconnected > 1 ? "s" : "")
                    + ". Une déconnexion n'efface ni la position ni l'appareil.");
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.setMargins(0, dp(12), 0, 0);
        content.addView(message, mp);

        next.setEnabled(!configured.isEmpty());
        final TvButton focusTarget = firstRow != null ? firstRow : primary;
        focusTarget.post(() -> focusTarget.requestFocus());
    }

    private void scanGovee() {
        primary.setEnabled(false);
        message.setText("Scan du réseau local…");
        stopService(new Intent(this, AmbiService.class));
        executor.execute(() -> {
            try {
                Thread.sleep(220);
                List<GoveeLan.Device> devices = GoveeLan.discoverDevices(this, 3200);
                runOnUiThread(() -> showGoveeDevices(devices));
            } catch (Exception e) {
                runOnUiThread(() -> { primary.setEnabled(true); message.setText("Scan impossible : " + shortError(e)); });
            }
        });
    }

    private void showGoveeDevices(List<GoveeLan.Device> devices) {
        primary.setEnabled(true);
        if (devices == null || devices.isEmpty()) {
            message.setText("Aucun Govee LAN détecté. Vérifie Contrôle LAN et que TV + lampes sont sur le même réseau.");
            primary.requestFocus(); return;
        }
        List<GoveeConfig> existing = ConfigStore.goveeLights(this);
        String[] labels = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            GoveeLan.Device d = devices.get(i);
            boolean already = false;
            for (GoveeConfig g : existing) if (g.identity().equals(d.identity())) { already = true; break; }
            labels[i] = d.toString() + (already ? "   ✓ déjà ajouté" : "");
        }
        new AlertDialog.Builder(this)
                .setTitle("Choisis une lumière Govee")
                .setItems(labels, (dialog, which) -> choosePosition(devices.get(which)))
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void choosePosition(GoveeLan.Device d) {
        final String[] labels = {"Plafond / pièce entière", "Gauche de la TV", "Au-dessus de la TV", "Droite de la TV", "Sous la TV"};
        final String[] values = {GoveeConfig.POSITION_ROOM, GoveeConfig.POSITION_LEFT, GoveeConfig.POSITION_TOP, GoveeConfig.POSITION_RIGHT, GoveeConfig.POSITION_BOTTOM};
        new AlertDialog.Builder(this)
                .setTitle("Où se trouve " + (d.sku.isEmpty() ? "cette lampe" : d.sku) + " ?")
                .setItems(labels, (dialog, which) -> {
                    GoveeConfig cfg = new GoveeConfig(d.ip, d.device, d.sku, values[which], "", true);
                    ConfigStore.upsertGovee(this, cfg);
                    showStep(2);
                    message.setText(cfg.displayName() + " ajouté • " + cfg.positionLabel() + " ✓");
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void editGovee(GoveeConfig g) {
        final String toggle = g.enabled ? "DÉCONNECTER DE LA SYNCHRO" : "CONNECTER À LA SYNCHRO";
        final String[] labels = {toggle, "Plafond / pièce entière", "Gauche de la TV", "Au-dessus de la TV", "Droite de la TV", "Sous la TV", "SUPPRIMER CET APPAREIL"};
        final String[] values = {GoveeConfig.POSITION_ROOM, GoveeConfig.POSITION_LEFT, GoveeConfig.POSITION_TOP, GoveeConfig.POSITION_RIGHT, GoveeConfig.POSITION_BOTTOM};
        new AlertDialog.Builder(this)
                .setTitle(g.displayName() + " — " + (g.enabled ? "connectée" : "déconnectée"))
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        ConfigStore.setGoveeEnabled(this, g.identity(), !g.enabled);
                        showStep(2);
                        message.setText(g.displayName() + (g.enabled ? " déconnectée. Elle reste enregistrée." : " reconnectée à la synchronisation ✓"));
                    } else if (which == 6) {
                        ConfigStore.removeGovee(this, g.identity());
                        showStep(2);
                        message.setText(g.displayName() + " supprimée d'AmbiGovee.");
                    } else {
                        ConfigStore.upsertGovee(this, g.withPosition(values[which - 1]));
                        showStep(2);
                        message.setText("Position mise à jour ✓");
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void showFinish() {
        next.setVisibility(View.GONE);
        content.addView(kicker("VÉRIFICATION"));
        content.addView(title("On teste la TV et toutes les lampes."));
        content.addView(body("Le test ne modifie pas ta lumière normale. Il vérifie Ambilight /measured et que chaque Govee configuré répond bien au protocole LAN RGB."));

        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL); results.setPadding(dp(22), dp(18), dp(22), dp(18));
        results.setBackground(roundGradient(0xff151a27, 0xff101521, 20, 0xff293144));
        TextView philipsResult = text("Philips : en attente du test", 18, 0xffaab2c4, true);
        TextView goveeResult = text("Govee : en attente du test", 18, 0xffaab2c4, true);
        results.addView(philipsResult); LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2); gp.setMargins(0, dp(10), 0, 0); results.addView(goveeResult, gp);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(130)); rp.setMargins(0, dp(20), 0, dp(16)); content.addView(results, rp);

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        primary = button("TESTER MAINTENANT", 0xff1a2030, 0xff4c5a78);
        TvButton finish = button("TERMINER ET LANCER", 0xff35255f, 0xff7655ff);
        boolean configured = ConfigStore.isConfigured(this); primary.setEnabled(configured); finish.setEnabled(configured);
        primary.setOnClickListener(v -> runCompatibilityTest(philipsResult, goveeResult)); finish.setOnClickListener(v -> finishSetup());
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, dp(62), 1); a.setMargins(dp(6), 0, dp(6), 0); actions.addView(primary, a); actions.addView(finish, a); content.addView(actions);
        primary.setNextFocusRightId(finish.getId()); finish.setNextFocusLeftId(primary.getId()); primary.post(() -> primary.requestFocus());
    }

    private void runCompatibilityTest(TextView philipsResult, TextView goveeResult) {
        primary.setEnabled(false); philipsResult.setText("Philips : test du flux Ambilight…"); goveeResult.setText("Govee : test de toutes les lampes…");
        executor.execute(() -> {
            boolean pOk=false; String pText; GoveeLan temp=null; int total=ConfigStore.enabledGoveeCount(this), ok=0;
            try { JSONObject measured=new PhilipsClient(this).getMeasured(); pOk=measured.optJSONObject("layer1")!=null; pText=pOk?"Philips : Ambilight /measured compatible ✓":"Philips : flux Ambilight non détecté"; }
            catch(Exception e){pText="Philips : échec — "+shortError(e);}
            try {
                temp=new GoveeLan(this);
                for(GoveeConfig cfg:ConfigStore.goveeLights(this)){
                    if(!cfg.enabled) continue;
                    GoveeLan.GoveeState st=temp.queryStatus(cfg);
                    if(st!=null&&st.colorCapable)ok++;
                }
            }catch(Exception ignored){} finally {if(temp!=null)temp.close();}
            final int gok=ok; final String pt=pText; final boolean pp=pOk;
            final String gt = gok==total && total>0 ? "Govee : "+gok+"/"+total+" lampes LAN + RGB compatibles ✓" : "Govee : "+gok+"/"+total+" compatibles — vérifie les autres";
            runOnUiThread(() -> { philipsResult.setText(pt); philipsResult.setTextColor(pp?0xff79dfaa:0xffff8c8c); goveeResult.setText(gt); goveeResult.setTextColor(gok==total&&total>0?0xff79dfaa:0xffffc56d); primary.setEnabled(true); primary.requestFocus(); });
        });
    }

    private void finishSetup() {
        if (!ConfigStore.isConfigured(this)) return;
        ConfigStore.markSetupCompleted(this);
        Intent s = new Intent(this, AmbiService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private LinearLayout infoCard(String icon, String heading, String detail) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(18), dp(20), dp(16)); box.setBackground(roundGradient(0xff151a27, 0xff101521, 20, 0xff293144));
        box.addView(text(icon, 18, 0xffc7bbff, true)); TextView h=text(heading,19,Color.WHITE,true); LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.setMargins(0,dp(8),0,0);box.addView(h,hp); TextView d=text(detail,13,0xff949db1,false);LinearLayout.LayoutParams dpv=new LinearLayout.LayoutParams(-1,-2);dpv.setMargins(0,dp(6),0,0);box.addView(d,dpv);return box;
    }
    private TextView kicker(String s){return text(s,13,0xff9e8fff,true);} private TextView title(String s){TextView t=text(s,29,Color.WHITE,true);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,0);t.setLayoutParams(p);return t;} private TextView body(String s){TextView t=text(s,16,0xffa5aec1,false);t.setLineSpacing(0,1.12f);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(10),0,0);t.setLayoutParams(p);return t;}
    private TvButton button(String label,int normal,int focus){TvButton b=new TvButton(this);b.setId(View.generateViewId());b.setText(label);b.colors(normal,focus);return b;}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
    private GradientDrawable pageBackground(){return new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff070a10,0xff0d1019,0xff170f25});}
    private GradientDrawable roundGradient(int a,int b,int radius,int stroke){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});g.setCornerRadius(dp(radius));g.setStroke(dp(1),stroke);return g;}
    private String shortError(Exception e){String s=e.getMessage();if(s==null||s.trim().isEmpty())return e.getClass().getSimpleName();return s.length()>95?s.substring(0,95)+"…":s;}

    @Override protected void onDestroy(){
        executor.shutdownNow();
        if(ConfigStore.isConfigured(this)){
            try{Intent s=new Intent(this,AmbiService.class);if(Build.VERSION.SDK_INT>=26)startForegroundService(s);else startService(s);}catch(Exception ignored){}
        }
        super.onDestroy();
    }
    private int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
}
