package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.marine.EquipmentAccessTier;
import com.dillon.starsectormarines.marine.EquipmentAcquisitionEligibility;
import com.dillon.starsectormarines.marine.MechPattern;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MechPatternRecoveryCatalogTest {

    @Test
    void highRiskOperationsExposeAtMostOnePattern() {
        List<LootCandidate> candidates = candidates(request(RiskLevel.HIGH), Set.of());

        assertEquals(1, candidates.size());
        LootCandidate candidate = candidates.get(0);
        assertEquals(LootKind.SPECIAL, candidate.kind);
        assertEquals(1, candidate.maxQuantity);
        assertTrue(MechPattern.parse(candidate.itemId) != null);
        assertEquals(candidate.itemId, candidates(request(RiskLevel.HIGH), Set.of()).get(0).itemId);
    }

    @Test
    void lowRiskAndExhaustedPoolsYieldNothing() {
        assertTrue(candidates(request(RiskLevel.LOW), Set.of()).isEmpty());
        Set<String> everything = new HashSet<>();
        for (MechPattern pattern : MechPattern.offerable()) everything.add(pattern.id());
        assertTrue(candidates(request(RiskLevel.HIGH), everything).isEmpty());
    }

    private static List<LootCandidate> candidates(LootRollRequest request, Set<String> unavailable) {
        return MechPatternRecoveryCatalog.candidates(request, unavailable, 5_000, 1f, "card.png",
                EquipmentAcquisitionEligibility.Progress.complete());
    }

    private static LootRollRequest request(RiskLevel risk) {
        return new LootRollRequest("pattern-test", MissionType.RAID, risk,
                "hegemony", "militarybase", 50_000, 50);
    }
}
