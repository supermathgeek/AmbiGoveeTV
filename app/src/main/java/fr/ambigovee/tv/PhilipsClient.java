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

final class PhilipsClient {
    private static final int PORT = 1926;
    private static final Pattern DIGEST_PAIR = Pattern.compile("([a-zA-Z0-9_]+)=(?:\\\"([^\\\"]*)\\\"|([^,\\s]+))");

    private final Context context;
    private final String user;
    private final String key;
    private final String configuredIp;
    private final SSLSocketFactory sslSocketFactory;
    private final HostnameVerifier hostnameVerifier = (hostname, session) -> true;
    private final SecureRandom random = new SecureRandom();
    private final AtomicInteger nonceCount = new AtomicInteger(1);

    private volatile String preferredBase;
    private volatile DigestChallenge challenge;

    PhilipsClient(Context context) throws Exception {
        this.context = context.getApplicationContext();
        ConfigStore.seedDefaults(this.context);
        user = ConfigStore.tvUser(this.context);
        key = ConfigStore.tvKey(this.context);
        configuredIp = ConfigStore.tvIp(this.context);
        if (user.isEmpty() || key.isEmpty()) throw new IllegalStateException("Philips non appairée");

        TrustManager[] trustAll = new TrustManager[]{new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
            @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        }};
        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(null, trustAll, new SecureRandom());
        sslSocketFactory = ssl.getSocketFactory();
    }

    JSONObject getMeasured() throws Exception { return getJson("/6/ambilight/measured"); }
    JSONObject getPowerState() throws Exception { return getJson("/6/powerstate"); }

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
                    challenge = null;
                }
            }
        }
        if (last != null) throw last;
        throw new IllegalStateException("API Philips inaccessible");
    }

    private List<String> candidates() {
        List<String> list = new ArrayList<>();
        if (preferredBase != null) addUnique(list, preferredBase);
        // L'app tourne sur la TV : localhost est souvent le chemin le plus rapide.
        addUnique(list, "https://127.0.0.1:" + PORT);
        String local = localIpv4();
        if (local != null) addUnique(list, "https://" + local + ":" + PORT);
        if (configuredIp != null && !configuredIp.isEmpty()) addUnique(list, "https://" + configuredIp + ":" + PORT);
        return list;
    }

    private static void addUnique(List<String> list, String value) { if (!list.contains(value)) list.add(value); }

    private String authenticatedGet(String base, String path) throws Exception {
        DigestChallenge c = challenge;
        if (c == null) {
            RawResponse first = request(base, path, null);
            if (first.code == HttpURLConnection.HTTP_OK) return first.body;
            if (first.code != HttpURLConnection.HTTP_UNAUTHORIZED) throw new IllegalStateException("Philips HTTP " + first.code);
            c = parseChallenge(first.wwwAuthenticate);
            challenge = c;
            nonceCount.set(1);
        }

        String auth = buildAuthorization(c, "GET", path);
        RawResponse second = request(base, path, auth);
        if (second.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            c = parseChallenge(second.wwwAuthenticate);
            challenge = c;
            nonceCount.set(1);
            second = request(base, path, buildAuthorization(c, "GET", path));
        }
        if (second.code != HttpURLConnection.HTTP_OK) throw new IllegalStateException("Philips HTTP " + second.code);
        return second.body;
    }

    private RawResponse request(String base, String path, String authorization) throws Exception {
        URL url = new URL(base + path);
        HttpsURLConnection c = (HttpsURLConnection) url.openConnection();
        c.setSSLSocketFactory(sslSocketFactory);
        c.setHostnameVerifier(hostnameVerifier);
        c.setRequestMethod("GET");
        c.setConnectTimeout(260);
        c.setReadTimeout(500);
        c.setUseCaches(false);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("Connection", "keep-alive");
        if (authorization != null) c.setRequestProperty("Authorization", authorization);
        int code = c.getResponseCode();
        String challenge = c.getHeaderField("WWW-Authenticate");
        InputStream stream = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
        String body = readAll(stream);
        c.disconnect();
        return new RawResponse(code, body, challenge);
    }

    private String buildAuthorization(DigestChallenge c, String method, String uri) throws Exception {
        String cnonce = randomHex(16);
        int count = nonceCount.getAndIncrement();
        String nc = String.format(Locale.US, "%08x", count);
        String ha1 = md5(user + ":" + c.realm + ":" + key);
        if ("MD5-sess".equalsIgnoreCase(c.algorithm)) ha1 = md5(ha1 + ":" + c.nonce + ":" + cnonce);
        String ha2 = md5(method + ":" + uri);
        String response = c.qop != null && !c.qop.isEmpty()
                ? md5(ha1 + ":" + c.nonce + ":" + nc + ":" + cnonce + ":" + c.qop + ":" + ha2)
                : md5(ha1 + ":" + c.nonce + ":" + ha2);

        StringBuilder h = new StringBuilder("Digest ");
        h.append("username=\"").append(user).append("\"");
        h.append(", realm=\"").append(c.realm).append("\"");
        h.append(", nonce=\"").append(c.nonce).append("\"");
        h.append(", uri=\"").append(uri).append("\"");
        h.append(", response=\"").append(response).append("\"");
        if (c.algorithm != null && !c.algorithm.isEmpty()) h.append(", algorithm=").append(c.algorithm);
        if (c.qop != null && !c.qop.isEmpty()) {
            h.append(", qop=").append(c.qop).append(", nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\"");
        }
        if (c.opaque != null && !c.opaque.isEmpty()) h.append(", opaque=\"").append(c.opaque).append("\"");
        return h.toString();
    }

    private DigestChallenge parseChallenge(String header) {
        if (header == null || !header.toLowerCase(Locale.US).contains("digest")) throw new IllegalStateException("Challenge Digest Philips absent");
        Map<String, String> parts = new HashMap<>();
        Matcher m = DIGEST_PAIR.matcher(header);
        while (m.find()) parts.put(m.group(1).toLowerCase(Locale.US), m.group(2) != null ? m.group(2) : m.group(3));
        String realm = parts.get("realm"), nonce = parts.get("nonce");
        if (realm == null || nonce == null) throw new IllegalStateException("Challenge Digest invalide");
        String qop = null, raw = parts.get("qop");
        if (raw != null) {
            for (String candidate : raw.split(",")) if ("auth".equalsIgnoreCase(candidate.trim())) { qop = "auth"; break; }
            if (qop == null && !raw.trim().isEmpty()) qop = raw.trim();
        }
        return new DigestChallenge(realm, nonce, qop, parts.getOrDefault("algorithm", "MD5"), parts.get("opaque"));
    }

    private String randomHex(int bytes) {
        byte[] data = new byte[bytes]; random.nextBytes(data);
        StringBuilder out = new StringBuilder();
        for (byte b : data) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private static String md5(String value) throws Exception {
        byte[] result = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.ISO_8859_1));
        StringBuilder out = new StringBuilder();
        for (byte b : result) out.append(String.format(Locale.US, "%02x", b & 0xff));
        return out.toString();
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = r.readLine()) != null) out.append(line);
        }
        return out.toString();
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
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static final class RawResponse {
        final int code; final String body; final String wwwAuthenticate;
        RawResponse(int code, String body, String wwwAuthenticate) { this.code = code; this.body = body; this.wwwAuthenticate = wwwAuthenticate; }
    }
    private static final class DigestChallenge {
        final String realm, nonce, qop, algorithm, opaque;
        DigestChallenge(String realm, String nonce, String qop, String algorithm, String opaque) {
            this.realm = realm; this.nonce = nonce; this.qop = qop; this.algorithm = algorithm; this.opaque = opaque;
        }
    }
}
