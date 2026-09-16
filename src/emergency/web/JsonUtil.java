package emergency.web;

import emergency.model.DispatchPlan;
import emergency.model.EmergencyResource;
import emergency.model.Incident;
import emergency.model.Zone;
import emergency.system.DispatchSystem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight, zero-dependency JSON utility for the Emergency Dispatch web server.
 */
public final class JsonUtil {

    private JsonUtil() { }

    public static String escape(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static String toJson(EmergencyResource r) {
        if (r == null) return "null";
        return "{"
                + "\"id\":\"" + escape(r.id()) + "\","
                + "\"name\":\"" + escape(r.name()) + "\","
                + "\"type\":\"" + r.type().name() + "\","
                + "\"x\":" + r.x() + ","
                + "\"y\":" + r.y() + ","
                + "\"available\":" + r.isAvailable() + ","
                + "\"icuBeds\":" + r.icuBeds()
                + "}";
    }

    public static String toJson(Zone z) {
        if (z == null) return "null";
        return "{"
                + "\"name\":\"" + escape(z.name()) + "\","
                + "\"minX\":" + z.area().minX() + ","
                + "\"minY\":" + z.area().minY() + ","
                + "\"maxX\":" + z.area().maxX() + ","
                + "\"maxY\":" + z.area().maxY()
                + "}";
    }

    public static String toJson(Incident inc) {
        if (inc == null) return "null";
        return "{"
                + "\"id\":\"" + escape(inc.id()) + "\","
                + "\"x\":" + inc.x() + ","
                + "\"y\":" + inc.y() + ","
                + "\"type\":\"" + inc.type().name() + "\","
                + "\"severity\":\"" + inc.severity().name() + "\","
                + "\"severityLabel\":\"" + escape(inc.severity().label()) + "\","
                + "\"ambulancesNeeded\":" + inc.severity().ambulancesNeeded() + ","
                + "\"requiresIcu\":" + inc.severity().requiresIcu()
                + "}";
    }

    public static String resourcesToJson(List<EmergencyResource> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String zonesToJson(List<Zone> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(toJson(list.get(i)));
        }
        sb.append("]");
        return sb.toString();
    }

    public static String stringsToJson(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(list.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String dispatchPlanToJson(DispatchPlan plan) {
        if (plan == null) return "null";
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"incident\":").append(toJson(plan.incident())).append(",");
        sb.append("\"zone\":").append(plan.zone() == null ? "null" : toJson(plan.zone())).append(",");

        sb.append("\"ambulances\":[");
        for (int i = 0; i < plan.ambulances().size(); i++) {
            if (i > 0) sb.append(",");
            EmergencyResource a = plan.ambulances().get(i);
            double dx = a.x() - plan.incident().x();
            double dy = a.y() - plan.incident().y();
            double dist = Math.sqrt(dx * dx + dy * dy);
            sb.append("{\"resource\":").append(toJson(a))
              .append(",\"distance\":").append(String.format(java.util.Locale.US, "%.2f", dist))
              .append("}");
        }
        sb.append("],");

        if (plan.hospital() != null) {
            EmergencyResource h = plan.hospital();
            double dx = h.x() - plan.incident().x();
            double dy = h.y() - plan.incident().y();
            double dist = Math.sqrt(dx * dx + dy * dy);
            sb.append("\"hospital\":{\"resource\":").append(toJson(h))
              .append(",\"distance\":").append(String.format(java.util.Locale.US, "%.2f", dist))
              .append("},");
        } else {
            sb.append("\"hospital\":null,");
        }

        if (plan.fireTruck() != null) {
            EmergencyResource f = plan.fireTruck();
            double dx = f.x() - plan.incident().x();
            double dy = f.y() - plan.incident().y();
            double dist = Math.sqrt(dx * dx + dy * dy);
            sb.append("\"fireTruck\":{\"resource\":").append(toJson(f))
              .append(",\"distance\":").append(String.format(java.util.Locale.US, "%.2f", dist))
              .append("},");
        } else {
            sb.append("\"fireTruck\":null,");
        }

        if (plan.police() != null) {
            EmergencyResource p = plan.police();
            double dx = p.x() - plan.incident().x();
            double dy = p.y() - plan.incident().y();
            double dist = Math.sqrt(dx * dx + dy * dy);
            sb.append("\"police\":{\"resource\":").append(toJson(p))
              .append(",\"distance\":").append(String.format(java.util.Locale.US, "%.2f", dist))
              .append("},");
        } else {
            sb.append("\"police\":null,");
        }

        sb.append("\"unmet\":").append(stringsToJson(plan.unmet()));
        sb.append("}");
        return sb.toString();
    }

    public static String coverageReportToJson(DispatchSystem city, double icuRadiusKm) {
        StringBuilder sb = new StringBuilder("[");
        List<Zone> zones = city.zones();
        for (int i = 0; i < zones.size(); i++) {
            if (i > 0) sb.append(",");
            Zone z = zones.get(i);
            double cx = (z.area().minX() + z.area().maxX()) / 2.0;
            double cy = (z.area().minY() + z.area().maxY()) / 2.0;
            int ambulances = city.availableAmbulancesInRegion(z.area()).size();
            boolean icu = !city.hospitalsWithIcuWithinRadius(cx, cy, icuRadiusKm).isEmpty();
            EmergencyResource nearest = city.nearestAvailableAmbulance(cx, cy);
            double dist = -1.0;
            String nearestId = null;
            if (nearest != null) {
                nearestId = nearest.id();
                double dx = nearest.x() - cx, dy = nearest.y() - cy;
                dist = Math.sqrt(dx * dx + dy * dy);
            }
            boolean underCovered = (ambulances == 0);

            sb.append("{")
              .append("\"zone\":\"").append(escape(z.name())).append("\",")
              .append("\"ambulances\":").append(ambulances).append(",")
              .append("\"hasIcu\":").append(icu).append(",")
              .append("\"nearestAmbulance\":").append(nearestId == null ? "null" : "\"" + escape(nearestId) + "\"").append(",")
              .append("\"nearestDistance\":").append(dist >= 0 ? String.format(java.util.Locale.US, "%.2f", dist) : "null").append(",")
              .append("\"underCovered\":").append(underCovered)
              .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    public static String cityStateToJson(DispatchSystem city) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"zones\":").append(zonesToJson(city.zones())).append(",");
        sb.append("\"resources\":").append(resourcesToJson(city.resources())).append(",");
        sb.append("\"coverage\":").append(coverageReportToJson(city, 8.0));
        sb.append("}");
        return sb.toString();
    }

    public static String success(String message, String extraJson) {
        StringBuilder sb = new StringBuilder("{\"success\":true");
        if (message != null) {
            sb.append(",\"message\":\"").append(escape(message)).append("\"");
        }
        if (extraJson != null && !extraJson.trim().isEmpty()) {
            sb.append(",").append(extraJson);
        }
        sb.append("}");
        return sb.toString();
    }

    public static String error(String message) {
        return "{\"success\":false,\"error\":\"" + escape(message) + "\"}";
    }

    /** Simple key-value parser for flat/top-level JSON object. */
    public static Map<String, String> parseFlatObject(String json) {
        Map<String, String> map = new HashMap<String, String>();
        if (json == null) return map;
        String s = json.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);

        boolean inQuotes = false;
        StringBuilder token = new StringBuilder();
        List<String> pairs = new java.util.ArrayList<String>();

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
                token.append(c);
            } else if (c == ',' && !inQuotes) {
                pairs.add(token.toString().trim());
                token.setLength(0);
            } else {
                token.append(c);
            }
        }
        if (token.length() > 0 && !token.toString().trim().isEmpty()) {
            pairs.add(token.toString().trim());
        }

        for (String pair : pairs) {
            int colon = pair.indexOf(':');
            if (colon > 0) {
                String key = cleanToken(pair.substring(0, colon));
                String val = cleanToken(pair.substring(colon + 1));
                map.put(key, val);
            }
        }
        return map;
    }

    private static String cleanToken(String t) {
        String s = t.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1);
            s = s.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }
}
