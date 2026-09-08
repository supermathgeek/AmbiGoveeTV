package fr.ambigovee.tv;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mini serveur HTTP local utilisé uniquement pendant l'association Philips.
 *
 * Pourquoi : la fenêtre Philips qui affiche le PIN doit rester ouverte. Comme AmbiGovee
 * tourne sur la même TV, le téléphone sert de second écran pour saisir le PIN sans fermer
 * cette fenêtre. Aucun identifiant Philips n'est renvoyé au téléphone et rien ne sort du LAN.
 */
final class PairingBridgeServer implements Closeable {

    static final String STATE_READY = "ready";
    static final String STATE_STARTING = "starting";
    static final String STATE_WAITING_PIN = "waiting_pin";
    static final String STATE_VERIFYING = "verifying";
    static final String STATE_SUCCESS = "success";
    static final String STATE_ERROR = "error";
    static final String STATE_EXPIRED = "expired";

    private static final int FIRST_PORT = 8765;
    private static final int LAST_PORT = 8785;
    private static final int SOCKET_TIMEOUT_MS = 7000;
    private static final int MAX_BODY_BYTES = 4096;

    private final Context context;
    private final String ip;
    private final String token;
    private final String basePath;
    private final Object lock = new Object();
    private final ExecutorService clients = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;
    private int port;

    private PhilipsPairer pairer;
    private PhilipsPairer.Pending pending;
    private String state = STATE_READY;
    private String message = "Prêt à démarrer l'association.";

    private PairingBridgeServer(Context context, String ip, String token) {
        this.context = context.getApplicationContext();
        this.ip = ip;
        this.token = token;
        this.basePath = "/p/" + token;
    }

    static PairingBridgeServer start(Context context) throws Exception {
        String ip = PhilipsClient.localIpv4();
        if (ip == null || ip.trim().isEmpty()) {
            throw new IllegalStateException("La TV n'a pas d'adresse réseau locale");
        }

        PairingBridgeServer bridge = new PairingBridgeServer(
                context,
                ip.trim(),
                randomToken()
        );
        bridge.bindAndStart();
        return bridge;
    }

    String url() {
        return "http://" + ip + ":" + port + basePath;
    }

    String state() {
        synchronized (lock) {
            refreshExpirationLocked();
            return state;
        }
    }

    String message() {
        synchronized (lock) {
            refreshExpirationLocked();
            return message;
        }
    }

    int secondsLeft() {
        synchronized (lock) {
            refreshExpirationLocked();
            return pending == null ? 0 : pending.secondsLeft();
        }
    }

    private void bindAndStart() throws Exception {
        Exception last = null;

        for (int candidate = FIRST_PORT; candidate <= LAST_PORT; candidate++) {
            try {
                ServerSocket socket = new ServerSocket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress("0.0.0.0", candidate));
                serverSocket = socket;
                port = candidate;
                break;
            } catch (Exception e) {
                last = e;
            }
        }

        if (serverSocket == null) {
            throw new IllegalStateException(
                    "Impossible d'ouvrir le lien local"
                            + (last == null || last.getMessage() == null ? "" : " : " + last.getMessage())
            );
        }

        running = true;
        acceptThread = new Thread(this::acceptLoop, "AmbiGovee-PairingBridge");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                clients.execute(() -> handleClient(socket));
            } catch (Exception e) {
                if (running) {
                    synchronized (lock) {
                        if (!STATE_SUCCESS.equals(state)) {
                            state = STATE_ERROR;
                            message = "Le lien local a rencontré une erreur.";
                        }
                    }
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (Socket ignored = socket;
             InputStream rawIn = new BufferedInputStream(socket.getInputStream());
             OutputStream rawOut = new BufferedOutputStream(socket.getOutputStream())) {

            String requestLine = readLine(rawIn);
            if (requestLine == null || requestLine.trim().isEmpty()) {
                return;
            }

            String[] requestParts = requestLine.split(" ");
            if (requestParts.length < 2) {
                sendText(rawOut, 400, "Bad Request", "text/plain; charset=utf-8", "Requête invalide");
                return;
            }

            String method = requestParts[0].toUpperCase(Locale.US);
            String path = requestParts[1];
            int query = path.indexOf('?');
            if (query >= 0) path = path.substring(0, query);

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(rawIn)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(
                            line.substring(0, colon).trim().toLowerCase(Locale.US),
                            line.substring(colon + 1).trim()
                    );
                }
            }

            int contentLength = parseContentLength(headers.get("content-length"));
            if (contentLength > MAX_BODY_BYTES) {
                sendText(rawOut, 413, "Payload Too Large", "text/plain; charset=utf-8", "Requête trop grande");
                return;
            }

            String body = contentLength > 0
                    ? new String(readExactly(rawIn, contentLength), StandardCharsets.UTF_8)
                    : "";

            if ("GET".equals(method) && (basePath.equals(path) || (basePath + "/").equals(path))) {
                sendText(rawOut, 200, "OK", "text/html; charset=utf-8", phonePage());
                return;
            }

            if ("GET".equals(method) && (basePath + "/status").equals(path)) {
                sendJson(rawOut, statusJson());
                return;
            }

            if ("POST".equals(method) && (basePath + "/start").equals(path)) {
                sendJson(rawOut, startPairing().toString());
                return;
            }

            if ("POST".equals(method) && (basePath + "/pin").equals(path)) {
                Map<String, String> form = parseForm(body);
                sendJson(rawOut, submitPin(form.get("pin")).toString());
                return;
            }

            if ("GET".equals(method) && "/favicon.ico".equals(path)) {
                sendText(rawOut, 204, "No Content", "text/plain", "");
                return;
            }

            sendText(rawOut, 404, "Not Found", "text/plain; charset=utf-8", "Lien invalide");
        } catch (Exception ignored) {
            // Un téléphone peut fermer une connexion pendant un changement de page : ce n'est pas fatal.
        }
    }

    private JSONObject startPairing() {
        synchronized (lock) {
            refreshExpirationLocked();

            if (STATE_STARTING.equals(state)
                    || STATE_VERIFYING.equals(state)
                    || STATE_WAITING_PIN.equals(state)) {
                return statusObjectLocked();
            }

            if (STATE_SUCCESS.equals(state)) {
                return statusObjectLocked();
            }

            pairer = null;
            pending = null;
            state = STATE_STARTING;
            message = "Demande envoyée à la TV…";
        }

        try {
            PhilipsPairer newPairer = new PhilipsPairer();
            PhilipsPairer.Pending newPending = newPairer.begin();

            synchronized (lock) {
                pairer = newPairer;
                pending = newPending;
                state = STATE_WAITING_PIN;
                message = "Le PIN est affiché sur la TV. Laisse la fenêtre Philips ouverte.";
                return statusObjectLocked();
            }
        } catch (Exception e) {
            synchronized (lock) {
                state = STATE_ERROR;
                message = friendlyError(e);
                return statusObjectLocked();
            }
        }
    }

    private JSONObject submitPin(String pin) {
        PhilipsPairer localPairer;
        PhilipsPairer.Pending localPending;

        pin = pin == null ? "" : pin.trim();
        if (!pin.matches("[0-9]{4,8}")) {
            synchronized (lock) {
                message = "Entre le code complet affiché par la TV.";
                return statusObjectLocked();
            }
        }

        synchronized (lock) {
            refreshExpirationLocked();

            if (!STATE_WAITING_PIN.equals(state) || pairer == null || pending == null) {
                if (STATE_EXPIRED.equals(state)) {
                    return statusObjectLocked();
                }
                state = STATE_ERROR;
                message = "Relance l'association pour obtenir un nouveau PIN.";
                return statusObjectLocked();
            }

            if (pending.isExpired()) {
                state = STATE_EXPIRED;
                message = "Le PIN a expiré. Relance l'association.";
                return statusObjectLocked();
            }

            localPairer = pairer;
            localPending = pending;
            state = STATE_VERIFYING;
            message = "Validation du PIN…";
        }

        try {
            PhilipsPairer.Result result = localPairer.grant(localPending, pin);
            ConfigStore.savePhilips(context, result.ip, result.user, result.key);

            synchronized (lock) {
                state = STATE_SUCCESS;
                message = "TV Philips connectée ✓";
                return statusObjectLocked();
            }
        } catch (Exception e) {
            synchronized (lock) {
                String raw = friendlyError(e);
                String lower = raw.toLowerCase(Locale.US);

                if (lower.contains("timed out")
                        || lower.contains("timeout")
                        || lower.contains("expir")) {
                    state = STATE_EXPIRED;
                    message = "Le PIN a expiré. Appuie sur Recommencer pour en obtenir un nouveau.";
                } else {
                    state = STATE_ERROR;
                    message = raw;
                }
                pairer = null;
                pending = null;
                return statusObjectLocked();
            }
        }
    }

    private String statusJson() {
        synchronized (lock) {
            refreshExpirationLocked();
            return statusObjectLocked().toString();
        }
    }

    private JSONObject statusObjectLocked() {
        JSONObject json = new JSONObject();
        try {
            json.put("state", state);
            json.put("message", message);
            json.put("seconds_left", pending == null ? 0 : pending.secondsLeft());
            json.put("paired", !ConfigStore.tvUser(context).isEmpty() && !ConfigStore.tvKey(context).isEmpty());
        } catch (Exception ignored) {}
        return json;
    }

    private void refreshExpirationLocked() {
        if (STATE_WAITING_PIN.equals(state) && pending != null && pending.isExpired()) {
            state = STATE_EXPIRED;
            message = "Le PIN a expiré. Appuie sur Recommencer pour en obtenir un nouveau.";
            pairer = null;
            pending = null;
        }
    }

    private String phonePage() {
        String escapedUrl = html(url());

        return "<!doctype html>"
                + "<html lang=\"fr\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">"
                + "<meta name=\"theme-color\" content=\"#0b0f18\">"
                + "<title>AmbiGovee — Philips</title>"
                + "<style>"
                + "*{box-sizing:border-box}body{margin:0;min-height:100vh;background:linear-gradient(145deg,#06080d,#0b0f18 48%,#211238);color:#fff;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;display:flex;align-items:center;justify-content:center;padding:22px}"
                + ".wrap{width:min(520px,100%)}.brand{font-size:15px;color:#b9a9ff;font-weight:800;letter-spacing:.08em;text-transform:uppercase;margin-bottom:12px}"
                + ".card{background:rgba(20,26,38,.96);border:1px solid #30394d;border-radius:26px;padding:25px;box-shadow:0 24px 70px rgba(0,0,0,.38)}"
                + "h1{font-size:30px;line-height:1.08;margin:0 0 10px}.sub{color:#a9b2c4;font-size:16px;line-height:1.45;margin:0 0 22px}"
                + ".step{display:flex;gap:13px;align-items:flex-start;padding:14px 0;border-top:1px solid #293144}.num{min-width:34px;height:34px;border-radius:12px;background:#35255f;color:#d8ceff;font-weight:800;display:flex;align-items:center;justify-content:center}.txt{padding-top:5px;color:#d9dfeb}.hint{font-size:13px;color:#8994a8;margin-top:4px}"
                + "button{width:100%;border:0;border-radius:18px;padding:16px 18px;background:linear-gradient(135deg,#5d3fd7,#7a5cff);color:white;font-size:17px;font-weight:800;margin-top:18px;box-shadow:0 9px 24px rgba(93,63,215,.28)}button:disabled{opacity:.5}"
                + "input{width:100%;border:1px solid #4a5570;background:#0d121c;color:#fff;border-radius:18px;padding:17px 18px;font-size:28px;letter-spacing:.18em;text-align:center;outline:none;margin-top:10px}input:focus{border-color:#9079ff;box-shadow:0 0 0 3px rgba(144,121,255,.16)}"
                + ".status{margin-top:18px;border-radius:18px;padding:14px 16px;background:#101722;border:1px solid #293144;color:#c9d1df;line-height:1.4}.ok{border-color:#2d7659;background:#10231c;color:#8fe0b7}.warn{border-color:#7b6332;background:#241e11;color:#ffd087}.bad{border-color:#7b3949;background:#261319;color:#ff9dad}.hidden{display:none}.timer{font-size:13px;color:#a99bdf;margin-top:9px}.privacy{text-align:center;color:#747f93;font-size:12px;line-height:1.4;margin-top:15px}.url{word-break:break-all;color:#8f99ad;font-size:11px;text-align:center;margin-top:12px}"
                + "</style></head><body><main class=\"wrap\"><div class=\"brand\">AmbiGovee</div><section class=\"card\">"
                + "<h1 id=\"title\">Connecte ta TV Philips</h1>"
                + "<p class=\"sub\" id=\"subtitle\">Ton téléphone sert uniquement pendant cette première association.</p>"
                + "<div id=\"intro\">"
                + "<div class=\"step\"><div class=\"num\">1</div><div class=\"txt\">Appuie sur <b>Démarrer l'association</b>.<div class=\"hint\">La TV affichera immédiatement un PIN Philips.</div></div></div>"
                + "<div class=\"step\"><div class=\"num\">2</div><div class=\"txt\">Laisse la fenêtre du PIN <b>ouverte sur la TV</b>.<div class=\"hint\">Ne touche pas à « Fermer ».</div></div></div>"
                + "<div class=\"step\"><div class=\"num\">3</div><div class=\"txt\">Tape le PIN ici sur ton téléphone.</div></div>"
                + "<button id=\"start\" type=\"button\">DÉMARRER L'ASSOCIATION</button></div>"
                + "<form id=\"pinForm\" class=\"hidden\"><input id=\"pin\" name=\"pin\" inputmode=\"numeric\" pattern=\"[0-9]*\" autocomplete=\"one-time-code\" maxlength=\"8\" placeholder=\"PIN\"><div class=\"timer\" id=\"timer\"></div><button id=\"send\" type=\"submit\">CONNECTER LA TV</button></form>"
                + "<button id=\"retry\" class=\"hidden\" type=\"button\">RECOMMENCER</button>"
                + "<div id=\"status\" class=\"status\">Prêt.</div>"
                + "<div class=\"privacy\">Connexion locale uniquement • aucune image de la TV ni identifiant Philips n'est envoyé sur Internet.</div>"
                + "<div class=\"url\">" + escapedUrl + "</div>"
                + "</section></main>"
                + "<script>"
                + "const base=location.pathname.replace(/\\/$/,'');const qs=s=>document.querySelector(s);const intro=qs('#intro'),form=qs('#pinForm'),retry=qs('#retry'),statusBox=qs('#status'),title=qs('#title'),subtitle=qs('#subtitle'),start=qs('#start'),send=qs('#send'),pin=qs('#pin'),timer=qs('#timer');"
                + "async function call(path,opt){const r=await fetch(base+path,Object.assign({cache:'no-store'},opt||{}));return await r.json()}"
                + "function busy(v){start.disabled=v;send.disabled=v;retry.disabled=v}"
                + "function render(s){busy(false);statusBox.textContent=s.message||'';statusBox.className='status';timer.textContent='';"
                + "if(s.state==='ready'){intro.classList.remove('hidden');form.classList.add('hidden');retry.classList.add('hidden');title.textContent='Connecte ta TV Philips';subtitle.textContent='Ton téléphone sert uniquement pendant cette première association.';}"
                + "else if(s.state==='starting'){intro.classList.add('hidden');form.classList.add('hidden');retry.classList.add('hidden');busy(true);title.textContent='Ouverture sur la TV…';subtitle.textContent='Attends une seconde.';}"
                + "else if(s.state==='waiting_pin'){intro.classList.add('hidden');form.classList.remove('hidden');retry.classList.add('hidden');title.textContent='Entre le PIN affiché sur la TV';subtitle.textContent='Ne ferme surtout pas la fenêtre Philips sur la TV.';timer.textContent=s.seconds_left>0?'Code valable encore environ '+s.seconds_left+' s':'';if(document.activeElement!==pin)pin.focus();}"
                + "else if(s.state==='verifying'){intro.classList.add('hidden');form.classList.remove('hidden');retry.classList.add('hidden');busy(true);title.textContent='Connexion en cours…';subtitle.textContent='AmbiGovee valide le code avec la TV.';}"
                + "else if(s.state==='success'||s.paired){intro.classList.add('hidden');form.classList.add('hidden');retry.classList.add('hidden');statusBox.classList.add('ok');title.textContent='TV connectée ✓';subtitle.textContent='C’est terminé. Tu peux reposer ton téléphone et continuer sur la TV.';}"
                + "else{intro.classList.add('hidden');form.classList.add('hidden');retry.classList.remove('hidden');statusBox.classList.add(s.state==='expired'?'warn':'bad');title.textContent=s.state==='expired'?'Le code a expiré':'Association impossible';subtitle.textContent='On peut relancer proprement avec un nouveau PIN.';}"
                + "}"
                + "start.onclick=async()=>{busy(true);try{render(await call('/start',{method:'POST'}))}catch(e){statusBox.textContent='Connexion locale interrompue. Réessaie.';statusBox.className='status bad';busy(false)}};"
                + "retry.onclick=async()=>{busy(true);pin.value='';try{render(await call('/start',{method:'POST'}))}catch(e){busy(false)}};"
                + "form.onsubmit=async e=>{e.preventDefault();const v=pin.value.trim();if(!/^\\d{4,8}$/.test(v)){statusBox.textContent='Entre le PIN complet.';statusBox.className='status warn';return}busy(true);try{render(await call('/pin',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'pin='+encodeURIComponent(v)}))}catch(err){statusBox.textContent='Connexion locale interrompue. Réessaie.';statusBox.className='status bad';busy(false)}};"
                + "async function poll(){try{render(await call('/status'))}catch(e){}setTimeout(poll,700)}poll();"
                + "</script></body></html>";
    }

    private static int parseContentLength(String value) {
        if (value == null || value.trim().isEmpty()) return 0;
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static Map<String, String> parseForm(String body) throws Exception {
        Map<String, String> result = new HashMap<>();
        if (body == null || body.isEmpty()) return result;

        for (String part : body.split("&")) {
            int equals = part.indexOf('=');
            String key = equals >= 0 ? part.substring(0, equals) : part;
            String value = equals >= 0 ? part.substring(equals + 1) : "";
            result.put(
                    URLDecoder.decode(key, "UTF-8"),
                    URLDecoder.decode(value, "UTF-8")
            );
        }
        return result;
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int previous = -1;

        while (out.size() < 8192) {
            int current = in.read();
            if (current == -1) {
                if (out.size() == 0) return null;
                break;
            }

            if (previous == '\r' && current == '\n') {
                byte[] bytes = out.toByteArray();
                int length = Math.max(0, bytes.length - 1);
                return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
            }

            out.write(current);
            previous = current;
        }

        return new String(out.toByteArray(), StandardCharsets.ISO_8859_1).trim();
    }

    private static byte[] readExactly(InputStream in, int length) throws Exception {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int count = in.read(data, offset, length - offset);
            if (count < 0) break;
            offset += count;
        }
        if (offset == length) return data;

        byte[] partial = new byte[offset];
        System.arraycopy(data, 0, partial, 0, offset);
        return partial;
    }

    private static void sendJson(OutputStream out, String json) throws Exception {
        sendText(out, 200, "OK", "application/json; charset=utf-8", json);
    }

    private static void sendText(
            OutputStream out,
            int code,
            String reason,
            String contentType,
            String body
    ) throws Exception {
        byte[] payload = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + code + " " + reason + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + payload.length + "\r\n"
                + "Cache-Control: no-store, no-cache, must-revalidate\r\n"
                + "Pragma: no-cache\r\n"
                + "X-Content-Type-Options: nosniff\r\n"
                + "Connection: close\r\n\r\n";

        out.write(headers.getBytes(StandardCharsets.ISO_8859_1));
        out.write(payload);
        out.flush();
    }

    private static String friendlyError(Exception e) {
        String value = e == null ? null : e.getMessage();
        if (value == null || value.trim().isEmpty()) {
            value = e == null ? "Erreur inconnue" : e.getClass().getSimpleName();
        }
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.length() > 180) value = value.substring(0, 180) + "…";
        return value;
    }

    private static String randomToken() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private static String html(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    @Override
    public void close() {
        running = false;

        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (Exception ignored) {}
            serverSocket = null;
        }

        clients.shutdown();
    }
}
