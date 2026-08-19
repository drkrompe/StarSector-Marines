package com.dillon.starsectormarines.campaign;

import java.util.Locale;
import java.util.Random;

/** Renders one recent confirmed Chronicle fact linked to the current patron. */
public final class PatronChronicleComposer {

    public static final int MAX_AGE_DAYS = 365;

    private static final long SEED_MIXER = 0x3C6EF372FE94F82BL;

    private PatronChronicleComposer() {}

    public static String compose(CampaignState state, long patronHouseId,
                                 long currentContractId, int currentOfferDay,
                                 String patronDisplayName) {
        if (state == null || currentContractId <= 0L
                || PatronEngagementMemory.history(
                    state, patronHouseId) != null) {
            return null;
        }
        PatronChronicleMemory.Snapshot memory =
                PatronChronicleMemory.latestForPatron(state, patronHouseId,
                        currentOfferDay, MAX_AGE_DAYS);
        if (memory == null) return null;
        int otherHouseRow = state.houseIndex(memory.otherHouseId);
        String otherHouse = otherHouseRow >= 0
                ? state.houseDisplayName[otherHouseRow] : null;
        if (otherHouse == null || otherHouse.trim().isEmpty()) return null;
        String patron = patronDisplayName;
        if (patron == null || patron.trim().isEmpty()) {
            int patronRow = state.houseIndex(patronHouseId);
            patron = patronRow >= 0
                    ? state.houseDisplayName[patronRow] : null;
        }
        if (patron == null || patron.trim().isEmpty()) return null;
        String[] pool = PatronChronicleVoice.forType(memory.referenceType);
        if (pool.length == 0) return null;
        long seed = currentContractId * 0x9E3779B97F4A7C15L
                + memory.id + SEED_MIXER;
        int index = Math.floorMod(new Random(seed).nextInt(), pool.length);
        int daysAgo = currentOfferDay - memory.happenedTick;
        return pool[index]
                .replace("{patron}", patron)
                .replace("{otherHouse}", otherHouse)
                .replace("{chronicleOutcome}",
                        outcomeLabel(memory.chainOutcome))
                .replace("{daysAgo}", String.valueOf(daysAgo))
                .replace("{age}", ageLabel(daysAgo));
    }

    private static String outcomeLabel(ChainState outcome) {
        return outcome != null
                ? outcome.name().toLowerCase(Locale.ROOT)
                : "";
    }

    private static String ageLabel(int daysAgo) {
        if (daysAgo <= 0) return "today";
        if (daysAgo == 1) return "1 day ago";
        return daysAgo + " days ago";
    }
}
