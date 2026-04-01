package service;

import java.util.concurrent.ConcurrentHashMap;

public class RewardService {

    private static final ConcurrentHashMap<String, Long> LAST_LOGIN_REWARD_DAY = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Integer> AD_REWARD_COUNT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> LAST_AD_REWARD_TIME = new ConcurrentHashMap<>();

    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long AD_COOLDOWN_MS = 30L * 60L * 1000L;
    private static final int MAX_AD_REWARD_PER_DAY = 3;

    public static String getRewardSnapshot(String userId) {
        long now = System.currentTimeMillis();
        resetAdCounterIfNeeded(userId, now);

        Long lastLoginRewardDay = LAST_LOGIN_REWARD_DAY.get(userId);
        Integer adCount = AD_REWARD_COUNT.getOrDefault(userId, 0);
        Long lastAd = LAST_AD_REWARD_TIME.get(userId);

        boolean loginRewardAvailable = isNewDay(lastLoginRewardDay, now);
        long nextAdInMs = 0;

        if (lastAd != null) {
            nextAdInMs = Math.max(0, AD_COOLDOWN_MS - (now - lastAd));
        }

        return "{"
                + "\"loginRewardAvailable\":" + loginRewardAvailable + ","
                + "\"adRewardsUsedToday\":" + adCount + ","
                + "\"adRewardsRemaining\":" + Math.max(0, MAX_AD_REWARD_PER_DAY - adCount) + ","
                + "\"nextAdRewardInMs\":" + nextAdInMs
                + "}";
    }

    public static boolean grantDailyLoginReward(String userId) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOGIN_REWARD_DAY.get(userId);

        if (isNewDay(last, now)) {
            LAST_LOGIN_REWARD_DAY.put(userId, now);
            return true;
        }
        return false;
    }

    public static boolean canGrantAdReward(String userId) {
        long now = System.currentTimeMillis();
        resetAdCounterIfNeeded(userId, now);

        int used = AD_REWARD_COUNT.getOrDefault(userId, 0);
        if (used >= MAX_AD_REWARD_PER_DAY) {
            return false;
        }

        Long last = LAST_AD_REWARD_TIME.get(userId);
        return last == null || (now - last) >= AD_COOLDOWN_MS;
    }

    public static boolean grantAdReward(String userId) {
        if (!canGrantAdReward(userId)) {
            return false;
        }

        AD_REWARD_COUNT.put(userId, AD_REWARD_COUNT.getOrDefault(userId, 0) + 1);
        LAST_AD_REWARD_TIME.put(userId, System.currentTimeMillis());
        return true;
    }

    private static boolean isNewDay(Long lastTime, long now) {
        if (lastTime == null) return true;
        return (now / ONE_DAY_MS) != (lastTime / ONE_DAY_MS);
    }

    private static void resetAdCounterIfNeeded(String userId, long now) {
        Long last = LAST_AD_REWARD_TIME.get(userId);
        if (last == null) return;

        if ((now / ONE_DAY_MS) != (last / ONE_DAY_MS)) {
            AD_REWARD_COUNT.put(userId, 0);
        }
    }
}
