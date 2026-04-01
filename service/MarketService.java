package service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

public class MarketService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final LocalTime PRE_OPEN_START = LocalTime.of(9, 0);
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 15);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(15, 30);

    public static String getMarketStatus() {
        LocalDateTime now = LocalDateTime.now(ZONE);
        DayOfWeek day = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();

        boolean weekend = (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY);

        String session;
        boolean isOpen;

        if (weekend) {
            session = "CLOSED";
            isOpen = false;
        } else if (!time.isBefore(PRE_OPEN_START) && time.isBefore(MARKET_OPEN)) {
            session = "PRE_OPEN";
            isOpen = false;
        } else if (!time.isBefore(MARKET_OPEN) && !time.isAfter(MARKET_CLOSE)) {
            session = "OPEN";
            isOpen = true;
        } else {
            session = "CLOSED";
            isOpen = false;
        }

        return "{"
                + "\"success\":true,"
                + "\"timestamp\":" + System.currentTimeMillis() + ","
                + "\"market\":\"NSE\","
                + "\"isOpen\":" + isOpen + ","
                + "\"session\":\"" + session + "\","
                + "\"serverTime\":\"" + escape(now.toString()) + "\","
                + "\"error\":null"
                + "}";
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
