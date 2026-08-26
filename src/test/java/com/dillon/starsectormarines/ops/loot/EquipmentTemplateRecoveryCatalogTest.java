package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.marine.FactionEquipmentCatalog;
import com.dillon.starsectormarines.marine.FactionEquipmentSource;
import com.dillon.starsectormarines.marine.EquipmentAcquisitionEligibility;
import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentTemplateRecoveryCatalogTest {

    @Test
    void highRiskOperationsExposeOneFactionRecoveryCard() {
        LootRollRequest request = request("hegemony", RiskLevel.HIGH);

        List<LootCandidate> candidates = candidates(request, Set.of());

        assertEquals(1, candidates.size());
        LootCandidate candidate = candidates.get(0);
        assertEquals(LootKind.SPECIAL, candidate.kind);
        assertEquals(1, candidate.minQuantity);
        assertEquals(1, candidate.maxQuantity);
        assertTrue(FactionEquipmentCatalog.resolve("hegemony").offers(
                candidate.itemId, FactionEquipmentSource.RECOVERY));
    }

    @Test
    void lowRiskOperationsAndExplicitFactionExclusionsHaveNoCard() {
        assertTrue(candidates(request("hegemony", RiskLevel.LOW), Set.of()).isEmpty());
        assertTrue(candidates(request("remnant", RiskLevel.HIGH), Set.of()).isEmpty());
        assertTrue(candidates(new LootRollRequest("no-rights", MissionType.RAID,
                RiskLevel.HIGH, "hegemony", "militarybase", 50_000, 0),
                Set.of()).isEmpty());
    }

    @Test
    void learnedOrCarriedCardsAreExcludedBeforeTheManifestFreezes() {
        LootRollRequest request = request("tritachyon", RiskLevel.HIGH);
        Set<String> entirePool = Set.copyOf(FactionEquipmentCatalog.resolve("tritachyon")
                .offers(FactionEquipmentSource.RECOVERY).stream()
                .map(offer -> offer.templateId()).toList());

        assertTrue(candidates(request, entirePool).isEmpty());
    }

    @Test
    void unknownSubmodFactionsUseIndependentRecoveryData() {
        LootCandidate candidate = candidates(
                request("example_oc_without_a_pool", RiskLevel.HIGH), Set.of()).get(0);

        assertTrue(FactionEquipmentCatalog.resolve("independent").offers(
                candidate.itemId, FactionEquipmentSource.RECOVERY));
    }

    @Test
    void immutableMissionFactsSelectTheSameTemplate() {
        LootRollRequest request = request("luddic_path", RiskLevel.HIGH);

        assertEquals(candidates(request, Set.of()).get(0).itemId,
                candidates(request, Set.of()).get(0).itemId);
    }

    @Test
    void recoveryOpensAdvancedAndPrestigeBandsWithCompanyVictories() {
        LootRollRequest hegemony = request("hegemony", RiskLevel.HIGH);
        LootRollRequest tritachyon = request("tritachyon", RiskLevel.HIGH);

        assertTrue(candidates(hegemony, Set.of(), 4).isEmpty());
        assertEquals(1, candidates(hegemony, Set.of(), 5).size());
        assertTrue(candidates(tritachyon, Set.of(), 14).isEmpty());
        assertEquals(1, candidates(tritachyon, Set.of(), 15).size());
    }

    private static List<LootCandidate> candidates(LootRollRequest request,
                                                   Set<String> unavailable) {
        return candidates(request, unavailable, 15);
    }

    private static List<LootCandidate> candidates(LootRollRequest request,
                                                   Set<String> unavailable,
                                                   int victories) {
        return EquipmentTemplateRecoveryCatalog.candidates(
                request, unavailable, 5_000, 1f, "template.png",
                new EquipmentAcquisitionEligibility.Progress(victories, 20));
    }

    private static LootRollRequest request(String factionId, RiskLevel risk) {
        return new LootRollRequest("recovery-test", MissionType.RAID, risk,
                factionId, "militarybase", 50_000, 50);
    }
}
