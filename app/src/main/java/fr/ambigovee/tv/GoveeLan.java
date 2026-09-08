package fr.ambigovee.tv;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Une seule pile UDP pour toutes les lampes Govee.
 * Les réponses LAN Govee reviennent sur le port 4002 : partager un seul socket
 * évite les collisions quand plusieurs lampes sont configurées.
 */
final class GoveeLan {
    private static final int CONTROL_PORT = 4003;
    private static final int RESPONSE_PORT = 4002;
    private static final int SCAN_PORT = 4001;
    private static final String MULTICAST_IP = "239.255.255.250";

    static final class GoveeState {
        final boolean on;
        final int brightness, r, g, b, kelvin;
        final boolean colorCapable;
        GoveeState(boolean on, int brightness, int r, int g, int b, int kelvin, boolean colorCapable) {
            this.on = on; this.brightness = brightness; this.r = r; this.g = g; this.b = b;
            this.kelvin = kelvin; this.colorCapable = colorCapable;
        }
    }

    static final class Device {
        final String ip, device, sku;
        Device(String ip, String device, String sku) {
            this.ip = clean(ip); this.device = clean(device); this.sku = clean(sku);
        }
        String identity() { return !device.isEmpty() ? device.toLowerCase() : ip; }
        @Override public String toString() { return (sku.isEmpty() ? "Govee" : sku) + "  •  " + ip; }
    }

    private final Context context;
    private final DatagramSocket statusSocket;
    private final DatagramSocket sendSocket;
    private final WifiManager.MulticastLock multicastLock;
    private long lastDiscoveryAttempt = 0;
    private List<Device> lastDiscovery = new ArrayList<>();
    private final Map<String,String> runtimeIps = new HashMap<>();

    GoveeLan(Context context) throws Exception {
        this.context = context.getApplicationContext();
        WifiManager wifi = (WifiManager) this.context.getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            multicastLock = wifi.createMulticastLock("AmbiGovee-GoveeRuntime");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } else multicastLock = null;

        statusSocket = new DatagramSocket(null);
        statusSocket.setReuseAddress(true);
        statusSocket.bind(new InetSocketAddress(RESPONSE_PORT));
        statusSocket.setSoTimeout(175);
        sendSocket = new DatagramSocket();
    }

    synchronized GoveeState queryStatus(GoveeConfig target) {
        if (target == null || !target.enabled) return null;

        String ip = runtimeIps.containsKey(target.identity()) ? runtimeIps.get(target.identity()) : target.ip;
        if (!ip.isEmpty()) {
            GoveeState state = queryAt(ip, "devStatus");
            if (state == null) state = queryAt(ip, "status");
            if (state != null) return state;
        }

        Device found = discoverTarget(target);
        if (found != null) {
            runtimeIps.put(target.identity(), found.ip);
            if (!found.ip.equals(target.ip)) ConfigStore.updateGoveeIp(context, target.identity(), found.ip);
            GoveeState state = queryAt(found.ip, "devStatus");
            if (state == null) state = queryAt(found.ip, "status");
            return state;
        }
        return null;
    }

    void setColor(GoveeConfig target, int r, int g, int b) {
        String ip = resolveIp(target);
        if (ip.isEmpty()) return;
        try {
            JSONObject color = new JSONObject();
            color.put("r", clamp(r,0,255)); color.put("g", clamp(g,0,255)); color.put("b", clamp(b,0,255));
            JSONObject data = new JSONObject();
            data.put("color", color); data.put("colorTemInKelvin", 0);
            sendCommand(ip, "colorwc", data);
        } catch (Exception ignored) {}
    }

    void setBrightness(GoveeConfig target, int brightness) {
        String ip = resolveIp(target);
        if (ip.isEmpty()) return;
        try {
            JSONObject d = new JSONObject(); d.put("value", clamp(brightness,1,100));
            sendCommand(ip, "brightness", d);
        } catch (Exception ignored) {}
    }

    void restore(GoveeConfig target, BaseState state) {
        if (state == null) return;
        String ip = resolveIp(target);
        if (ip.isEmpty()) return;
        try {
            JSONObject color = new JSONObject();
            color.put("r", state.r); color.put("g", state.g); color.put("b", state.b);
            JSONObject data = new JSONObject();
            data.put("color", color); data.put("colorTemInKelvin", state.kelvin);
            sendCommand(ip, "colorwc", data);
            Thread.sleep(22);
            JSONObject b = new JSONObject(); b.put("value", clamp(state.brightness,1,100));
            sendCommand(ip, "brightness", b);
        } catch (Exception ignored) {}
    }

    private String resolveIp(GoveeConfig target) {
        if (target == null) return "";
        String cached = runtimeIps.get(target.identity());
        if (cached != null && !cached.isEmpty()) return cached;
        if (!target.ip.isEmpty()) return target.ip;
        Device d = discoverTarget(target);
        if (d != null) {
            runtimeIps.put(target.identity(), d.ip);
            ConfigStore.updateGoveeIp(context, target.identity(), d.ip);
            return d.ip;
        }
        return "";
    }

    private synchronized GoveeState queryAt(String ip, String command) {
        try {
            drain();
            sendStatusCommand(ip, command, new JSONObject());
            long deadline = System.nanoTime() + 210_000_000L;
            byte[] buffer = new byte[8192];
            while (System.nanoTime() < deadline) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                statusSocket.receive(packet);
                if (!packet.getAddress().getHostAddress().equals(ip)) continue;
                JSONObject root = new JSONObject(new String(packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8));
                JSONObject msg = root.optJSONObject("msg");
                JSONObject data = msg != null ? msg.optJSONObject("data") : null;
                if (data == null || !data.has("onOff")) continue;
                JSONObject color = data.optJSONObject("color");
                return new GoveeState(
                        data.optInt("onOff",0)==1,
                        data.optInt("brightness",100),
                        color != null ? color.optInt("r",255) : 255,
                        color != null ? color.optInt("g",255) : 255,
                        color != null ? color.optInt("b",255) : 255,
                        data.optInt("colorTemInKelvin",0),
                        color != null
                );
            }
        } catch (Exception ignored) {}
        return null;
    }

    private synchronized Device discoverTarget(GoveeConfig target) {
        try {
            long now = System.currentTimeMillis();
            if (now - lastDiscoveryAttempt > 2500 || lastDiscovery.isEmpty()) {
                lastDiscoveryAttempt = now;
                lastDiscovery = discoverWithSocket(statusSocket, 850);
            }
            for (Device d : lastDiscovery) {
                if (!target.device.isEmpty() && target.device.equalsIgnoreCase(d.device)) return d;
            }
            for (Device d : lastDiscovery) {
                if (!target.ip.isEmpty() && target.ip.equals(d.ip)) return d;
            }
            // SKU seul n'est utilisé que si un seul appareil de ce SKU est présent.
            if (!target.sku.isEmpty()) {
                Device candidate = null;
                for (Device d : lastDiscovery) {
                    if (target.sku.equalsIgnoreCase(d.sku)) {
                        if (candidate != null) return null;
                        candidate = d;
                    }
                }
                if (candidate != null) return candidate;
            }
        } catch (Exception ignored) {}
        return null;
    }

    static List<Device> discoverDevices(Context context, int timeoutMs) throws Exception {
        WifiManager.MulticastLock lock = null;
        WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi != null) {
            lock = wifi.createMulticastLock("AmbiGovee-SetupDiscovery");
            lock.setReferenceCounted(false); lock.acquire();
        }
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(RESPONSE_PORT));
        try {
            int first = Math.max(900, Math.min(timeoutMs, 1800));
            List<Device> found = discoverWithSocket(socket, first);
            if (!found.isEmpty()) return found;
            return discoverSubnetWithSocket(socket, Math.max(900, timeoutMs - first));
        } finally {
            socket.close();
            if (lock != null && lock.isHeld()) lock.release();
        }
    }

    private static List<Device> discoverSubnetWithSocket(DatagramSocket socket, int timeoutMs) throws Exception {
        List<Device> result = new ArrayList<>();
        String local = PhilipsClient.localIpv4();
        if (local == null) return result;
        int cut = local.lastIndexOf('.');
        if (cut <= 0) return result;
        String prefix = local.substring(0, cut + 1);

        byte[] payload = scanPayload();
        for (int i = 1; i <= 254; i++) {
            String ip = prefix + i;
            if (ip.equals(local)) continue;
            try {
                DatagramPacket p = new DatagramPacket(payload, payload.length, InetAddress.getByName(ip), SCAN_PORT);
                socket.send(p);
            } catch (Exception ignored) {}
        }
        collectScanResponses(socket, timeoutMs, result);
        return result;
    }

    private static List<Device> discoverWithSocket(DatagramSocket socket, int timeoutMs) throws Exception {
        List<Device> result = new ArrayList<>();
        try {
            socket.setSoTimeout(1);
            byte[] b = new byte[256];
            while (true) socket.receive(new DatagramPacket(b,b.length));
        } catch (Exception ignored) {}

        byte[] payload = scanPayload();
        DatagramPacket p = new DatagramPacket(payload, payload.length, InetAddress.getByName(MULTICAST_IP), SCAN_PORT);
        socket.send(p);
        // Deuxième envoi : certains appareils ratent le premier multicast après réveil Wi‑Fi.
        try { Thread.sleep(80); socket.send(p); } catch (Exception ignored) {}
        collectScanResponses(socket, timeoutMs, result);
        return result;
    }

    private static byte[] scanPayload() throws Exception {
        JSONObject reserve = new JSONObject(); reserve.put("account_topic", "reserve");
        JSONObject msg = new JSONObject(); msg.put("cmd", "scan"); msg.put("data", reserve);
        JSONObject root = new JSONObject(); root.put("msg", msg);
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void collectScanResponses(DatagramSocket socket, int timeoutMs, List<Device> result) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        socket.setSoTimeout(160);
        byte[] buf = new byte[8192];
        while (System.currentTimeMillis() < end) {
            try {
                DatagramPacket rx = new DatagramPacket(buf, buf.length);
                socket.receive(rx);
                JSONObject j = new JSONObject(new String(rx.getData(), rx.getOffset(), rx.getLength(), StandardCharsets.UTF_8));
                JSONObject msg = j.optJSONObject("msg");
                JSONObject data = msg != null ? msg.optJSONObject("data") : null;
                if (data == null) continue;
                Device d = new Device(
                        data.optString("ip", rx.getAddress().getHostAddress()),
                        data.optString("device", ""),
                        data.optString("sku", "")
                );
                if (d.ip.isEmpty()) continue;
                boolean duplicate = false;
                for (Device x : result) {
                    if ((!d.device.isEmpty() && d.device.equalsIgnoreCase(x.device)) || d.ip.equals(x.ip)) { duplicate = true; break; }
                }
                if (!duplicate) result.add(d);
            } catch (java.net.SocketTimeoutException ignored) {}
        }
    }

    private synchronized void sendStatusCommand(String ip, String command, JSONObject data) throws Exception {
        JSONObject msg = new JSONObject(); msg.put("cmd", command); msg.put("data", data);
        JSONObject root = new JSONObject(); root.put("msg", msg);
        byte[] payload = root.toString().getBytes(StandardCharsets.UTF_8);
        statusSocket.send(new DatagramPacket(payload, payload.length, InetAddress.getByName(ip), CONTROL_PORT));
    }

    private void sendCommand(String ip, String command, JSONObject data) throws Exception {
        JSONObject msg = new JSONObject(); msg.put("cmd", command); msg.put("data", data);
        JSONObject root = new JSONObject(); root.put("msg", msg);
        byte[] payload = root.toString().getBytes(StandardCharsets.UTF_8);
        sendSocket.send(new DatagramPacket(payload, payload.length, InetAddress.getByName(ip), CONTROL_PORT));
    }

    private synchronized void drain() {
        try {
            statusSocket.setSoTimeout(1);
            byte[] b = new byte[512];
            while (true) statusSocket.receive(new DatagramPacket(b,b.length));
        } catch (Exception ignored) {
        } finally {
            try { statusSocket.setSoTimeout(175); } catch (Exception ignored) {}
        }
    }

    void close() {
        try { statusSocket.close(); } catch (Exception ignored) {}
        try { sendSocket.close(); } catch (Exception ignored) {}
        try { if (multicastLock != null && multicastLock.isHeld()) multicastLock.release(); } catch (Exception ignored) {}
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
