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
        return PairingPhonePage.render();
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
