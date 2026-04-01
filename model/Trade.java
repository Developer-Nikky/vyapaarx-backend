package model;

public class Trade {

    private String tradeId;
    private String userId;
    private String symbol;
    private String action;
    private String side;
    private int quantity;
    private double price;
    private long timestamp;

    public Trade(String tradeId, String userId, String symbol, String action, String side, int quantity, double price, long timestamp) {
        this.tradeId = tradeId;
        this.userId = userId;
        this.symbol = symbol;
        this.action = action;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.timestamp = timestamp;
    }

    public String toJson() {
        return "{"
                + "\"tradeId\":\"" + escape(tradeId) + "\","
                + "\"userId\":\"" + escape(userId) + "\","
                + "\"symbol\":\"" + escape(symbol) + "\","
                + "\"action\":\"" + escape(action) + "\","
                + "\"side\":\"" + escape(side) + "\","
                + "\"quantity\":" + quantity + ","
                + "\"price\":" + price + ","
                + "\"timestamp\":" + timestamp
                + "}";
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
