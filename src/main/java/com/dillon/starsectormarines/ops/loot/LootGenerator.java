package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.ops.MissionOutcome;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/** Campaign-facing adapter that freezes a manifest from a mission outcome. */
public final class LootGenerator {

    private static final Logger LOG = Global.getLogger(LootGenerator.class);

    private LootGenerator() {}

    public static LootManifest generate(MissionOutcome outcome) {
        if (outcome == null || !outcome.victory || outcome.salvageEntitlement <= 0) {
            return LootManifest.EMPTY;
        }
        try {
            LootRollRequest request = LootRollRequest.from(outcome);
            return LootRoller.roll(request, StarsectorLootCatalog.candidates(request));
        } catch (RuntimeException ex) {
            LOG.error("Unable to generate recovery manifest for mission " + outcome.missionId, ex);
            return LootManifest.EMPTY;
        }
    }
}
