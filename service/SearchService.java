package service;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.stream.Collectors;

public class SearchService {
    // 50,000+ symbols yahan store honge
    private static final List<String> MASTER_LIST = new ArrayList<>();

    // Main.java ise call karega server start hote hi
    public static void initMasterData() {
        Thread syncThread = new Thread(() -> {
            try {
                System.out.println("⏳ Starting Master Instruments Sync from Upstox...");
                // Upstox Official Master CSV (GZipped for speed)
                URL url = new URL("https://assets.upstox.com/market-quote/instruments/exchange/complete.csv.gz");
                
                try (InputStream is = url.openStream();
                     GZIPInputStream gzis = new GZIPInputStream(is);
                     BufferedReader br = new BufferedReader(new InputStreamReader(gzis))) {
                    
                    String line;
                    br.readLine(); // Skip CSV Header line
                    
                    int count = 0;
                    while ((line = br.readLine()) != null) {
                        // CSV Format: instrument_key, exchange_token, trading_symbol, name, last_price...
                        String[] cols = line.split(",");
                        if (cols.length > 3) {
                            String key = cols[0];      // e.g. NSE_EQ|INE002A01018
                            String symbol = cols[2];   // e.g. RELIANCE
                            String name = cols[3].replace("\"", ""); // e.g. RELIANCE INDUSTRIES LTD
                            
                            // Hum search ke liye "SYMBOL | NAME" format save kar rahe hain
                            MASTER_LIST.add(key + " | " + symbol + " | " + name);
                            count++;
                        }
                    }
                    System.out.println("✅ Master Sync Complete! " + count + " symbols loaded in memory.");
                }
            } catch (Exception e) {
                System.err.println("❌ Master Sync Failed: " + e.getMessage());
                // Fallback: Agar sync fail ho toh basic indices add kar do
                MASTER_LIST.add("NSE_INDEX|Nifty 50 | NIFTY 50");
                MASTER_LIST.add("NSE_INDEX|Nifty Bank | NIFTY BANK");
            }
        });
        syncThread.setPriority(Thread.MIN_PRIORITY); // Background mein chale taaki server slow na ho
        syncThread.start();
    }

    // QuotesController ise call karega /search?q=... par
    public static List<String> find(String query) {
        if (query == null || query.length() < 2) return new ArrayList<>();
        
        String lowerQuery = query.toLowerCase();
        return MASTER_LIST.stream()
                .filter(s -> s.toLowerCase().contains(lowerQuery))
                .limit(15) // Top 15 results mobile UI ke liye kaafi hain
                .collect(Collectors.toList());
    }
}
