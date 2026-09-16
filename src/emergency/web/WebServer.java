package emergency.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import emergency.geometry.Rectangle;
import emergency.model.EmergencyResource;
import emergency.model.Incident;
import emergency.model.IncidentType;
import emergency.model.ResourceType;
import emergency.model.Severity;
import emergency.model.Zone;
import emergency.system.DispatchSystem;
import emergency.system.SampleCity;

import java.awt.Desktop;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Embedded HTTP server delivering the Emergency Dispatch Control Center web UI
 * and REST APIs using standard Java SE (com.sun.net.httpserver).
 */
public final class WebServer {

    private static DispatchSystem city = SampleCity.load();
    private static final Object lock = new Object();

    public static void main(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) { }
        }
        start(port, true);
    }

    public static void start(int port, boolean openBrowser) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newCachedThreadPool());

            // API handlers
            server.createContext("/api/state", new StateHandler());
            server.createContext("/api/query/range", new RangeQueryHandler());
            server.createContext("/api/query/nearest", new NearestQueryHandler());
            server.createContext("/api/query/radius", new RadiusQueryHandler());
            server.createContext("/api/query/point-location", new PointLocationHandler());
            server.createContext("/api/dispatch", new DispatchHandler());
            server.createContext("/api/redistribute", new RedistributeHandler());
            server.createContext("/api/return-service", new ReturnServiceHandler());
            server.createContext("/api/reset", new ResetHandler());

            // Static file serving handler
            server.createContext("/", new StaticFileHandler());

            server.start();

            String url = "http://localhost:" + port;
            System.out.println("============================================================");
            System.out.println(" EMERGENCY DISPATCH CONTROL CENTER - WEB FRONTEND");
            System.out.println("============================================================");
            System.out.println(" Server running at : " + url);
            System.out.println(" REST API base     : " + url + "/api/");
            System.out.println(" Press Ctrl+C in terminal to stop the server.");
            System.out.println("============================================================\n");

            if (openBrowser) {
                openWebpage(url);
            }

        } catch (IOException e) {
            System.err.println("[ERROR] Failed to start WebServer on port " + port + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void openWebpage(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", url});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", url});
                } else if (os.contains("nix") || os.contains("nux")) {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", url});
                }
            }
        } catch (Exception ignored) {
            // Non-critical; user can navigate to URL manually
        }
    }

    private static void addCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        addCors(ex);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return baos.toString(StandardCharsets.UTF_8.name());
        }
    }

    /* ------------------------------------------------------------------ */
    /*  API Handlers                                                      */
    /* ------------------------------------------------------------------ */

    private static class StateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                addCors(ex);
                ex.sendResponseHeaders(204, -1);
                return;
            }
            synchronized (lock) {
                sendJson(ex, 200, JsonUtil.cityStateToJson(city));
            }
        }
    }

    private static class RangeQueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                addCors(ex);
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, JsonUtil.error("POST required"));
                return;
            }
            try {
                String body = readBody(ex);
                Map<String, String> map = JsonUtil.parseFlatObject(body);
                double minX = Double.parseDouble(map.getOrDefault("minX", "0"));
                double minY = Double.parseDouble(map.getOrDefault("minY", "0"));
                double maxX = Double.parseDouble(map.getOrDefault("maxX", "30"));
                double maxY = Double.parseDouble(map.getOrDefault("maxY", "30"));

                Rectangle rect = new Rectangle(minX, minY, maxX, maxY);
                List<EmergencyResource> list;
                synchronized (lock) {
                    list = city.availableAmbulancesInRegion(rect);
                }

                String extra = "\"region\":{\"minX\":" + minX + ",\"minY\":" + minY + ",\"maxX\":" + maxX + ",\"maxY\":" + maxY + "},"
                        + "\"count\":" + list.size() + ","
                        + "\"ambulances\":" + JsonUtil.resourcesToJson(list);
                sendJson(ex, 200, JsonUtil.success("Range query evaluated", extra));
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error("Invalid range query parameters: " + e.getMessage()));
            }
        }
    }

    private static class NearestQueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                addCors(ex);
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, JsonUtil.error("POST required"));
                return;
            }
            try {
                String body = readBody(ex);
                Map<String, String> map = JsonUtil.parseFlatObject(body);
                double x = Double.parseDouble(map.get("x"));
                double y = Double.parseDouble(map.get("y"));

                EmergencyResource amb;
                double dist = -1;
                synchronized (lock) {
                    amb = city.nearestAvailableAmbulance(x, y);
                    if (amb != null) {
                        double dx = amb.x() - x, dy = amb.y() - y;
                        dist = Math.sqrt(dx * dx + dy * dy);
                    }
                }

                String extra = "\"query\":{\"x\":" + x + ",\"y\":" + y + "},"
                        + "\"nearest\":" + JsonUtil.toJson(amb) + ","
                        + "\"distance\":" + (dist >= 0 ? String.format(java.util.Locale.US, "%.2f", dist) : "null");
                sendJson(ex, 200, JsonUtil.success(amb != null ? "Nearest ambulance found" : "No available ambulance found", extra));
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error("Invalid nearest query parameters: " + e.getMessage()));
            }
        }
    }

    private static class RadiusQueryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                addCors(ex);
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, JsonUtil.error("POST required"));
                return;
            }
            try {
                String body = readBody(ex);
                Map<String, String> map = JsonUtil.parseFlatObject(body);
                double x = Double.parseDouble(map.get("x"));
                double y = Double.parseDouble(map.get("y"));
                double radius = Double.parseDouble(map.getOrDefault("radiusKm", "5.0"));

                List<EmergencyResource> list;
                synchronized (lock) {
                    list = city.hospitalsWithIcuWithinRadius(x, y, radius);
                }

                StringBuilder hospJson = new StringBuilder("[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) hospJson.append(",");
                    EmergencyResource h = list.get(i);
                    double dx = h.x() - x, dy = h.y() - y;
                    double dist = Math.sqrt(dx * dx + dy * dy);
                    hospJson.append("{\"resource\":").append(JsonUtil.toJson(h))
                            .append(",\"distance\":").append(String.format(java.util.Locale.US, "%.2f", dist))
                            .append("}");
                }
                hospJson.append("]");

                String extra = "\"query\":{\"x\":" + x + ",\"y\":" + y + ",\"radiusKm\":" + radius + "},"
                        + "\"count\":" + list.size() + ","
                        + "\"hospitals\":" + hospJson.toString();
                sendJson(ex, 200, JsonUtil.success("Radius query evaluated", extra));
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error("Invalid radius query parameters: " + e.getMessage()));
            }
        }
    }

    private static class PointLocationHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                addCors(ex);
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, JsonUtil.error("POST required"));
                return;
            }
            try {
                String body = readBody(ex);
                Map<String, String> map = JsonUtil.parseFlatObject(body);
                double x = Double.parseDouble(map.get("x"));
                double y = Double.parseDouble(map.get("y"));

                Zone z;
                synchronized (lock) {
                    z = city.zoneOf(x, y);
                }

                String extra = "\"query\":{\"x\":" + x + ",\"y\":" + y + "},"
                        + "\"zone\":" + (z == null ? "null" : JsonUtil.toJson(z));
                sendJson(ex, 200, JsonUtil.success(z != null ? "Zone identified" : "Outside mapped sectors", extra));
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error("Invalid coordinates: " + e.getMessage()));
            }
        }
    }

    private static class DispatchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                addCors(ex);
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, JsonUtil.error("POST required"));
                return;
            }
            try {
                String body = readBody(ex);
                Map<String, String> map = JsonUtil.parseFlatObject(body);
                String id = map.getOrDefault("id", "INC-" + (System.currentTimeMillis() % 10000));
                double x = Double.parseDouble(map.get("x"));
                double y = Double.parseDouble(map.get("y"));
                String typeStr = map.getOrDefault("type", "ROAD_ACCIDENT").toUpperCase();
                String sevStr = map.getOrDefault("severity", "CRITICAL").toUpperCase();

                IncidentType type;
                try {
                    type = IncidentType.valueOf(typeStr);
                } catch (Exception e) {
                    type = IncidentType.ROAD_ACCIDENT;
                }

                Severity sev;
                try {
                    sev = Severity.valueOf(sevStr);
                } catch (Exception e) {
                    sev = Severity.CRITICAL;
                }

                Incident inc = new Incident(id, x, y, type, sev);
                emergency.model.DispatchPlan plan;
                String stateJson;
                synchronized (lock) {
                    plan = city.allocate(inc);
                    stateJson = JsonUtil.cityStateToJson(city);
                }

                String extra = "\"plan\":" + JsonUtil.dispatchPlanToJson(plan) + ","
                        + "\"state\":" + stateJson;
                sendJson(ex, 200, JsonUtil.success("Incident dispatched successfully", extra));
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error("Dispatch failed: " + e.getMessage()));
            }
        }
    }

    private static class RedistributeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                addCors(ex);
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod()) && !"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, JsonUtil.error("GET or POST required"));
                return;
            }
            try {
                List<String> moves;
                String stateJson;
                synchronized (lock) {
                    moves = city.redistributeCoverage();
                    stateJson = JsonUtil.cityStateToJson(city);
                }

                String extra = "\"moves\":" + JsonUtil.stringsToJson(moves) + ","
                        + "\"state\":" + stateJson;
                sendJson(ex, 200, JsonUtil.success("Redistribution complete", extra));
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error("Redistribution failed: " + e.getMessage()));
            }
        }
    }

    private static class ReturnServiceHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                addCors(ex);
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, JsonUtil.error("POST required"));
                return;
            }
            try {
                String body = readBody(ex);
                Map<String, String> map = JsonUtil.parseFlatObject(body);
                String id = map.get("resourceId");
                boolean resetAll = "true".equalsIgnoreCase(map.get("all"));

                String stateJson;
                synchronized (lock) {
                    if (resetAll) {
                        for (EmergencyResource r : city.resources()) {
                            if (r.type() != ResourceType.HOSPITAL) {
                                r.setAvailable(true);
                            }
                        }
                    } else if (id != null) {
                        city.returnToService(id);
                    }
                    stateJson = JsonUtil.cityStateToJson(city);
                }

                String extra = "\"state\":" + stateJson;
                sendJson(ex, 200, JsonUtil.success("Resource(s) returned to service", extra));
            } catch (Exception e) {
                sendJson(ex, 400, JsonUtil.error("Return to service failed: " + e.getMessage()));
            }
        }
    }

    private static class ResetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                addCors(ex);
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod()) && !"GET".equalsIgnoreCase(ex.getRequestMethod())) {
                sendJson(ex, 405, JsonUtil.error("GET or POST required"));
                return;
            }
            try {
                String stateJson;
                synchronized (lock) {
                    city = SampleCity.load();
                    stateJson = JsonUtil.cityStateToJson(city);
                }
                String extra = "\"state\":" + stateJson;
                sendJson(ex, 200, JsonUtil.success("City reset to reference dataset", extra));
            } catch (Exception e) {
                sendJson(ex, 500, JsonUtil.error("Reset failed: " + e.getMessage()));
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Static File Handler                                               */
    /* ------------------------------------------------------------------ */

    private static class StaticFileHandler implements HttpHandler {
        private final File webDir = new File("web");

        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isEmpty()) {
                path = "/index.html";
            }

            File file = new File(webDir, path.replace('/', File.separatorChar));
            // Prevent directory traversal
            if (!file.getCanonicalPath().startsWith(webDir.getCanonicalPath()) || !file.exists() || file.isDirectory()) {
                // Fallback to index.html for SPA if file not found
                file = new File(webDir, "index.html");
                if (!file.exists()) {
                    String notFound = "<h1>404 Not Found</h1><p>Web resources directory not found.</p>";
                    byte[] b = notFound.getBytes(StandardCharsets.UTF_8);
                    ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    ex.sendResponseHeaders(404, b.length);
                    try (OutputStream os = ex.getResponseBody()) { os.write(b); }
                    return;
                }
            }

            String mime = getMimeType(file.getName());
            ex.getResponseHeaders().set("Content-Type", mime);
            ex.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            ex.sendResponseHeaders(200, file.length());

            try (InputStream is = new FileInputStream(file);
                 OutputStream os = ex.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = is.read(buffer)) != -1) {
                    os.write(buffer, 0, count);
                }
            }
        }

        private static String getMimeType(String filename) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=utf-8";
            if (lower.endsWith(".css")) return "text/css; charset=utf-8";
            if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
            if (lower.endsWith(".json")) return "application/json; charset=utf-8";
            if (lower.endsWith(".svg")) return "image/svg+xml";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".ico")) return "image/x-icon";
            return "application/octet-stream";
        }
    }
}
