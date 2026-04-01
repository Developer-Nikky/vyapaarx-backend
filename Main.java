import com.sun.net.httpserver.HttpServer;
import controller.InstrumentsController;
import controller.MarketController;
import controller.QuotesController;
import controller.TradeController;
import controller.UserController;
import service.InstrumentsService;
import service.QuotesService;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        InstrumentsService.bootstrapDefaults();
        InstrumentsService.syncMasterIfConfigured();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                QuotesService.warmCriticalQuotes();
            } catch (Exception e) {
                System.out.println("Quote warmup failed: " + e.getMessage());
            }
        }, 0, 2, TimeUnit.SECONDS);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                InstrumentsService.syncMasterIfConfigured();
            } catch (Exception e) {
                System.out.println("Instrument sync failed: " + e.getMessage());
            }
        }, 30, 360, TimeUnit.MINUTES);

        server.createContext("/quotes", QuotesController::handle);
        server.createContext("/quotes/batch", QuotesController::handle);
        server.createContext("/market/status", MarketController::handle);
        server.createContext("/user/me", UserController::handle);
        server.createContext("/trade", TradeController::handle);

        server.createContext("/instruments/search", InstrumentsController::handle);
        server.createContext("/instruments/indices", InstrumentsController::handle);
        server.createContext("/instruments/sync", InstrumentsController::handle);

        server.createContext("/health", exchange -> {
            String response = "{"
                    + "\"success\":true,"
                    + "\"status\":\"ok\","
                    + "\"service\":\"vyapaarx-backend\","
                    + "\"timestamp\":" + System.currentTimeMillis() + ","
                    + "\"instruments\":" + InstrumentsService.getHealthStatsJson()
                    + "}";

            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        server.setExecutor(Executors.newFixedThreadPool(24));
        server.start();

        System.out.println("VyapaarX Backend Live on Port: " + port);
    }
}
