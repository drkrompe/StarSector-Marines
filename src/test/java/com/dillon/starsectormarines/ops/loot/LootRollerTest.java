package com.dillon.starsectormarines.ops.loot;

import com.dillon.starsectormarines.ops.MissionType;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LootRollerTest {

    @Test
    void sameFactsAndCatalogProduceSameOrderedManifest() {
        LootRollRequest request = request("mission-a", 60);
        List<LootCandidate> catalog = catalog(20);

        LootManifest first = LootRoller.roll(request, catalog);
        List<LootCandidate> reversed = new ArrayList<>(catalog);
        Collections.reverse(reversed);
        LootManifest second = LootRoller.roll(request, reversed);

        assertEquals(first.seed, second.seed);
        assertEquals(first.selectionBudget, second.selectionBudget);
        assertEquals(signature(first), signature(second));
    }

    @Test
    void immutableOutcomeFactsChangeTheSeed() {
        LootRollRequest first = request("mission-a", 60);
        LootRollRequest second = request("mission-b", 60);

        assertNotEquals(LootRoller.seedOf(first), LootRoller.seedOf(second));
    }

    @Test
    void drawsWithoutReplacementAndBudgetsByEntitlement() {
        LootManifest manifest = LootRoller.roll(request("mission-a", 35), catalog(20));

        Set<String> unique = new HashSet<>();
        for (LootStack stack : manifest.stacks) unique.add(stack.kind + ":" + stack.itemId);
        assertEquals(manifest.stacks.size(), unique.size());
        assertEquals((long) manifest.totalValue * 35L / 100L, manifest.selectionBudget);
        assertTrue(manifest.stacks.size() <= 12);
    }

    @Test
    void zeroEntitlementOrEmptyCatalogProducesEmptyManifest() {
        assertTrue(LootRoller.roll(request("mission-a", 0), catalog(5)).isEmpty());
        assertTrue(LootRoller.roll(request("mission-a", 50), Collections.emptyList()).isEmpty());
    }

    private static LootRollRequest request(String missionId, int entitlement) {
        return new LootRollRequest(missionId, MissionType.RAID, RiskLevel.MEDIUM,
                "tritachyon", "orbitalworks", 25_000, entitlement);
    }

    private static List<LootCandidate> catalog(int count) {
        List<LootCandidate> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LootKind kind = i % 4 == 0 ? LootKind.COMMODITY : LootKind.WEAPON;
            int min = kind == LootKind.COMMODITY ? 5 : 1;
            int max = kind == LootKind.COMMODITY ? 20 : 1;
            out.add(new LootCandidate(kind, "item-" + i, "Item " + i, null,
                    500 + i * 250, 1f + i, 1f + (i % 5), min, max));
        }
        return out;
    }

    private static List<String> signature(LootManifest manifest) {
        List<String> out = new ArrayList<>();
        for (LootStack stack : manifest.stacks) {
            out.add(stack.kind + ":" + stack.itemId + ":" + stack.quantity);
        }
        return out;
    }
}
