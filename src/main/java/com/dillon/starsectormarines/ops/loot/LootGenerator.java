package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.ops.MissionOutcome;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/** Campaign-facing adapter that freezes a manifest from a mission outcome. */
public final class LootGenerator {

    private static final Logger LOG = Global.getLogger(LootGenerator.class);

    private LootGenerator() {}

    /**
     * The negotiated roll and the field finds on a victory; on any non-debug
     * outcome, the materials its succeeded optional objectives earned, which are
     * the objective's own and do not depend on who held the field.
     */
    public static LootManifest generate(MissionOutcome outcome) {
        if (outcome == null || outcome.missionSource.isDebug()) {
            return LootManifest.EMPTY;
        }
        LootManifest manifest = LootManifest.EMPTY;
        if (outcome.victory) {
            try {
                LootRollRequest request = LootRollRequest.from(outcome);
                if (outcome.salvageEntitlement > 0)
                    manifest = LootRoller.roll(request, StarsectorLootCatalog.candidates(request));
            } catch (RuntimeException ex) {
                LOG.error("Unable to generate recovery manifest for mission " + outcome.missionId, ex);
            }
            manifest = FieldSalvageLoot.append(manifest, outcome.fieldSalvage,
                    resource -> commodity(resource.commodityId));
        }
        return FieldSalvageLoot.appendObjectiveMaterials(manifest, outcome.objectives, LootGenerator::commodity);
    }

    private static LootCandidate commodity(String commodityId) {
        var settings = Global.getSettings();
        var spec = settings == null ? null : settings.getCommoditySpec(commodityId);
        if (spec == null) return null;
        return new LootCandidate(LootKind.COMMODITY, commodityId, spec.getName(),
                spec.getIconName(), Math.max(1, Math.round(spec.getBasePrice())),
                spec.getCargoSpace(), 1, 1, 1);
    }
}
