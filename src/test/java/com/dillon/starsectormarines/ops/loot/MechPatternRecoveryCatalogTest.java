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
        List<LootCandidate> candidates = candidates(request(RiskLevel.HIGH), Set.of(), 15);

        assertEquals(1, candidates.size());
        LootCandidate candidate = candidates.get(0);
        assertEquals(LootKind.SPECIAL, candidate.kind);
        assertEquals(1, candidate.maxQuantity);
        assertTrue(MechPattern.parse(candidate.itemId) != null);
        assertEquals(candidate.itemId, candidates(request(RiskLevel.HIGH), Set.of(), 15).get(0).itemId);
    }

    @Test
    void lowRiskAndExhaustedPoolsYieldNothing() {
        assertTrue(candidates(request(RiskLevel.LOW), Set.of(), 15).isEmpty());
        Set<String> everything = new HashSet<>();
        for (MechPattern pattern : MechPattern.offerable()) everything.add(pattern.id());
        assertTrue(candidates(request(RiskLevel.HIGH), everything, 15).isEmpty());
    }

    @Test
    void recoveryGateFollowsVictories() {
        Set<String> common = new HashSet<>();
        for (MechPattern pattern : MechPattern.offerable()) {
            if (pattern.accessTier() == EquipmentAccessTier.COMMON) common.add(pattern.id());
        }
        // Everything Common is held: an opening company recovers nothing further.
        assertTrue(candidates(request(RiskLevel.HIGH), common, 4).isEmpty());
        assertEquals(1, candidates(request(RiskLevel.HIGH), common, 5).size());
    }

    private static List<LootCandidate> candidates(LootRollRequest request, Set<String> unavailable,
                                                  int victories) {
        return MechPatternRecoveryCatalog.candidates(request, unavailable, 5_000, 1f, "card.png",
                new EquipmentAcquisitionEligibility.Progress(victories, 0));
    }

    private static LootRollRequest request(RiskLevel risk) {
        return new LootRollRequest("pattern-test", MissionType.RAID, risk,
                "hegemony", "militarybase", 50_000, 50);
    }
}
