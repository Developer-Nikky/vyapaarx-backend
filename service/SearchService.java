package service;

import model.Instrument;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;
import java.util.stream.Collectors;

public class SearchService {

    private static final String MASTER_URL =
            "https://assets.upstox.com/market-quote/instruments/exchange/complete.csv.gz";

    private static final CopyOnWriteArrayList<Instrument> MASTER_LIST = new CopyOnWriteArrayList<>();
    private static volatile boolean syncInProgress = false;
    private static volatile long lastSyncAt = 0L;
    private static volatile String lastSyncStatus = "NOT_STARTED";

    public static void initMasterData() {
        if (syncInProgress || !MASTER_LIST.isEmpty()) return;

        Thread syncThread = new Thread(() -> {
            syncInProgress = true;
            lastSyncStatus = "SYNCING";
            try {
                List<Instrument> loaded = loadMasterInstruments();
                MASTER_LIST.clear();
                MASTER_LIST.addAll(loaded);
                lastSyncAt = System.currentTimeMillis();
                lastSyncStatus = "SYNC_OK";
                System.out.println("✅ Master Sync Complete! " + loaded.size() + " instruments loaded.");
            } catch (Exception e) {
                lastSyncStatus = "SYNC_FAILED";
                System.err.println("❌ Master Sync Failed: " + e.getMessage());

                if (MASTER_LIST.isEmpty()) {
                    MASTER_LIST.add(new Instrument("NIFTY", "NIFTY 50", "NSE_INDEX|Nifty 50", "NSE", "INDEX", true, "INDEX"));
                    MASTER_LIST.add(new Instrument("BANKNIFTY", "NIFTY BANK", "NSE_INDEX|Nifty Bank", "NSE", "INDEX", true, "INDEX"));
                    MASTER_LIST.add(new Instrument("SENSEX", "SENSEX", "NSE_INDEX|SENSEX", "BSE", "INDEX", true, "INDEX"));
                }
            } finally {
                syncInProgress = false;
            }
        });

        syncThread.setName("master-sync-thread");
        syncThread.setDaemon(true);
        syncThread.setPriority(Thread.MIN_PRIORITY);
        syncThread.start();
    }

    public static void refreshMasterDataIfEmpty() {
        if (MASTER_LIST.isEmpty() && !syncInProgress) {
            initMasterData();
        }
    }

    public static List<Instrument> findInstruments(String query, int limit) {
        if (query == null || query.trim().length() < 2) {
            return getDefaultIndices(limit);
        }

        String lowerQuery = query.trim().toLowerCase(Locale.ROOT);

        return MASTER_LIST.stream()
                .filter(i ->
                        i.getSymbol().toLowerCase(Locale.ROOT).contains(lowerQuery)
                                || i.getDisplayName().toLowerCase(Locale.ROOT).contains(lowerQuery)
                                || i.getInstrumentKey().toLowerCase(Locale.ROOT).contains(lowerQuery))
                .limit(Math.max(1, Math.min(limit, 50)))
                .collect(Collectors.toList());
    }

    public static Instrument findExactInstrument(String query) {
        if (query == null || query.isBlank()) return null;

        String normalized = normalize(query);

        for (Instrument instrument : MASTER_LIST) {
            if (normalize(instrument.getSymbol()).equals(normalized)) {
                return instrument;
            }
        }

        for (Instrument instrument : MASTER_LIST) {
            if (normalize(instrument.getDisplayName()).contains(normalized)) {
                return instrument;
            }
        }

        return null;
    }

    public static List<Instrument> getDefaultIndices(int limit) {
        List<Instrument> out = new ArrayList<>();
        for (Instrument instrument : MASTER_LIST) {
            if ("INDEX".equalsIgnoreCase(instrument.getInstrumentType())) {
                out.add(instrument);
            }
        }

        if (out.isEmpty()) {
            out.add(new Instrument("NIFTY", "NIFTY 50", "NSE_INDEX|Nifty 50", "NSE", "INDEX", true, "INDEX"));
            out.add(new Instrument("BANKNIFTY", "NIFTY BANK", "NSE_INDEX|Nifty Bank", "NSE", "INDEX", true, "INDEX"));
            out.add(new Instrument("SENSEX", "SENSEX", "NSE_INDEX|SENSEX", "BSE", "INDEX", true, "INDEX"));
        }

        return out.subList(0, Math.min(limit, out.size()));
    }

    public static int getMasterCount() {
        return MASTER_LIST.size();
    }

    public static long getLastSyncAt() {
        return lastSyncAt;
    }

    public static String getLastSyncStatus() {
        return lastSyncStatus;
    }

    public static List<Instrument> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(MASTER_LIST));
    }

    private static List<Instrument> loadMasterInstruments() throws Exception {
        List<Instrument> loaded = new ArrayList<>();

        URL url = new URL(MASTER_URL);
        try (InputStream is = url.openStream();
             GZIPInputStream gzis = new GZIPInputStream(is);
             BufferedReader br = new BufferedReader(new InputStreamReader(gzis, StandardCharsets.UTF_8))) {

            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] cols = safeCsvSplit(line);
                if (cols.length < 7) continue;

                String instrumentKey = clean(cols[0]);
                String tradingSymbol = clean(cols[2]);
                String name = clean(cols[3]);
                String exchange = cols.length > 11 ? clean(cols[11]) : "";
                String segment = cols.length > 8 ? clean(cols[8]) : "";
                String instrumentType = cols.length > 9 ? clean(cols[9]) : "";
                boolean tradable = true;

                if (instrumentKey.isBlank() || tradingSymbol.isBlank()) continue;

                loaded.add(new Instrument(
                        normalize(tradingSymbol),
                        name.isBlank() ? tradingSymbol : name,
                        instrumentKey,
                        exchange,
                        segment,
                        tradable,
                        instrumentType.isBlank() ? "UNKNOWN" : instrumentType
                ));
            }
        }

        return loaded;
    }

    private static String[] safeCsvSplit(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);

            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                cols.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cols.add(current.toString());

        return cols.toArray(new String[0]);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace("\"", "").trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT).replace(" ", "_");
    }
}
