package fr.ambigovee.tv;

import org.json.JSONException;
import org.json.JSONObject;

final class GoveeConfig {
    static final String POSITION_ROOM = "room";
    static final String POSITION_LEFT = "left";
    static final String POSITION_TOP = "top";
    static final String POSITION_RIGHT = "right";
    static final String POSITION_BOTTOM = "bottom";

    final String ip;
    final String device;
    final String sku;
    final String position;
    final String name;
    final boolean enabled;

    GoveeConfig(String ip, String device, String sku, String position, String name, boolean enabled) {
        this.ip = clean(ip);
        this.device = clean(device);
        this.sku = clean(sku);
        this.position = normalizePosition(position);
        this.name = clean(name);
        this.enabled = enabled;
    }

    String identity() {
        if (!device.isEmpty()) return device.toLowerCase();
        if (!ip.isEmpty()) return ip;
        return sku + ":" + position;
    }

    String displayName() {
        if (!name.isEmpty()) return name;
        if (!sku.isEmpty()) return sku;
        return "Govee";
    }

    String positionLabel() {
        switch (position) {
            case POSITION_LEFT: return "Gauche de la TV";
            case POSITION_RIGHT: return "Droite de la TV";
            case POSITION_TOP: return "Au-dessus de la TV";
            case POSITION_BOTTOM: return "Sous la TV";
            default: return "Plafond / pièce entière";
        }
    }

    GoveeConfig withPosition(String p) {
        return new GoveeConfig(ip, device, sku, p, name, enabled);
    }

    GoveeConfig withIp(String newIp) {
        return new GoveeConfig(newIp, device, sku, position, name, enabled);
    }

    GoveeConfig withEnabled(boolean value) {
        return new GoveeConfig(ip, device, sku, position, name, value);
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("ip", ip);
        o.put("device", device);
        o.put("sku", sku);
        o.put("position", position);
        o.put("name", name);
        o.put("enabled", enabled);
        return o;
    }

    static GoveeConfig fromJson(JSONObject o) {
        if (o == null) return null;
        return new GoveeConfig(
                o.optString("ip", ""),
                o.optString("device", ""),
                o.optString("sku", ""),
                o.optString("position", POSITION_ROOM),
                o.optString("name", ""),
                o.optBoolean("enabled", true)
        );
    }

    static String normalizePosition(String p) {
        if (POSITION_LEFT.equals(p) || POSITION_RIGHT.equals(p) || POSITION_TOP.equals(p) || POSITION_BOTTOM.equals(p)) return p;
        return POSITION_ROOM;
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
