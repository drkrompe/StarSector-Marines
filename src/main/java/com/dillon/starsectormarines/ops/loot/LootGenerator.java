package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.ops.MissionOutcome;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/** Campaign-facing adapter that freezes a manifest from a mission outcome. */
public final class LootGenerator {

    private static final Logger LOG = Global.getLogger(LootGenerator.class);

    private LootGenerator() {}

    public static LootManifest generate(MissionOutcome outcome) {
        if (outcome == null || outcome.missionSource.isDebug() || !outcome.victory) {
            return LootManifest.EMPTY;
        }
        LootManifest manifest = LootManifest.EMPTY;
        try {
            LootRollRequest request = LootRollRequest.from(outcome);
            if (outcome.salvageEntitlement > 0)
                manifest = LootRoller.roll(request, StarsectorLootCatalog.candidates(request));
        } catch (RuntimeException ex) {
            LOG.error("Unable to generate recovery manifest for mission " + outcome.missionId, ex);
        }
        return FieldSalvageLoot.append(manifest, outcome.fieldSalvage, resource -> {
            var settings = Global.getSettings();
            var spec = settings == null ? null : settings.getCommoditySpec(resource.commodityId);
            if (spec == null) return null;
            return new LootCandidate(LootKind.COMMODITY, resource.commodityId, spec.getName(),
                    spec.getIconName(), Math.max(1, Math.round(spec.getBasePrice())),
                    spec.getCargoSpace(), 1, 1, 1);
        });
    }
}
