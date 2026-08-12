package com.dillon.starsectormarines.ops.loot;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootSettlementPlannerTest {

    @Test
    void allocatesIndependentCapacityBucketsAndFencesPartialOverflow() {
        LootManifest manifest = new LootManifest(1L, 100, 100_000, Arrays.asList(
                stack(LootKind.COMMODITY, "supplies", 100, 10, 1f),
                stack(LootKind.COMMODITY, "fuel", 25, 10, 0f),
                stack(LootKind.COMMODITY, "marines", 20, 50, 0f),
                stack(LootKind.WEAPON, "heavy_blaster", 1, 5_000, 20f)));
        LootSelection selection = selectAll(manifest);

        LootSettlementPlan plan = LootSettlementPlanner.plan(selection,
                new LootCapacitySnapshot(105f, 20f, 12f));

        assertLine(plan.lines.get(0), LootCapacityBucket.CARGO, 100, 0, 0);
        assertLine(plan.lines.get(1), LootCapacityBucket.FUEL, 20, 5, 37);
        assertLine(plan.lines.get(2), LootCapacityBucket.PERSONNEL, 12, 8, 300);
        assertLine(plan.lines.get(3), LootCapacityBucket.CARGO, 0, 1, 3_750);
        assertEquals(132, plan.keptUnits);
        assertEquals(14, plan.fencedUnits);
        assertEquals(4_087, plan.fencedCredits);
    }

    @Test
    void walksOnlySelectedStacksInManifestOrder() {
        LootManifest manifest = new LootManifest(1L, 100, 20_000, Arrays.asList(
                stack(LootKind.WEAPON, "first", 1, 1_000, 5f),
                stack(LootKind.WEAPON, "second", 1, 2_000, 5f),
                stack(LootKind.WEAPON, "third", 1, 3_000, 5f)));
        LootSelection selection = new LootSelection(manifest);
        selection.toggle(0);
        selection.toggle(2);

        LootSettlementPlan plan = LootSettlementPlanner.plan(selection,
                new LootCapacitySnapshot(5f, 0f, 0f));

        assertEquals(2, plan.lines.size());
        assertEquals(0, plan.lines.get(0).stackIndex);
        assertEquals(2_250, plan.lines.get(1).fencedCredits);
    }

    @Test
    void nullSelectionProducesEmptyPlan() {
        assertEquals(0, LootSettlementPlanner.plan(null, null).lines.size());
    }

    private static LootSelection selectAll(LootManifest manifest) {
        LootSelection selection = new LootSelection(manifest);
        for (int i = 0; i < manifest.stacks.size(); i++) selection.toggle(i);
        return selection;
    }

    private static LootStack stack(LootKind kind, String id, int quantity,
                                   int unitValue, float cargoPerUnit) {
        LootCandidate candidate = new LootCandidate(kind, id, id, null,
                unitValue, cargoPerUnit, 1f, quantity, quantity);
        return new LootStack(candidate, quantity);
    }

    private static void assertLine(LootSettlementLine line, LootCapacityBucket bucket,
                                   int kept, int fenced, int fencedCredits) {
        assertEquals(bucket, line.bucket);
        assertEquals(kept, line.keptQuantity);
        assertEquals(fenced, line.fencedQuantity);
        assertEquals(fencedCredits, line.fencedCredits);
    }
}
