package connector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class UpstoxConnector {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;
    private static final String BASE_URL = "https://api.upstox.com";

    public static String fetchLtpQuotes(String keys) throws Exception {
        String accessToken = getAccessToken();

        if (keys == null || keys.isBlank()) {
            return "{\"success\":false,\"error\":\"No keys provided\",\"source\":\"UPSTOX\"}";
        }

        String apiUrl = BASE_URL + "/v2/market-quote/ltp?instrument_key="
                + URLEncoder.encode(keys, StandardCharsets.UTF_8);

        return doGet(apiUrl, accessToken);
    }

    public static String searchInstruments(String query, int records) throws Exception {
        String accessToken = getAccessToken();

        if (query == null || query.isBlank()) {
            return "{\"success\":false,\"error\":\"Query required\",\"source\":\"UPSTOX\"}";
        }

        int safeRecords = Math.max(1, Math.min(records, 50));

        String apiUrl = BASE_URL + "/v1/instruments/search"
                + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&records=" + safeRecords;

        return doGet(apiUrl, accessToken);
    }

    private static String doGet(String apiUrl, String accessToken) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");

        int statusCode = conn.getResponseCode();

        BufferedReader reader;
        if (statusCode >= 200 && statusCode < 300) {
            reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
            );
        } else {
            if (conn.getErrorStream() == null) {
                conn.disconnect();
                return "{\"success\":false,\"error\":\"Upstream request failed\",\"status\":" + statusCode + ",\"source\":\"UPSTOX\"}";
            }

            reader = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8)
            );
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();
        conn.disconnect();

        String responseText = response.toString();

        if (statusCode >= 200 && statusCode < 300) {
            return responseText;
        }

        return "{"
                + "\"success\":false,"
                + "\"error\":\"Upstream error\","
                + "\"status\":" + statusCode + ","
                + "\"details\":\"" + escapeJson(responseText) + "\","
                + "\"source\":\"UPSTOX\""
                + "}";
    }

    private static String getAccessToken() {
        String accessToken = System.getenv("UPSTOX_ACCESS_TOKEN");
        if (accessToken == null || accessToken.isBlank()) {
            throw new RuntimeException("Missing UPSTOX_ACCESS_TOKEN");
        }
        return accessToken;
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
