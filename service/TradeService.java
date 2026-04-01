package service;

import model.Portfolio;
import model.Trade;
import model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TradeService {

    private static final ConcurrentHashMap<String, List<Trade>> TRADE_HISTORY = new ConcurrentHashMap<>();

    public static String executeTrade(String userId, String body) {
        try {
            User user = UserService.getUserObject(userId);
            Portfolio portfolio = UserService.getPortfolioObject(userId);

            String action = extractString(body, "action");
            String symbol = extractString(body, "symbol");
            String side = extractString(body, "side");
            int quantity = extractInt(body, "quantity", 0);
            double price = extractDouble(body, "price", 0.0);

            if (action == null || action.isBlank()) action = "BUY";
            if (symbol == null || symbol.isBlank()) return error("Missing symbol");
            if (side == null || side.isBlank()) side = "LONG";
            if (quantity <= 0) return error("Invalid quantity");
            if (price <= 0) return error("Invalid price");

            double tradeValue = quantity * price;

            if ("BUY".equalsIgnoreCase(action)) {
                if (portfolio.getBalance() < tradeValue) {
                    return error("Insufficient balance");
                }
                portfolio.setBalance(portfolio.getBalance() - tradeValue);
                portfolio.setUsedMargin(portfolio.getUsedMargin() + tradeValue);

            } else if ("SELL".equalsIgnoreCase(action)) {
                portfolio.setBalance(portfolio.getBalance() + tradeValue);
                portfolio.setUsedMargin(Math.max(0.0, portfolio.getUsedMargin() - tradeValue));

            } else {
                return error("Unsupported action");
            }

            Trade trade = new Trade(
                    String.valueOf(System.nanoTime()),
                    userId,
                    symbol.toUpperCase(),
                    action.toUpperCase(),
                    side.toUpperCase(),
                    quantity,
                    price,
                    System.currentTimeMillis()
            );

            TRADE_HISTORY.computeIfAbsent(userId, k -> new ArrayList<>()).add(trade);
            UserService.savePortfolio(portfolio);

            return "{"
                    + "\"success\":true,"
                    + "\"timestamp\":" + System.currentTimeMillis() + ","
                    + "\"data\":{"
                    + "\"userId\":\"" + escape(user.getUserId()) + "\","
                    + "\"trade\":" + trade.toJson() + ","
                    + "\"portfolio\":" + portfolio.toJson()
                    + "},"
                    + "\"error\":null"
                    + "}";

        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    public static String getPortfolio(String userId) {
        Portfolio portfolio = UserService.getPortfolioObject(userId);
        List<Trade> trades = TRADE_HISTORY.computeIfAbsent(userId, k -> new ArrayList<>());

        StringBuilder tradeJson = new StringBuilder();
        tradeJson.append("[");

        for (int i = 0; i < trades.size(); i++) {
            tradeJson.append(trades.get(i).toJson());
            if (i < trades.size() - 1) {
                tradeJson.append(",");
            }
        }

        tradeJson.append("]");

        return "{"
                + "\"success\":true,"
                + "\"timestamp\":" + System.currentTimeMillis() + ","
                + "\"data\":{"
                + "\"portfolio\":" + portfolio.toJson() + ","
                + "\"trades\":" + tradeJson
                + "},"
                + "\"error\":null"
                + "}";
    }

    private static String extractString(String json, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json == null ? "" : json);
        return m.find() ? m.group(1) : null;
    }

    private static int extractInt(String json, String key, int defaultValue) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher m = p.matcher(json == null ? "" : json);
        return m.find() ? Integer.parseInt(m.group(1)) : defaultValue;
    }

    private static double extractDouble(String json, String key, double defaultValue) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)");
        Matcher m = p.matcher(json == null ? "" : json);
        return m.find() ? Double.parseDouble(m.group(1)) : defaultValue;
    }

    private static String error(String message) {
        return "{"
                + "\"success\":false,"
                + "\"timestamp\":" + System.currentTimeMillis() + ","
                + "\"data\":{},"
                + "\"error\":\"" + escape(message) + "\""
                + "}";
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
