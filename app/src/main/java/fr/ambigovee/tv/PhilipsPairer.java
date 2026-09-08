package fr.ambigovee.tv;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Association Philips JointSpace v6.
 *
 * Cette implémentation reproduit le flux qui fonctionne avec philipstv :
 * - pair/request sans authentification
 * - signature du PIN en HMAC-SHA256
 * - pair/grant avec HTTP Digest
 * - prise en charge réelle de l'algorithme Digest annoncé par la TV
 *   (MD5, MD5-sess, SHA/SHA-1, SHA-256, SHA-512)
 */
final class PhilipsPairer {

    private static final int PORT = 1926;

    private static final String SECRET =
            "JCqdN5AcnAHgJYseUn7ER5k3qgtemfUvMRghQpTfTZq7Cvv8EPQPqfz6dDxPQPSu4gKFPWkJGw32zyASgJkHwCjU";

    private static final Pattern DIGEST_PAIR = Pattern.compile(
            "([a-zA-Z0-9_]+)=(?:\\\"([^\\\"]*)\\\"|([^,\\s]+))"
    );

    static final class Pending {
        final String base;
        final String deviceId;
        final String authKey;
        final long timestamp;

        Pending(String base, String deviceId, String authKey, long timestamp) {
            this.base = base;
            this.deviceId = deviceId;
            this.authKey = authKey;
            this.timestamp = timestamp;
        }
    }

    static final class Result {
        final String ip;
        final String user;
        final String key;

        Result(String ip, String user, String key) {
            this.ip = ip;
            this.user = user;
            this.key = key;
        }
    }

    private final SSLSocketFactory sslSocketFactory;
    private final HostnameVerifier hostnameVerifier = (hostname, session) -> true;
    private final SecureRandom random = new SecureRandom();

    PhilipsPairer() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};

        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, trustAll, new SecureRandom());
        sslSocketFactory = ssl.getSocketFactory();
    }

    Pending begin() throws Exception {
        String local = PhilipsClient.localIpv4();
        String[] hosts = local != null && !local.trim().isEmpty()
                ? new String[]{local, "127.0.0.1"}
                : new String[]{"127.0.0.1"};

        Exception last = null;
        for (String host : hosts) {
            try {
                return beginAt(host);
            } catch (Exception e) {
                last = e;
            }
        }

        if (last != null) throw last;
        throw new IllegalStateException("TV Philips introuvable");
    }

    private Pending beginAt(String host) throws Exception {
        String base = "https://" + host + ":" + PORT;
        String deviceId = randomId();

        JSONObject body = new JSONObject();
        body.put("scope", new JSONArray().put("read").put("write").put("control"));
        body.put("device", device(deviceId));

        Raw response = post(base, "/6/pair/request", body.toString(), null);
        if (response.code < 200 || response.code >= 300) {
            throw new IllegalStateException("Pair request HTTP " + response.code + details(response.body));
        }

        JSONObject json = new JSONObject(response.body);
        if (!"SUCCESS".equalsIgnoreCase(json.optString("error_id", ""))) {
            throw new IllegalStateException(json.optString("error_text", "Association Philips refusée"));
        }

        String authKey = json.optString("auth_key", "").trim();
        long timestamp = json.optLong("timestamp", 0L);
        if (authKey.isEmpty() || timestamp <= 0L) {
            throw new IllegalStateException("Réponse d'association Philips incomplète");
        }

        return new Pending(base, deviceId, authKey, timestamp);
    }

    Result grant(Pending pending, String pin) throws Exception {
        if (pending == null) {
            throw new IllegalStateException("Association expirée. Relance l'association.");
        }

        pin = pin == null ? "" : pin.trim();
        if (pin.length() < 4) {
            throw new IllegalArgumentException("PIN invalide");
        }

        JSONObject auth = new JSONObject();
        auth.put("pin", pin);
        auth.put("auth_timestamp", pending.timestamp);
        auth.put("auth_signature", signature(pending.timestamp, pin));

        JSONObject body = new JSONObject();
        body.put("auth", auth);
        body.put("device", device(pending.deviceId));

        String path = "/6/pair/grant";

        // Comme requests.HTTPDigestAuth : premier envoi sans Authorization,
        // puis nouvelle tentative avec le challenge Digest renvoyé par la TV.
        Raw first = post(pending.base, path, body.toString(), null);
        Raw response = first;

        if (first.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            DigestChallenge challenge = parseChallenge(first.wwwAuthenticate);
            response = post(
                    pending.base,
                    path,
                    body.toString(),
                    buildAuthorization(challenge, "POST", path, pending.deviceId, pending.authKey, 1)
            );

            // Si le nonce a changé/stale, on accepte un nouveau challenge une fois.
            if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED
                    && response.wwwAuthenticate != null
                    && !response.wwwAuthenticate.trim().isEmpty()) {
                challenge = parseChallenge(response.wwwAuthenticate);
                response = post(
                        pending.base,
                        path,
                        body.toString(),
                        buildAuthorization(challenge, "POST", path, pending.deviceId, pending.authKey, 1)
                );
            }
        }

        if (response.code < 200 || response.code >= 300) {
            throw new IllegalStateException("Pair grant HTTP " + response.code + details(response.body));
        }

        JSONObject json = response.body == null || response.body.trim().isEmpty()
                ? new JSONObject()
                : new JSONObject(response.body);

        if (json.has("error_id")
                && !"SUCCESS".equalsIgnoreCase(json.optString("error_id", ""))) {
            throw new IllegalStateException(json.optString("error_text", "Association Philips refusée"));
        }

        String ip = pending.base
                .replace("https://", "")
                .replace(":" + PORT, "");

        return new Result(ip, pending.deviceId, pending.authKey);
    }

    private JSONObject device(String id) throws Exception {
        JSONObject device = new JSONObject();
        device.put("device_name", "AmbiGovee TV");
        device.put("device_os", "Android TV");
        device.put("app_name", "AmbiGovee");
        device.put("type", "native");
        device.put("app_id", "fr.ambigovee.tv");
        device.put("id", id);
        return device;
    }

    private String signature(long timestamp, String pin) throws Exception {
        byte[] secret = Base64.decode(SECRET, Base64.DEFAULT);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        byte[] digest = mac.doFinal((String.valueOf(timestamp) + pin).getBytes(StandardCharsets.UTF_8));

        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format(Locale.US, "%02x", b & 0xff));

        return Base64.encodeToString(
                hex.toString().getBytes(StandardCharsets.UTF_8),
                Base64.NO_WRAP
        );
    }

    private Raw post(String base, String path, String body, String authorization) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(base + path).openConnection();
        c.setSSLSocketFactory(sslSocketFactory);
        c.setHostnameVerifier(hostnameVerifier);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setUseCaches(false);
        c.setConnectTimeout(1800);
        c.setReadTimeout(3500);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Connection", "keep-alive");
        if (authorization != null && !authorization.isEmpty()) {
            c.setRequestProperty("Authorization", authorization);
        }

        try (OutputStream out = c.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        int code = c.getResponseCode();
        String challenge = c.getHeaderField("WWW-Authenticate");
        InputStream stream = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        String text = readAll(stream);
        c.disconnect();
        return new Raw(code, text, challenge);
    }

    private DigestChallenge parseChallenge(String header) {
        if (header == null || !header.toLowerCase(Locale.US).contains("digest")) {
            throw new IllegalStateException("Challenge Digest Philips absent");
        }

        Map<String, String> parts = new HashMap<>();
        Matcher m = DIGEST_PAIR.matcher(header);
        while (m.find()) {
            parts.put(
                    m.group(1).toLowerCase(Locale.US),
                    m.group(2) != null ? m.group(2) : m.group(3)
            );
        }

        String realm = parts.get("realm");
        String nonce = parts.get("nonce");
        if (realm == null || nonce == null) {
            throw new IllegalStateException("Challenge Digest Philips invalide");
        }

        String qop = null;
        String rawQop = parts.get("qop");
        if (rawQop != null && !rawQop.trim().isEmpty()) {
            for (String candidate : rawQop.split(",")) {
                if ("auth".equalsIgnoreCase(candidate.trim())) {
                    qop = "auth";
                    break;
                }
            }
            if (qop == null) {
                throw new IllegalStateException("Digest qop non supporté : " + rawQop);
            }
        }

        return new DigestChallenge(
                realm,
                nonce,
                qop,
                parts.getOrDefault("algorithm", "MD5"),
                parts.get("opaque")
        );
    }

    private String buildAuthorization(
            DigestChallenge c,
            String method,
            String uri,
            String username,
            String password,
            int nonceCount
    ) throws Exception {

        String algorithm = c.algorithm == null || c.algorithm.trim().isEmpty()
                ? "MD5"
                : c.algorithm.trim();

        String hashAlgorithm = digestHashAlgorithm(algorithm);
        boolean sessionAlgorithm = algorithm.toUpperCase(Locale.US).endsWith("-SESS");

        String cnonce = randomHex(16);
        String nc = String.format(Locale.US, "%08x", Math.max(1, nonceCount));

        String ha1 = hashHex(hashAlgorithm, username + ":" + c.realm + ":" + password);
        if (sessionAlgorithm) {
            ha1 = hashHex(hashAlgorithm, ha1 + ":" + c.nonce + ":" + cnonce);
        }

        String ha2 = hashHex(hashAlgorithm, method + ":" + uri);
        String response = c.qop != null
                ? hashHex(hashAlgorithm,
                    ha1 + ":" + c.nonce + ":" + nc + ":" + cnonce + ":" + c.qop + ":" + ha2)
                : hashHex(hashAlgorithm, ha1 + ":" + c.nonce + ":" + ha2);

        StringBuilder h = new StringBuilder("Digest ");
        h.append("username=\"").append(username).append("\"");
        h.append(", realm=\"").append(c.realm).append("\"");
        h.append(", nonce=\"").append(c.nonce).append("\"");
        h.append(", uri=\"").append(uri).append("\"");
        h.append(", response=\"").append(response).append("\"");

        if (c.opaque != null && !c.opaque.isEmpty()) {
            h.append(", opaque=\"").append(c.opaque).append("\"");
        }

        if (c.algorithm != null && !c.algorithm.trim().isEmpty()) {
            h.append(", algorithm=\"").append(c.algorithm).append("\"");
        }

        if (c.qop != null) {
            h.append(", qop=\"").append(c.qop).append("\"");
            h.append(", nc=").append(nc);
            h.append(", cnonce=\"").append(cnonce).append("\"");
        }

        return h.toString();
    }

    private static String digestHashAlgorithm(String algorithm) {
        String normalized = algorithm.toUpperCase(Locale.US).replace("-SESS", "");
        if ("MD5".equals(normalized)) return "MD5";
        if ("SHA".equals(normalized) || "SHA-1".equals(normalized)) return "SHA-1";
        if ("SHA-256".equals(normalized)) return "SHA-256";
        if ("SHA-512".equals(normalized)) return "SHA-512";
        throw new IllegalStateException("Algorithme Digest Philips non supporté : " + algorithm);
    }

    private static String hashHex(String algorithm, String value) throws Exception {
        byte[] result = MessageDigest.getInstance(algorithm)
                .digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : result) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private String randomId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder out = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            out.append(chars.charAt(random.nextInt(chars.length())));
        }
        return out.toString();
    }

    private String randomHex(int bytes) {
        byte[] data = new byte[bytes];
        random.nextBytes(data);
        StringBuilder out = new StringBuilder(bytes * 2);
        for (byte b : data) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private static String details(String body) {
        if (body == null || body.trim().isEmpty()) return "";
        String value = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.length() > 160) value = value.substring(0, 160) + "…";
        return " — " + value;
    }

    private static final class Raw {
        final int code;
        final String body;
        final String wwwAuthenticate;

        Raw(int code, String body, String wwwAuthenticate) {
            this.code = code;
            this.body = body;
            this.wwwAuthenticate = wwwAuthenticate;
        }
    }

    private static final class DigestChallenge {
        final String realm;
        final String nonce;
        final String qop;
        final String algorithm;
        final String opaque;

        DigestChallenge(String realm, String nonce, String qop, String algorithm, String opaque) {
            this.realm = realm;
            this.nonce = nonce;
            this.qop = qop;
            this.algorithm = algorithm;
            this.opaque = opaque;
        }
    }
}
