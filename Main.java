import com.sun.net.httpserver.HttpServer;
import controller.QuotesController;
import service.QuotesService;
import service.SearchService;
import java.net.InetSocketAddress;
import java.util.concurrent.*;

public class Main {
    public static void main(String[] args) {
        try {
            // 1. DYNAMIC PORT: Render assigns a port (default 8080 locally)
            int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "10000"));
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            System.out.println("VyapaarX Backend Starting on Port: " + port);

            // 2. MASTER DATA SYNC: Start downloading 50,000+ symbols in background
            // Iske bina Universal Search kaam nahi karega
            SearchService.initMasterData();

            // 3. BACKGROUND WORKER: Live Market Data Refresh (Every 1.5 Seconds)
            // Ye Nifty/BankNifty aur Watchlist stocks ka LTP update karta rahega
            ScheduledExecutorService marketEngine = Executors.newSingleThreadScheduledExecutor();
            marketEngine.scheduleAtFixedRate(QuotesService::refreshQuotes, 2, 1500, TimeUnit.MILLISECONDS);

            // 4. API ENDPOINTS (Routing)
            // Dono raste (Search aur Quotes) ab QuotesController ke paas jayenge
            server.createContext("/quotes", QuotesController::handle);
            server.createContext("/search", QuotesController::handle);
            
            // Health Check: Render ko batane ke liye ki server zinda hai
            server.createContext("/health", ex -> {
                String response = "{\"status\":\"online\", \"message\":\"VyapaarX Engine is Roaring!\"}";
                byte[] bytes = response.getBytes();
                ex.getResponseHeaders().set("Content-Type", "application/json");
                ex.sendResponseHeaders(200, bytes.length);
                ex.getResponseBody().write(bytes);
                ex.getResponseBody().close();
            });

            // 5. THREAD POOL: Taaki multiple users ek saath app chalayein toh hang na ho
            server.setExecutor(Executors.newFixedThreadPool(10));
            
            server.start();
            System.out.println("✅ VyapaarX Institutional-Grade Backend is LIVE!");

        } catch (Exception e) {
            System.err.println("❌ Critical Server Failure: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
