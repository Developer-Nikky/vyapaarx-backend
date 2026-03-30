package service;

import cache.MemoryCache;
import connector.UpstoxConnector;
import java.util.regex.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class QuotesService {
    // Ye set store karega ki filhal users kaunse stocks dekh rahe hain
    private static final Set<String> ACTIVE_SYMBOLS = ConcurrentHashMap.newKeySet();

    static {
        // Default indices jo hamesha update honge
        ACTIVE_SYMBOLS.add("NSE_INDEX|Nifty 50");
        ACTIVE_SYMBOLS.add("NSE_INDEX|Nifty Bank");
    }

    public static void addSymbols(String keys) {
        if (keys == null) return;
        for (String k : keys.split(",")) ACTIVE_SYMBOLS.add(k.trim());
    }

    public static String getQuotesFromCache() {
        return MemoryCache.get("live_data");
    }

    public static void refreshQuotes() {
        try {
            if (ACTIVE_SYMBOLS.isEmpty()) return;
            String allKeys = String.join(",", ACTIVE_SYMBOLS);
            String raw = UpstoxConnector.fetchLtpQuotes(allKeys);
            
            if (raw != null && !raw.contains("\"error\"")) {
                MemoryCache.put("live_data", cleanData(raw));
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static String cleanData(String raw) {
        // Regex jo har tarah ke stock aur index ka LTP aur Token nikal lega
        Pattern p = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{[^\\}]*?\"last_price\"\\s*:\\s*([0-9.]+)[^\\}]*?\"instrument_token\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(raw);
        java.util.List<String> list = new java.util.ArrayList<>();
        
        while (m.find()) {
            String fullKey = m.group(1); // e.g. NSE_EQ|INE002A01018
            String ltp = m.group(2);
            
            // Symbol Name nikalne ka logic: NSE_INDEX|Nifty 50 -> NIFTY 50
            String displayName = fullKey.contains("|") ? fullKey.split("\\|")[1] : fullKey;
            
            list.add(String.format("{\"symbol\":\"%s\",\"ltp\":%s}", displayName.toUpperCase(), ltp));
        }
        
        return String.format("{\"timestamp\":%d,\"data\":[%s]}", System.currentTimeMillis(), String.join(",", list));
    }
}
