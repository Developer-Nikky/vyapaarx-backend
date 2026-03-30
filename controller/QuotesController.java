package controller;

import com.sun.net.httpserver.HttpExchange;
import service.QuotesService;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class QuotesController {
    public static void handle(HttpExchange ex) {
        try {
            // Security Check
            String auth = ex.getRequestHeaders().getFirst("Authorization");
            if (auth == null || !auth.equals("Bearer VyapaarX_Alpha_2026")) {
                send(ex, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }

            // Check if App is requesting new symbols via ?symbols=NSE_EQ|RELIANCE
            String query = ex.getRequestURI().getQuery();
            if (query != null && query.contains("symbols=")) {
                String symbols = query.split("symbols=")[1];
                QuotesService.addSymbols(URLDecoder.decode(symbols, StandardCharsets.UTF_8));
            }

            byte[] b = QuotesService.getQuotesFromCache().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.getResponseBody().close();
            
        } catch (Exception e) { /* log error */ }
    }
    
    private static void send(HttpExchange ex, int code, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.getResponseBody().close();
    }
}
