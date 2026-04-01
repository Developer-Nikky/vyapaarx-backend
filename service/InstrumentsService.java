package service;

import connector.UpstoxConnector;
import model.Instrument;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InstrumentsService {

    private static final ConcurrentHashMap<String, Instrument> BY_SYMBOL = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Instrument> BY_KEY = new ConcurrentHashMap<>();

    public static void bootstrapDefaults() {
        addInstrument(new Instrument("NIFTY", "NIFTY 50", "NSE_INDEX|Nifty 50", "NSE", "INDEX", true, "INDEX"));
        addInstrument(new Instrument("BANKNIFTY", "NIFTY BANK", "NSE_INDEX|Nifty Bank", "NSE", "INDEX", true, "INDEX"));
        addInstrument(new Instrument("SENSEX", "SENSEX", "NSE_INDEX|SENSEX", "BSE", "INDEX", true, "INDEX"));
    }

    public static void syncMasterIfConfigured() {
        // no-op now; master file dependency removed
    }

    public static Instrument getByInstrumentKey(String key) {
        return key == null ? null : BY_KEY.get(key);
    }

    public static List<String> resolveInstrumentKeys(List<String> symbols) {
        List<String> out = new ArrayList<>();

        for (String raw : symbols) {
            if (raw == null || raw.isBlank()) continue;

            String symbol = normalize(raw);

            Instrument cached = BY_SYMBOL.get(symbol);
            if (cached != null) {
                out.add(cached.getInstrumentKey());
                continue;
            }

            if (raw.contains("|")) {
                out.add(raw.trim());
                continue;
            }

            try {
                Instrument searched = searchExactInstrument(raw);
                if (searched != null) {
                    addInstrument(searched);
                    out.add(searched.getInstrumentKey());
                }
            } catch (Exception ignored) {
            }
        }

        return out;
    }

    public static String search(String query, int limit) {
        try {
            if (query == null || query.isBlank()) {
                return defaultIndices(limit);
            }

            String raw = UpstoxConnector.searchInstruments(query, limit);
            List<Instrument> parsed = parseInstrumentSearchResponse(raw, limit);

            for (Instrument instrument : parsed) {
                addInstrument(instrument);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{")
              .append("\"success\":true,")
              .append("\"timestamp\":").append(System.currentTimeMillis()).append(",")
              .append("\"count\":").append(parsed.size()).append(",")
              .append("\"data\":[");

            for (int i = 0; i < parsed.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(parsed.get(i).toJson());
            }

            sb.append("],\"error\":null}");
            return sb.toString();

        } catch (Exception e) {
            return "{"
                    + "\"success\":false,"
                    + "\"timestamp\":" + System.currentTimeMillis() + ","
                    + "\"count\":0,"
                    + "\"data\":[],"
                    + "\"error\":\"" + escape(e.getMessage()) + "\""
                    + "}";
        }
    }

    public static String getIndices() {
        List<Instrument> indices = new ArrayList<>();
        addIfPresent(indices, "NIFTY");
        addIfPresent(indices, "BANKNIFTY");
        addIfPresent(indices, "SENSEX");

        StringBuilder sb = new StringBuilder();
        sb.append("{")
          .append("\"success\":true,")
          .append("\"timestamp\":").append(System.currentTimeMillis()).append(",")
          .append("\"count\":").append(indices.size()).append(",")
          .append("\"data\":[");

        for (int i = 0; i < indices.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(indices.get(i).toJson());
        }

        sb.append("],\"error\":null}");
        return sb.toString();
    }

    public static String getSyncStatus() {
        return "{"
                + "\"success\":true,"
                + "\"timestamp\":" + System.currentTimeMillis() + ","
                + "\"data\":{"
                + "\"synced\":true,"
                + "\"lastSyncAt\":0,"
                + "\"lastSyncStatus\":\"SEARCH_API_MODE\","
                + "\"instrumentCount\":" + BY_SYMBOL.size()
                + "},"
                + "\"error\":null"
                + "}";
    }

    public static String getHealthStatsJson() {
        return "{"
                + "\"synced\":true,"
                + "\"lastSyncAt\":0,"
                + "\"lastSyncStatus\":\"SEARCH_API_MODE\","
                + "\"instrumentCount\":" + BY_SYMBOL.size()
                + "}";
    }

    private static Instrument searchExactInstrument(String query) throws Exception {
        String raw = UpstoxConnector.searchInstruments(query, 10);
        List<Instrument> parsed = parseInstrumentSearchResponse(raw, 10);

        String target = normalize(query);

        for (Instrument instrument : parsed) {
            if (normalize(instrument.getSymbol()).equals(target)) {
                return instrument;
            }
        }

        return parsed.isEmpty() ? null : parsed.get(0);
    }

    private static List<Instrument> parseInstrumentSearchResponse(String rawJson, int limit) {
        List<Instrument> results = new ArrayList<>();

        Pattern objPattern = Pattern.compile("\\{[^\\{\\}]*\"instrument_key\"\\s*:\\s*\"([^\"]+)\"[^\\{\\}]*\\}");
        Matcher objMatcher = objPattern.matcher(rawJson);

        while (objMatcher.find() && results.size() < limit) {
            String obj = objMatcher.group();

            String instrumentKey = extractString(obj, "instrument_key");
            String tradingSymbol = extractString(obj, "trading_symbol");
            String shortName = extractString(obj, "short_name");
            String exchange = extractString(obj, "exchange");
            String segment = extractString(obj, "segment");
            String instrumentType = extractString(obj, "instrument_type");
            boolean tradable = extractBoolean(obj, "tradable", true);

            String symbol = !tradingSymbol.isBlank() ? tradingSymbol : deriveSymbolFromKey(instrumentKey);
            String displayName = !shortName.isBlank() ? shortName : symbol;

            if (!instrumentKey.isBlank() && !symbol.isBlank()) {
                results.add(new Instrument(
                        normalize(symbol),
                        displayName,
                        instrumentKey,
                        exchange,
                        segment,
                        tradable,
                        instrumentType
                ));
            }
        }

        return results;
    }

    private static String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json == null ? "" : json);
        return m.find() ? m.group(1) : "";
    }

    private static boolean extractBoolean(String json, String key, boolean defaultValue) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(true|false)");
        Matcher m = p.matcher(json == null ? "" : json);
        return m.find() ? Boolean.parseBoolean(m.group(1)) : defaultValue;
    }

    private static String deriveSymbolFromKey(String instrumentKey) {
        if (instrumentKey == null || instrumentKey.isBlank()) return "";
        if (instrumentKey.contains("|")) {
            String[] parts = instrumentKey.split("\\|", 2);
            if (parts.length == 2) {
                return normalize(parts[1]);
            }
        }
        return normalize(instrumentKey);
    }

    private static void addIfPresent(List<Instrument> out, String symbol) {
        Instrument instrument = BY_SYMBOL.get(symbol);
        if (instrument != null) out.add(instrument);
    }

    private static void addInstrument(Instrument instrument) {
        BY_SYMBOL.put(normalize(instrument.getSymbol()), instrument);
        BY_KEY.put(instrument.getInstrumentKey(), instrument);
    }

    private static String defaultIndices(int limit) {
        List<Instrument> out = new ArrayList<>();
        addIfPresent(out, "NIFTY");
        addIfPresent(out, "BANKNIFTY");
        addIfPresent(out, "SENSEX");

        StringBuilder sb = new StringBuilder();
        sb.append("{")
          .append("\"success\":true,")
          .append("\"timestamp\":").append(System.currentTimeMillis()).append(",")
          .append("\"count\":").append(Math.min(limit, out.size())).append(",")
          .append("\"data\":[");

        for (int i = 0; i < out.size() && i < limit; i++) {
            if (i > 0) sb.append(",");
            sb.append(out.get(i).toJson());
        }

        sb.append("],\"error\":null}");
        return sb.toString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
