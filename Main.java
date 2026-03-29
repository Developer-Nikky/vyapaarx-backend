import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Root
        server.createContext("/", exchange -> {
            if (handleHead(exchange)) return;
            sendText(exchange, "VyapaarX Backend Running 🚀");
        });

        // Health
        server.createContext("/health", exchange -> {
            if (handleHead(exchange)) return;
            sendJson(exchange, "{\"status\":\"ok\"}");
        });

        // 🔥 BULK QUOTES (MAIN API)
        server.createContext("/quotes", exchange -> {
            try {
                if (handleHead(exchange)) return;

                String accessToken = System.getenv("eyJ0eXAiOiJKV1QiLCJrZXlfaWQiOiJza192MS4wIiwiYWxnIjoiSFMyNTYifQ.eyJzdWIiOiIzWUFIMjQiLCJqdGkiOiI2OWM4ZDY1N2FkNjUxNzZmNWQzNjU1ZjkiLCJpc011bHRpQ2xpZW50IjpmYWxzZSwiaXNQbHVzUGxhbiI6dHJ1ZSwiaWF0IjoxNzc0NzY5NzUxLCJpc3MiOiJ1ZGFwaS1nYXRld2F5LXNlcnZpY2UiLCJleHAiOjE3NzQ4MjE2MDB9.jq4B_VqnKasNfTnLCePiTXTvmtFyZkZmtM6rPh5Dj9Q");
                if (accessToken == null || accessToken.isBlank()) {
                    sendJson(exchange, "{\"error\":\"Missing token\"}");
                    return;
                }

                String keys = getQueryParam(exchange.getRequestURI(), "keys");

                if (keys == null || keys.isBlank()) {
                    // default indices
                    keys = "NSE_INDEX|Nifty 50,NSE_INDEX|Nifty Bank";
                }

                String apiUrl = "https://api.upstox.com/v2/market-quote/ltp?instrument_key=" +
                        URLEncoder.encode(keys, StandardCharsets.UTF_8);

                HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Authorization", "Bearer " + accessToken);
                conn.setRequestProperty("Accept", "application/json");

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream())
                );

                String line;
                StringBuilder response = new StringBuilder();

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();

                sendJson(exchange, response.toString());

            } catch (Exception e) {
                sendJson(exchange, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Server started on port " + port);
    }

    // ================= HELPERS =================

    private static boolean handleHead(HttpExchange exchange) throws IOException {
        if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
            return true;
        }
        return false;
    }

    private static void sendText(HttpExchange exchange, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static void sendJson(HttpExchange exchange, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String getQueryParam(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null) return null;

        for (String param : query.split("&")) {
            String[] pair = param.split("=");
            if (pair.length == 2 && pair[0].equals(key)) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
