import com.sun.net.httpserver.HttpServer;
import controller.MarketController;
import controller.QuotesController;
import controller.TradeController;
import controller.UserController;
import service.QuotesService;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        ScheduledExecutorService warmWorker = Executors.newSingleThreadScheduledExecutor();
        warmWorker.scheduleAtFixedRate(() -> {
            try {
                QuotesService.warmCriticalQuotes();
            } catch (Exception e) {
                System.out.println("Warmup failed: " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);

        server.createContext("/quotes", QuotesController::handle);
        server.createContext("/market/status", MarketController::handle);
        server.createContext("/user/me", UserController::handle);
        server.createContext("/trade", TradeController::handle);

        server.createContext("/health", exchange -> {
            String response = "{"
                    + "\"success\":true,"
                    + "\"status\":\"ok\","
                    + "\"service\":\"vyapaarx-backend\","
                    + "\"timestamp\":" + System.currentTimeMillis()
                    + "}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("VyapaarX Backend Live on Port: " + port);
    }
}
