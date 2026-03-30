package controller;

import com.sun.net.httpserver.HttpExchange;
import service.QuotesService;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class QuotesController {
    public static void handle(HttpExchange ex) {
        try {
            // Static Key Security for Phase 1
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !auth.equals("Bearer VyapaarX_Alpha_2026")) {
                send(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }

            send(ex, 200, QuotesService.getQuotesFromCache());
        } catch (Exception e) {
            try { send(ex, 500, "{\"error\":\"Server Error\"}"); } catch (Exception ignored) {}
        }
    }

    private static void send(HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }
}
