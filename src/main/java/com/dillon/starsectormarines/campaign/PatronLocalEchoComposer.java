package com.dillon.starsectormarines.campaign;

import java.util.Locale;
import java.util.Random;

/** Renders one recent same-market engagement from a different patron. */
public final class PatronLocalEchoComposer {

    public static final int MAX_AGE_DAYS = 180;

    private static final long SEED_MIXER = 0xBB67AE8584CAA73BL;

    private PatronLocalEchoComposer() {}

    public static String compose(CampaignState state, long currentPatronId,
                                 int marketId, long currentContractId,
                                 int currentOfferDay) {
        if (state == null || currentContractId <= 0L
                || state.houseIndex(currentPatronId) < 0
                || PatronEngagementMemory.history(
                        state, currentPatronId) != null) {
            return null;
        }
        PatronEngagementMemory.Snapshot echo =
                PatronEngagementMemory.latestOtherAtMarket(state, marketId,
                        currentPatronId, currentOfferDay, MAX_AGE_DAYS);
        if (echo == null) return null;
        int houseRow = state.houseIndex(echo.houseId);
        String otherPatron = houseRow >= 0
                ? state.houseDisplayName[houseRow] : null;
        if (otherPatron == null || otherPatron.trim().isEmpty()) return null;
        String[] pool = PatronMemoryVoice.forLocalEcho(echo.outcome);
        if (pool.length == 0) return null;
        long seed = currentContractId * 0x9E3779B97F4A7C15L
                + echo.id + SEED_MIXER;
        int index = Math.floorMod(new Random(seed).nextInt(), pool.length);
        return pool[index]
                .replace("{otherPatron}", otherPatron)
                .replace("{otherContract}",
                        contractLabel(echo.contractType))
                .replace("{otherOutcome}", outcomeLabel(echo.outcome))
                .replace("{daysAgo}", String.valueOf(
                        currentOfferDay - echo.happenedTick))
                .replace("{age}", ageLabel(
                        currentOfferDay - echo.happenedTick));
    }

    private static String contractLabel(ContractType type) {
        return type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String outcomeLabel(PatronEngagementOutcome outcome) {
        switch (outcome) {
            case COMPLETED: return "completion";
            case FAILED: return "failure";
            case WITHDREW: return "withdrawal";
            case EMPLOYER_BREACHED: return "employer breach";
            default: return "unknown result";
        }
    }

    private static String ageLabel(int daysAgo) {
        if (daysAgo <= 0) return "today";
        if (daysAgo == 1) return "1 day ago";
        return daysAgo + " days ago";
    }
}
