package controller;

import com.sun.net.httpserver.HttpExchange;
import service.QuotesService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class QuotesController {

    public static void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();

            if (!"GET".equalsIgnoreCase(method)) {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }

            String keys = getQueryParam(exchange.getRequestURI(), "keys");

            String response = QuotesService.getQuotes(keys);
            sendJson(exchange, 200, response);

        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String getQueryParam(URI uri, String key) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) return null;

        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
