package controller;

import com.sun.net.httpserver.HttpExchange;
import service.UserService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class UserController {

    public static void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, error("Method not allowed"));
                return;
            }

            String userId = exchange.getRequestHeaders().getFirst("X-USER-ID");

            if (userId == null || userId.isBlank()) {
                send(exchange, 401, error("Missing user id"));
                return;
            }

            String response = UserService.getOrCreateUser(userId);
            send(exchange, 200, response);

        } catch (Exception e) {
            send(exchange, 500, error(e.getMessage()));
        }
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
        return "{\"success\":false,\"error\":\"" + msg + "\"}";
    }
}
