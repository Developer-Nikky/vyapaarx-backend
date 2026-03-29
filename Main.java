import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                sendText(exchange, 200, "VyapaarX Backend Running");
            }
        });

        server.createContext("/health", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                sendJson(exchange, 200, "{\"status\":\"ok\",\"service\":\"vyapaarx-backend\"}");
            }
        });

        server.createContext("/price", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                handlePrice(exchange);
            }
        });

        server.setExecutor(null);
        server.start();

        System.out.println("Server started on port " + port);
    }

    private static void handlePrice(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String accessToken = System.getenv("UPSTOX_ACCESS_TOKEN");
        if (accessToken == null || accessToken.isBlank()) {
            sendJson(exchange, 500, "{\"error\":\"Missing UPSTOX_ACCESS_TOKEN env var\"}");
            return;
        }

        String instrumentKey = getQueryParam(exchange.getRequestURI(), "instrument_key");
        if (instrumentKey == null || instrumentKey.isBlank()) {
            instrumentKey = "NSE_INDEX|Nifty 50";
        }

        String apiUrl = "https://api.upstox.com/v2/market-quote/ltp?instrument_key="
                + urlEncode(instrumentKey);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);

            int statusCode = conn.getResponseCode();
            String responseBody = readResponseBody(
                    statusCode >= 200 && statusCode < 300
                            ? conn.getInputStream()
                            : conn.getErrorStream()
            );

            sendJson(exchange, statusCode, responseBody);

        } catch (Exception e) {
            String safeMessage = escapeJson(e.getMessage() == null ? "Unknown error" : e.getMessage());
            sendJson(exchange, 500, "{\"error\":\"" + safeMessage + "\"}");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String getQueryParam(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf('=');
            if (idx <= 0) {
                continue;
            }

            String k = decode(pair.substring(0, idx));
            if (key.equals(k)) {
                return decode(pair.substring(idx + 1));
            }
        }
        return null;
    }

    private static String readResponseBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static void sendText(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decode(String value) {
        return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
