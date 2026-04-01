package controller;

import com.sun.net.httpserver.HttpExchange;
import service.TradeService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class TradeController {

    public static void handle(HttpExchange exchange) throws IOException {
        try {
            String userId = exchange.getRequestHeaders().getFirst("X-USER-ID");
            if (userId == null || userId.isBlank()) {
                send(exchange, 401, error("Missing user id"));
                return;
            }

            String method = exchange.getRequestMethod();

            if ("GET".equalsIgnoreCase(method)) {
                send(exchange, 200, TradeService.getPortfolio(userId));
                return;
            }

            if ("POST".equalsIgnoreCase(method)) {
                String body = readBody(exchange);
                send(exchange, 200, TradeService.executeTrade(userId, body));
                return;
            }

            send(exchange, 405, error("Method not allowed"));

        } catch (Exception e) {
            send(exchange, 500, error(e.getMessage()));
        }
    }

    private static String readBody(HttpExchange ex) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(ex.getRequestBody(), StandardCharsets.UTF_8)
        );
        StringBuilder body = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            body.append(line);
        }
        return body.toString();
    }

    private static void send(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String error(String msg) {
        return "{\"success\":false,\"error\":\"" + escape(msg) + "\"}";
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
