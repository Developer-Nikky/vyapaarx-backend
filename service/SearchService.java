package service;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.zip.GZIPInputStream;

public class SearchService {
    // Isme saare 50k+ symbols load honge
    private static final List<String> MASTER_LIST = new ArrayList<>();

    public static void initMasterData() {
        new Thread(() -> {
            try {
                System.out.println("Syncing Master Instruments from Upstox...");
                URL url = new URL("https://assets.upstox.com/market-quote/instruments/exchange/complete.csv.gz");
                try (GZIPInputStream gzis = new GZIPInputStream(url.openStream());
                     BufferedReader br = new BufferedReader(new InputStreamReader(gzis))) {
                    
                    String line;
                    br.readLine(); // Skip header
                    while ((line = br.readLine()) != null) {
                        String[] cols = line.split(",");
                        if (cols.length > 2) {
                            // Format: NSE_EQ|RELIANCE, RELIANCE INDUSTRIES
                            MASTER_LIST.add(cols[0] + " (" + cols[2] + ")");
                        }
                    }
                }
                System.out.println("Master Sync Done: " + MASTER_LIST.size() + " symbols loaded.");
            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }

    public static List<String> find(String query) {
        return MASTER_LIST.stream()
                .filter(s -> s.toLowerCase().contains(query.toLowerCase()))
                .limit(15) // Top 15 results taaki mobile screen par sahi dikhe
                .toList();
    }
}
