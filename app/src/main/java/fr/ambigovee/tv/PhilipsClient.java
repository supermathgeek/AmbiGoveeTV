package fr.ambigovee.tv;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Client JointSpace authentifié utilisé par le moteur Ambilight.
 *
 * Important : la réponse HTTP Digest est calculée avec l'algorithme réellement
 * annoncé par la TV (MD5, MD5-sess, SHA/SHA-1, SHA-256, SHA-512).
 */
final class PhilipsClient {

    private static final int PORT = 1926;

    private static final Pattern DIGEST_PAIR = Pattern.compile(
            "([a-zA-Z0-9_]+)=(?:\\\"([^\\\"]*)\\\"|([^,\\s]+))"
    );

    private final Context context;
    private final String user;
    private final String key;
    private final String configuredIp;
    private final SSLSocketFactory sslSocketFactory;
    private final HostnameVerifier hostnameVerifier = (hostname, session) -> true;
    private final SecureRandom random = new SecureRandom();
    private final AtomicInteger nonceCount = new AtomicInteger(1);

    private volatile String preferredBase;
    private volatile String challengeBase;
    private volatile DigestChallenge challenge;

    PhilipsClient(Context context) throws Exception {
        this.context = context.getApplicationContext();
        ConfigStore.seedDefaults(this.context);

        user = ConfigStore.tvUser(this.context);
        key = ConfigStore.tvKey(this.context);
        configuredIp = ConfigStore.tvIp(this.context);

        if (user.isEmpty() || key.isEmpty()) {
            throw new IllegalStateException("Philips non appairée");
        }

        TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};

        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, trustAll, new SecureRandom());
        sslSocketFactory = ssl.getSocketFactory();
    }

    JSONObject getMeasured() throws Exception {
        return getJson("/6/ambilight/measured");
    }

    JSONObject getPowerState() throws Exception {
        return getJson("/6/powerstate");
    }

    private JSONObject getJson(String path) throws Exception {
        Exception last = null;

        for (String base : candidates()) {
            try {
                String body = authenticatedGet(base, path);
                preferredBase = base;
                return new JSONObject(body);
            } catch (Exception e) {
                last = e;

                if (base.equals(preferredBase)) {
                    preferredBase = null;
                }

                if (base.equals(challengeBase)) {
                    challenge = null;
                    challengeBase = null;
                    nonceCount.set(1);
                }
            }
        }

        if (last != null) throw last;
        throw new IllegalStateException("API Philips inaccessible");
    }

    private List<String> candidates() {
        List<String> list = new ArrayList<>();

        if (preferredBase != null) addUnique(list, preferredBase);

        // L'app tourne directement sur la TV : localhost est généralement le plus rapide.
        addUnique(list, "https://127.0.0.1:" + PORT);

        String local = localIpv4();
        if (local != null && !local.trim().isEmpty()) {
            addUnique(list, "https://" + local + ":" + PORT);
        }

        if (configuredIp != null && !configuredIp.trim().isEmpty()) {
            addUnique(list, "https://" + configuredIp + ":" + PORT);
        }

        return list;
    }

    private static void addUnique(List<String> list, String value) {
        if (value != null && !value.isEmpty() && !list.contains(value)) {
            list.add(value);
        }
    }

    private String authenticatedGet(String base, String path) throws Exception {
        DigestChallenge current = challenge;

        if (current == null || challengeBase == null || !base.equals(challengeBase)) {
            RawResponse first = request(base, path, null);

            if (first.code == HttpURLConnection.HTTP_OK) {
                return first.body;
            }

            if (first.code != HttpURLConnection.HTTP_UNAUTHORIZED) {
                throw new IllegalStateException("Philips HTTP " + first.code + details(first.body));
            }

            current = parseChallenge(first.wwwAuthenticate);
            challenge = current;
            challengeBase = base;
            nonceCount.set(1);
        }

        RawResponse response = request(
                base,
                path,
                buildAuthorization(current, "GET", path)
        );

        // Un nonce peut devenir stale pendant que l'app tourne.
        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED
                && response.wwwAuthenticate != null
                && !response.wwwAuthenticate.trim().isEmpty()) {

            current = parseChallenge(response.wwwAuthenticate);
            challenge = current;
            challengeBase = base;
            nonceCount.set(1);

            response = request(
                    base,
                    path,
                    buildAuthorization(current, "GET", path)
            );
        }

        if (response.code != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException("Philips HTTP " + response.code + details(response.body));
        }

        return response.body;
    }

    private RawResponse request(String base, String path, String authorization) throws Exception {
        HttpsURLConnection c = (HttpsURLConnection) new URL(base + path).openConnection();
        c.setSSLSocketFactory(sslSocketFactory);
        c.setHostnameVerifier(hostnameVerifier);
        c.setRequestMethod("GET");
        c.setConnectTimeout(400);
        c.setReadTimeout(800);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Connection", "keep-alive");

        if (authorization != null && !authorization.isEmpty()) {
            c.setRequestProperty("Authorization", authorization);
        }

        int code = c.getResponseCode();
        String wwwAuthenticate = c.getHeaderField("WWW-Authenticate");
        InputStream stream = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        String body = readAll(stream);
        c.disconnect();

        return new RawResponse(code, body, wwwAuthenticate);
    }

    private String buildAuthorization(DigestChallenge c, String method, String uri) throws Exception {
        String algorithm = c.algorithm == null || c.algorithm.trim().isEmpty()
                ? "MD5"
                : c.algorithm.trim();

        String hashAlgorithm = digestHashAlgorithm(algorithm);
        boolean sessionAlgorithm = algorithm.toUpperCase(Locale.US).endsWith("-SESS");

        String cnonce = randomHex(16);
        int count = Math.max(1, nonceCount.getAndIncrement());
        String nc = String.format(Locale.US, "%08x", count);

        String ha1 = hashHex(hashAlgorithm, user + ":" + c.realm + ":" + key);
        if (sessionAlgorithm) {
            ha1 = hashHex(hashAlgorithm, ha1 + ":" + c.nonce + ":" + cnonce);
        }

        String ha2 = hashHex(hashAlgorithm, method + ":" + uri);
        String response = c.qop != null
                ? hashHex(hashAlgorithm,
                    ha1 + ":" + c.nonce + ":" + nc + ":" + cnonce + ":" + c.qop + ":" + ha2)
                : hashHex(hashAlgorithm, ha1 + ":" + c.nonce + ":" + ha2);

        StringBuilder h = new StringBuilder("Digest ");
        h.append("username=\"").append(user).append("\"");
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
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) out.append(line);
        }
        return out.toString();
    }

    private static String details(String body) {
        if (body == null || body.trim().isEmpty()) return "";
        String value = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (value.length() > 160) value = value.substring(0, 160) + "…";
        return " — " + value;
    }

    static String localIpv4() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;

                Enumeration<java.net.InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static final class RawResponse {
        final int code;
        final String body;
        final String wwwAuthenticate;

        RawResponse(int code, String body, String wwwAuthenticate) {
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
