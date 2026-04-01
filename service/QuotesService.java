package service;

import cache.MemoryCache;
import connector.UpstoxConnector;
import model.Instrument;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuotesService {

    private static final String DEFAULT_KEYS = "NSE_INDEX|Nifty 50,NSE_INDEX|Nifty Bank,NSE_INDEX|SENSEX";
    private static final long QUOTES_TTL_MS = 1200;
    private static final long MAX_STALE_MS = 15000;
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    public static void warmCriticalQuotes() {
        try {
            getQuotesByRequest(null, null);
        } catch (Exception ignored) {
        }
    }

    public static String getQuotesByRequest(String keys, String symbols) throws Exception {
        String finalKeys = resolveRequestKeys(keys, symbols);
        if (finalKeys == null || finalKeys.isBlank()) {
            return errorJson("No valid keys found");
        }

        String cacheKey = "quotes:" + finalKeys;

        String fresh = MemoryCache.getFresh(cacheKey);
        if (fresh != null) {
            return fresh;
        }

        Object lock = LOCKS.computeIfAbsent(cacheKey, k -> new Object());

        synchronized (lock) {
            fresh = MemoryCache.getFresh(cacheKey);
            if (fresh != null) {
                return fresh;
            }

            try {
                String rawResponse = UpstoxConnector.fetchLtpQuotes(finalKeys);

                if (rawResponse == null || rawResponse.isBlank()) {
                    return staleOrError(cacheKey, "Empty response from upstream");
                }

                if (rawResponse.contains("\"error\"") && !rawResponse.contains("\"data\"")) {
                    return staleOrError(cacheKey, "Upstream returned error");
                }

                String normalized = normalizeResponse(rawResponse, finalKeys, false);
                MemoryCache.put(cacheKey, normalized, QUOTES_TTL_MS);
                return normalized;

            } catch (Exception ex) {
                return staleOrError(cacheKey, ex.getMessage());
            } finally {
                LOCKS.remove(cacheKey);
            }
        }
    }

    private static String resolveRequestKeys(String keys, String symbols) {
        if (keys != null && !keys.isBlank()) {
            return sanitizeKeys(keys);
        }

        if (symbols != null && !symbols.isBlank()) {
            List<String> symbolList = splitCsv(symbols);
            List<String> resolved = InstrumentsService.resolveInstrumentKeys(symbolList);
            if (!resolved.isEmpty()) {
                return sanitizeKeys(String.join(",", resolved));
            }
        }

        return DEFAULT_KEYS;
    }

    private static String staleOrError(String cacheKey, String reason) {
        String stale = MemoryCache.getStale(cacheKey, MAX_STALE_MS);
        if (stale != null) {
            return markAsStale(stale, reason);
        }
        return errorJson(reason);
    }

    private static String markAsStale(String json, String reason) {
        if (json.endsWith("}")) {
            return json.substring(0, json.length() - 1)
                    + ",\"stale\":true,\"warning\":\"" + escapeJson(reason) + "\"}";
        }
        return errorJson(reason);
    }

    private static String sanitizeKeys(String keys) {
        List<String> raw = splitCsv(keys);
        Set<String> unique = new LinkedHashSet<>();
        for (String part : raw) {
            if (!part.isBlank()) {
                unique.add(part.trim());
            }
        }
        return String.join(",", unique);
    }

    private static List<String> splitCsv(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        for (String part : text.split(",")) {
            String value = part.trim();
            if (!value.isBlank()) {
                out.add(value);
            }
        }
        return out;
    }

    private static String normalizeResponse(String rawJson, String requestedKeys, boolean stale) {
        StringBuilder result = new StringBuilder();
        List<String> items = new ArrayList<>();

        Pattern pattern = Pattern.compile(
                "\"([^\"]+)\"\\s*:\\s*\\{[^\\}]*?\"last_price\"\\s*:\\s*([0-9.]+)[^\\}]*?\"instrument_token\"\\s*:\\s*\"([^\"]+)\""
        );

        Matcher matcher = pattern.matcher(rawJson);

        while (matcher.find()) {
            String rawSymbol = matcher.group(1);
            String ltp = matcher.group(2);
            String instrumentKey = matcher.group(3);

            Instrument known = InstrumentsService.getByInstrumentKey(instrumentKey);
            String cleanSymbol = known != null ? known.getSymbol() : mapSymbol(rawSymbol, instrumentKey);

            String item = "{"
                    + "\"symbol\":\"" + escapeJson(cleanSymbol) + "\","
                    + "\"ltp\":" + ltp + ","
                    + "\"instrumentKey\":\"" + escapeJson(instrumentKey) + "\""
                    + "}";

            items.add(item);
        }

        if (items.isEmpty()) {
            return errorJson("No quote data found");
        }

        result.append("{");
        result.append("\"success\":true,");
        result.append("\"timestamp\":").append(System.currentTimeMillis()).append(",");
        result.append("\"source\":\"UPSTOX\",");
        result.append("\"requestedKeys\":\"").append(escapeJson(requestedKeys)).append("\",");
        result.append("\"count\":").append(items.size()).append(",");
        result.append("\"stale\":").append(stale).append(",");
        result.append("\"error\":null,");
        result.append("\"data\":[");
        result.append(String.join(",", items));
        result.append("]");
        result.append("}");

        return result.toString();
    }

    private static String errorJson(String message) {
        return "{"
                + "\"success\":false,"
                + "\"timestamp\":" + System.currentTimeMillis() + ","
                + "\"source\":\"VYAPAARX\","
                + "\"stale\":false,"
                + "\"error\":\"" + escapeJson(message) + "\","
                + "\"data\":[]"
                + "}";
    }

    private static String mapSymbol(String rawSymbol, String instrumentKey) {
        String combined = (rawSymbol + " " + instrumentKey).toUpperCase();

        if (combined.contains("NIFTY BANK")) return "BANKNIFTY";
        if (combined.contains("NIFTY 50")) return "NIFTY";
        if (combined.contains("SENSEX")) return "SENSEX";

        if (instrumentKey.contains("|")) {
            String[] parts = instrumentKey.split("\\|", 2);
            if (parts.length == 2 && !parts[1].isBlank()) {
                return parts[1].trim().toUpperCase().replace(" ", "_");
            }
        }

        if (rawSymbol.contains(":")) {
            return rawSymbol.substring(rawSymbol.indexOf(":") + 1).trim().toUpperCase().replace(" ", "_");
        }

        return rawSymbol.trim().toUpperCase().replace(" ", "_");
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
