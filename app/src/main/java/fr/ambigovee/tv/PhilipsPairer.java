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
import java.util.concurrent.atomic.AtomicInteger;
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
 * Association Philips JointSpace.
 *
 * Fonctionnement :
 * 1. Demande l'association à la TV.
 * 2. La TV affiche un PIN.
 * 3. Le PIN est signé en HMAC-SHA256.
 * 4. La TV renvoie les identifiants JointSpace.
 *
 * L'application tourne directement sur la TV Philips,
 * donc localhost / IP locale sont utilisés.
 */
final class PhilipsPairer {

    private static final String SECRET =
            "JCqdN5AcnAHgJYseUn7ER5k3qgtemfUvMRghQpTfTZq7Cvv8EPQPqfz6dDxPQPSu4gKFPWkJGw32zyASgJkHwCjU";

    private static final Pattern PAIR = Pattern.compile(
            "([a-zA-Z0-9_]+)=(?:\\\"([^\\\"]*)\\\"|([^,\\s]+))"
    );

    static final class Pending {

        final String base;
        final String deviceId;
        final String authKey;
        final long timestamp;

        Pending(
                String base,
                String deviceId,
                String authKey,
                long timestamp
        ) {
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

        Result(
                String ip,
                String user,
                String key
        ) {
            this.ip = ip;
            this.user = user;
            this.key = key;
        }
    }

    private final SSLSocketFactory ssl;

    private final HostnameVerifier verifier =
            (hostname, session) -> true;

    private final SecureRandom random =
            new SecureRandom();

    PhilipsPairer() throws Exception {

        TrustManager[] trustAll =
                new TrustManager[]{
                        new X509TrustManager() {

                            @Override
                            public void checkClientTrusted(
                                    X509Certificate[] chain,
                                    String authType
                            ) {
                            }

                            @Override
                            public void checkServerTrusted(
                                    X509Certificate[] chain,
                                    String authType
                            ) {
                            }

                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }
                        }
                };

        SSLContext context =
                SSLContext.getInstance("TLS");

        context.init(
                null,
                trustAll,
                new SecureRandom()
        );

        ssl = context.getSocketFactory();
    }

    /**
     * Démarre l'association Philips.
     */
    Pending begin() throws Exception {

        String local =
                PhilipsClient.localIpv4();

        String[] hosts;

        if (local != null
                && !local.trim().isEmpty()) {

            hosts = new String[]{
                    local,
                    "127.0.0.1"
            };

        } else {

            hosts = new String[]{
                    "127.0.0.1"
            };
        }

        Exception last = null;

        for (String host : hosts) {

            try {

                return beginAt(host);

            } catch (Exception e) {

                last = e;
            }
        }

        if (last != null) {
            throw last;
        }

        throw new IllegalStateException(
                "TV Philips introuvable"
        );
    }

    /**
     * Demande à JointSpace d'afficher le PIN.
     */
    private Pending beginAt(
            String host
    ) throws Exception {

        String base =
                "https://" + host + ":1926";

        String deviceId =
                randomId();

        JSONObject body =
                new JSONObject();

        body.put(
                "scope",
                new JSONArray()
                        .put("read")
                        .put("write")
                        .put("control")
        );

        body.put(
                "device",
                device(deviceId)
        );

        Raw response =
                post(
                        base,
                        "/6/pair/request",
                        body.toString(),
                        null
                );

        if (response.code < 200
                || response.code >= 300) {

            throw new IllegalStateException(
                    "Pair request HTTP "
                            + response.code
            );
        }

        JSONObject json =
                new JSONObject(response.body);

        String error =
                json.optString(
                        "error_id",
                        ""
                );

        if (!"SUCCESS".equalsIgnoreCase(error)) {

            throw new IllegalStateException(
                    json.optString(
                            "error_text",
                            "Association Philips refusée"
                    )
            );
        }

        String authKey =
                json.getString(
                        "auth_key"
                );

        long timestamp =
                json.getLong(
                        "timestamp"
                );

        return new Pending(
                base,
                deviceId,
                authKey,
                timestamp
        );
    }

    /**
     * Valide le PIN affiché par la TV.
     */
    Result grant(
            Pending pending,
            String pin
    ) throws Exception {

        if (pending == null) {
            throw new IllegalStateException(
                    "Association expirée. Relance l'association."
            );
        }

        if (pin == null) {
            throw new IllegalArgumentException(
                    "PIN invalide"
            );
        }

        pin = pin.trim();

        if (pin.length() < 4) {
            throw new IllegalArgumentException(
                    "PIN invalide"
            );
        }

        JSONObject auth =
                new JSONObject();

        auth.put(
                "auth_AppId",
                "1"
        );

        auth.put(
                "pin",
                pin
        );

        auth.put(
                "auth_timestamp",
                pending.timestamp
        );

        auth.put(
                "auth_signature",
                signature(
                        pending.timestamp,
                        pin
                )
        );

        JSONObject body =
                new JSONObject();

        body.put(
                "auth",
                auth
        );

        body.put(
                "device",
                device(
                        pending.deviceId
                )
        );

        String path =
                "/6/pair/grant";

        Raw first =
                post(
                        pending.base,
                        path,
                        body.toString(),
                        null
                );

        Raw response =
                first;

        /*
         * Certaines Philips demandent une authentification
         * HTTP Digest sur /pair/grant.
         */
        if (first.code
                == HttpURLConnection.HTTP_UNAUTHORIZED) {

            Digest digest =
                    parse(
                            first.challenge
                    );

            AtomicInteger nonceCount =
                    new AtomicInteger(1);

            String authorization =
                    digestHeader(
                            digest,
                            "POST",
                            path,
                            pending.deviceId,
                            pending.authKey,
                            nonceCount
                    );

            response =
                    post(
                            pending.base,
                            path,
                            body.toString(),
                            authorization
                    );

            /*
             * Le nonce peut changer après la première tentative.
             */
            if (response.code
                    == HttpURLConnection.HTTP_UNAUTHORIZED) {

                digest =
                        parse(
                                response.challenge
                        );

                authorization =
                        digestHeader(
                                digest,
                                "POST",
                                path,
                                pending.deviceId,
                                pending.authKey,
                                new AtomicInteger(1)
                        );

                response =
                        post(
                                pending.base,
                                path,
                                body.toString(),
                                authorization
                        );
            }
        }

        if (response.code < 200
                || response.code >= 300) {

            throw new IllegalStateException(
                    "Pair grant HTTP "
                            + response.code
            );
        }

        JSONObject json =
                new JSONObject(
                        response.body
                );

        if (json.has("error_id")
                && !"SUCCESS".equalsIgnoreCase(
                        json.optString(
                                "error_id",
                                ""
                        )
                )) {

            throw new IllegalStateException(
                    json.optString(
                            "error_text",
                            "PIN Philips refusé"
                    )
            );
        }

        String ip =
                pending.base
                        .replace(
                                "https://",
                                ""
                        )
                        .replace(
                                ":1926",
                                ""
                        );

        return new Result(
                ip,
                pending.deviceId,
                pending.authKey
        );
    }

    /**
     * Informations de l'application envoyées à la Philips.
     */
    private JSONObject device(
            String id
    ) throws Exception {

        JSONObject device =
                new JSONObject();

        device.put(
                "device_name",
                "AmbiGovee TV"
        );

        device.put(
                "device_os",
                "Android TV"
        );

        device.put(
                "app_name",
                "AmbiGovee"
        );

        device.put(
                "type",
                "native"
        );

        device.put(
                "app_id",
                "fr.ambigovee.tv"
        );

        device.put(
                "id",
                id
        );

        return device;
    }

    /**
     * Signature demandée par JointSpace.
     *
     * IMPORTANT :
     * Philips utilise ici HMAC-SHA256.
     */
    private String signature(
            long timestamp,
            String pin
    ) throws Exception {

        byte[] key =
                Base64.decode(
                        SECRET,
                        Base64.DEFAULT
                );

        Mac mac =
                Mac.getInstance(
                        "HmacSHA256"
                );

        mac.init(
                new SecretKeySpec(
                        key,
                        "HmacSHA256"
                )
        );

        byte[] digest =
                mac.doFinal(
                        (
                                String.valueOf(timestamp)
                                        + pin
                        ).getBytes(
                                StandardCharsets.UTF_8
                        )
                );

        StringBuilder hex =
                new StringBuilder();

        for (byte value : digest) {

            hex.append(
                    String.format(
                            Locale.US,
                            "%02x",
                            value & 0xff
                    )
            );
        }

        /*
         * JointSpace attend le hash HEX
         * ensuite encodé en Base64.
         */
        return Base64.encodeToString(
                hex.toString().getBytes(
                        StandardCharsets.UTF_8
                ),
                Base64.NO_WRAP
        );
    }

    /**
     * Requête HTTPS JointSpace.
     */
    private Raw post(
            String base,
            String path,
            String body,
            String authorization
    ) throws Exception {

        HttpsURLConnection connection =
                (HttpsURLConnection)
                        new URL(
                                base + path
                        ).openConnection();

        connection.setSSLSocketFactory(
                ssl
        );

        connection.setHostnameVerifier(
                verifier
        );

        connection.setRequestMethod(
                "POST"
        );

        connection.setDoOutput(
                true
        );

        connection.setConnectTimeout(
                1500
        );

        connection.setReadTimeout(
                3000
        );

        connection.setUseCaches(
                false
        );

        connection.setRequestProperty(
                "Content-Type",
                "application/json"
        );

        connection.setRequestProperty(
                "Accept",
                "application/json"
        );

        connection.setRequestProperty(
                "Connection",
                "close"
        );

        if (authorization != null
                && !authorization.isEmpty()) {

            connection.setRequestProperty(
                    "Authorization",
                    authorization
            );
        }

        try (
                OutputStream output =
                        connection.getOutputStream()
        ) {

            output.write(
                    body.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            output.flush();
        }

        int code =
                connection.getResponseCode();

        String challenge =
                connection.getHeaderField(
                        "WWW-Authenticate"
                );

        InputStream stream =
                code >= 200
                        && code < 400
                        ? connection.getInputStream()
                        : connection.getErrorStream();

        String text =
                read(stream);

        connection.disconnect();

        return new Raw(
                code,
                text,
                challenge
        );
    }

    /**
     * Génère l'Authorization HTTP Digest.
     *
     * Le MD5 utilisé ici est NORMAL :
     * il n'a rien à voir avec la signature HMAC du PIN.
     */
    private String digestHeader(
            Digest digest,
            String method,
            String uri,
            String user,
            String key,
            AtomicInteger count
    ) throws Exception {

        String cnonce =
                randomId();

        String nc =
                String.format(
                        Locale.US,
                        "%08x",
                        count.getAndIncrement()
                );

        String ha1 =
                md5(
                        user
                                + ":"
                                + digest.realm
                                + ":"
                                + key
                );

        if ("MD5-sess".equalsIgnoreCase(
                digest.algorithm
        )) {

            ha1 =
                    md5(
                            ha1
                                    + ":"
                                    + digest.nonce
                                    + ":"
                                    + cnonce
                    );
        }

        String ha2 =
                md5(
                        method
                                + ":"
                                + uri
                );

        String response;

        if (digest.qop != null) {

            response =
                    md5(
                            ha1
                                    + ":"
                                    + digest.nonce
                                    + ":"
                                    + nc
                                    + ":"
                                    + cnonce
                                    + ":"
                                    + digest.qop
                                    + ":"
                                    + ha2
                    );

        } else {

            response =
                    md5(
                            ha1
                                    + ":"
                                    + digest.nonce
                                    + ":"
                                    + ha2
                    );
        }

        StringBuilder header =
                new StringBuilder(
                        "Digest username=\""
                );

        header.append(user)
                .append("\"");

        header.append(", realm=\"")
                .append(digest.realm)
                .append("\"");

        header.append(", nonce=\"")
                .append(digest.nonce)
                .append("\"");

        header.append(", uri=\"")
                .append(uri)
                .append("\"");

        header.append(", response=\"")
                .append(response)
                .append("\"");

        if (digest.algorithm != null) {

            header.append(", algorithm=")
                    .append(
                            digest.algorithm
                    );
        }

        if (digest.qop != null) {

            header.append(", qop=")
                    .append(digest.qop);

            header.append(", nc=")
                    .append(nc);

            header.append(", cnonce=\"")
                    .append(cnonce)
                    .append("\"");
        }

        if (digest.opaque != null) {

            header.append(", opaque=\"")
                    .append(digest.opaque)
                    .append("\"");
        }

        return header.toString();
    }

    /**
     * Parse WWW-Authenticate Digest.
     */
    private Digest parse(
            String header
    ) {

        if (header == null
                || header.trim().isEmpty()) {

            throw new IllegalStateException(
                    "Authentification Digest Philips absente"
            );
        }

        Map<String, String> values =
                new HashMap<>();

        Matcher matcher =
                PAIR.matcher(header);

        while (matcher.find()) {

            String key =
                    matcher.group(1)
                            .toLowerCase(
                                    Locale.US
                            );

            String value =
                    matcher.group(2) != null
                            ? matcher.group(2)
                            : matcher.group(3);

            values.put(
                    key,
                    value
            );
        }

        String realm =
                values.get("realm");

        String nonce =
                values.get("nonce");

        if (realm == null
                || nonce == null) {

            throw new IllegalStateException(
                    "Challenge Digest Philips invalide"
            );
        }

        String rawQop =
                values.get("qop");

        String qop =
                null;

        if (rawQop != null) {

            for (
                    String candidate
                    : rawQop.split(",")
            ) {

                if ("auth".equalsIgnoreCase(
                        candidate.trim()
                )) {

                    qop = "auth";
                    break;
                }
            }
        }

        return new Digest(
                realm,
                nonce,
                qop,
                values.getOrDefault(
                        "algorithm",
                        "MD5"
                ),
                values.get("opaque")
        );
    }

    /**
     * Identifiant aléatoire de l'application.
     */
    private String randomId() {

        String chars =
                "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
                        + "abcdefghijklmnopqrstuvwxyz"
                        + "0123456789";

        StringBuilder result =
                new StringBuilder();

        for (int i = 0; i < 16; i++) {

            result.append(
                    chars.charAt(
                            random.nextInt(
                                    chars.length()
                            )
                    )
            );
        }

        return result.toString();
    }

    /**
     * MD5 utilisé uniquement pour HTTP Digest.
     */
    private static String md5(
            String value
    ) throws Exception {

        byte[] result =
                MessageDigest
                        .getInstance("MD5")
                        .digest(
                                value.getBytes(
                                        StandardCharsets.ISO_8859_1
                                )
                        );

        StringBuilder output =
                new StringBuilder();

        for (byte b : result) {

            output.append(
                    String.format(
                            Locale.US,
                            "%02x",
                            b & 0xff
                    )
            );
        }

        return output.toString();
    }

    /**
     * Lecture réponse HTTP.
     */
    private static String read(
            InputStream stream
    ) throws Exception {

        if (stream == null) {
            return "";
        }

        StringBuilder output =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        stream,
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String line;

            while (
                    (line = reader.readLine())
                            != null
            ) {

                output.append(line);
            }
        }

        return output.toString();
    }

    private static final class Raw {

        final int code;
        final String body;
        final String challenge;

        Raw(
                int code,
                String body,
                String challenge
        ) {
            this.code = code;
            this.body = body;
            this.challenge = challenge;
        }
    }

    private static final class Digest {

        final String realm;
        final String nonce;
        final String qop;
        final String algorithm;
        final String opaque;

        Digest(
                String realm,
                String nonce,
                String qop,
                String algorithm,
                String opaque
        ) {
            this.realm = realm;
            this.nonce = nonce;
            this.qop = qop;
            this.algorithm = algorithm;
            this.opaque = opaque;
        }
    }
}
