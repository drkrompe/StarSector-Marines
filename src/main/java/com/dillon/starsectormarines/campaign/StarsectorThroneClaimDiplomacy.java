package com.dillon.starsectormarines.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

/** Applies the v1 diplomatic rupture between a claimant and its former ruler. */
public final class StarsectorThroneClaimDiplomacy implements ThroneClaimDiplomacy {

    static final float HOSTILE_CEILING = -0.5f;

    interface SectorAccess {
        boolean available();
        boolean factionExists(String factionId);
        float relationship(String fromFactionId, String toFactionId);
        void setRelationship(String fromFactionId, String toFactionId, float value);
    }

    private final SectorAccess access;

    public StarsectorThroneClaimDiplomacy() {
        this(new GlobalSectorAccess());
    }

    StarsectorThroneClaimDiplomacy(SectorAccess access) {
        if (access == null) throw new IllegalArgumentException("access");
        this.access = access;
    }

    @Override
    public Result apply(String sourceFactionId, String resultFactionId) {
        if (!access.available()) return Result.RETRY;
        if (sourceFactionId == null || resultFactionId == null
                || sourceFactionId.equals(resultFactionId)
                || !access.factionExists(sourceFactionId)
                || !access.factionExists(resultFactionId)) {
            return Result.REJECTED;
        }

        boolean sourceHostile = atOrBelowCeiling(sourceFactionId, resultFactionId);
        boolean resultHostile = atOrBelowCeiling(resultFactionId, sourceFactionId);
        if (sourceHostile && resultHostile) return Result.ALREADY_APPLIED;

        if (!sourceHostile) {
            access.setRelationship(sourceFactionId, resultFactionId, HOSTILE_CEILING);
        }
        if (!resultHostile) {
            access.setRelationship(resultFactionId, sourceFactionId, HOSTILE_CEILING);
        }
        return atOrBelowCeiling(sourceFactionId, resultFactionId)
                && atOrBelowCeiling(resultFactionId, sourceFactionId)
                ? Result.APPLIED : Result.RETRY;
    }

    private boolean atOrBelowCeiling(String fromFactionId, String toFactionId) {
        return access.relationship(fromFactionId, toFactionId) <= HOSTILE_CEILING;
    }

    private static final class GlobalSectorAccess implements SectorAccess {

        @Override
        public boolean available() {
            return Global.getSector() != null;
        }

        @Override
        public boolean factionExists(String factionId) {
            return faction(factionId) != null;
        }

        @Override
        public float relationship(String fromFactionId, String toFactionId) {
            FactionAPI from = faction(fromFactionId);
            return from != null ? from.getRelationship(toFactionId) : 0f;
        }

        @Override
        public void setRelationship(String fromFactionId, String toFactionId, float value) {
            FactionAPI from = faction(fromFactionId);
            if (from != null) from.setRelationship(toFactionId, value);
        }

        private static FactionAPI faction(String factionId) {
            SectorAPI sector = Global.getSector();
            return sector != null ? sector.getFaction(factionId) : null;
        }
    }
}
