package com.dillon.starsectormarines.campaign.bloom;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * A vanilla-shaped sector for bloom evidence. The shape is fixed (one generator seed) so every
 * evidence seed plays over the same map; the save seed, which decides reservoirs and draws, is the
 * evidence seed. Forty core markets with vanilla's spread of sizes, factions and industries within
 * twelve light-years of the centre, twelve outposts beyond, and three player colonies founded in
 * cycles one, two and four that grow a size every eighteen months to six. Core worlds carry the
 * trade; the player's young colonies carry the inflow.
 */
final class SyntheticSector extends TestBloomWorld {

    private static final String[] CORE_FACTIONS = {
            "hegemony", "hegemony", "hegemony", "hegemony", "hegemony", "hegemony", "hegemony",
            "tritachyon", "tritachyon", "tritachyon", "tritachyon",
            "sindrian_diktat", "sindrian_diktat", "sindrian_diktat",
            "luddic_church", "luddic_church", "luddic_church", "luddic_church",
            "persean", "persean", "persean", "persean", "persean", "persean",
            "independent", "independent", "independent", "independent", "independent", "independent",
            "independent", "independent",
            "pirates", "pirates", "pirates", "pirates",
            "luddic_path", "luddic_path",
            "lions_guard", "independent"};
    private static final int[] CORE_SIZES = {3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
            6, 6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 4, 5, 6, 3};
    private static final int OUTPOSTS = 12;
    private static final int[] PLAYER_FOUNDED = {365, 2 * 365, 4 * 365};

    /** The size each market was founded at, for regrowth after a bloom. */
    private final Map<String, Integer> fullSize = new HashMap<>();
    private final Map<String, Integer> lastChange = new HashMap<>();

    SyntheticSector(long saveSeed) {
        super(saveSeed);
        SplittableRandom shape = new SplittableRandom(0x5EC70AL);
        for (int i = 0; i < CORE_FACTIONS.length; i++) {
            double angle = shape.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(shape.nextDouble()) * 12.0;
            add(market("core_" + i, "core_sys_" + (i / 2), CORE_FACTIONS[i], CORE_SIZES[i], 20 * 365, shape,
                    true, angle, radius));
        }
        for (int i = 0; i < OUTPOSTS; i++) {
            double angle = shape.nextDouble() * Math.PI * 2;
            double radius = 15.0 + shape.nextDouble() * 20.0;
            add(market("outpost_" + i, "outpost_sys_" + i, i % 3 == 0 ? "pirates" : "independent",
                    3 + shape.nextInt(2), 10 * 365, shape, false, angle, radius));
        }
        for (BloomMarket market : markets()) fullSize.put(market.id, market.size);
    }

    static int marketCount() {
        return CORE_FACTIONS.length + OUTPOSTS + PLAYER_FOUNDED.length;
    }

    static int coreCount() {
        return CORE_FACTIONS.length;
    }

    /** One day of the sector's own life: markets age, player colonies are founded and grow, shrunk markets regrow. */
    void advance(int day) {
        for (int i = 0; i < PLAYER_FOUNDED.length; i++) {
            if (day == PLAYER_FOUNDED[i]) {
                double angle = 0.7 + i * 2.1;
                add(new BloomMarket("player_" + i, "Player colony " + (i + 1), "player_sys_" + i, "player", 3, 0,
                        false, true, i == 1, i != 0, true, false,
                        (float) (Math.cos(angle) * 30.0), (float) (Math.sin(angle) * 30.0), 30f,
                        0.6f, 0.8f));
                fullSize.put("player_" + i, 6);
                lastChange.put("player_" + i, day);
            }
        }
        age(1);
        for (BloomMarket market : markets()) {
            Integer full = fullSize.get(market.id);
            if (full == null || market.size >= full || activeBloomStandsOn(market.id)) continue;
            int interval = market.playerOwned ? 540 : 730;
            int since = day - lastChange.getOrDefault(market.id, 0);
            if (since >= interval) {
                grow(market.id);
                lastChange.put(market.id, day);
            }
        }
    }

    @Override
    public boolean reduceSize(String marketId) {
        boolean reduced = super.reduceSize(marketId);
        if (reduced) lastChange.put(marketId, Integer.MAX_VALUE / 2);
        return reduced;
    }

    @Override
    public void ended(RavBloom bloom, int unrest) {
        super.ended(bloom, unrest);
        if (lastChange.containsKey(bloom.marketId)) lastChange.put(bloom.marketId, bloom.endDay());
    }

    private boolean activeBloomStandsOn(String marketId) {
        return lastChange.getOrDefault(marketId, 0) >= Integer.MAX_VALUE / 2;
    }

    private static BloomMarket market(String id, String systemId, String faction, int size, int age,
                                      SplittableRandom shape, boolean core, double angle, double radius) {
        boolean workings = shape.nextDouble() < 0.4;
        boolean habitable = shape.nextDouble() < 0.3;
        boolean ruins = shape.nextDouble() < 0.15;
        float x = (float) (Math.cos(angle) * radius);
        float y = (float) (Math.sin(angle) * radius);
        // Traffic follows size and the core's trade lanes; inflow is modest in settled worlds.
        float traffic = (core ? 0.6f : 0.3f) + size * 0.12f + (float) shape.nextDouble() * 0.3f;
        float inflow = (float) shape.nextDouble() * (core ? 0.4f : 0.3f);
        return new BloomMarket(id, id, systemId, faction, size, age, false, false, ruins, habitable, workings,
                core, x, y, (float) radius, traffic, inflow);
    }
}
