package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.ops.loot.LootCapacityBucket;
import com.dillon.starsectormarines.ops.loot.LootCandidate;
import com.dillon.starsectormarines.ops.loot.LootFleetHold;
import com.dillon.starsectormarines.ops.loot.LootKind;
import com.dillon.starsectormarines.ops.loot.LootSettlementLine;
import com.dillon.starsectormarines.ops.loot.LootSettlementPlan;
import com.dillon.starsectormarines.ops.loot.LootStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LootScreenHoldLabelTest {

    private static final Map<String, String> COPY = Map.of(
            "lootScreenHoldFmt", "{0} {1} / {2} · {3} after",
            "lootScreenHoldCargo", "Cargo", "lootScreenHoldFuel", "Fuel",
            "lootScreenHoldCrew", "Crew", "lootScreenUnavailable", "Unavailable");

    private static final LootFleetHold HOLD = new LootFleetHold(340f, 1200f, 50f, 300f, 80f, 120f);

    @Test
    void showsCargoNowAndAfterTheKeptLines() {
        LootSettlementPlan plan = new LootSettlementPlan(List.of(
                line(LootCapacityBucket.CARGO, 52f)));

        assertEquals("Cargo 340 / 1,200 · 392 after", LootScreen.holdLabel(HOLD, plan, COPY::get));
    }

    @Test
    void addsFuelAndCrewOnlyWhenSomethingKeptLandsInThem() {
        LootSettlementPlan plan = new LootSettlementPlan(List.of(
                line(LootCapacityBucket.FUEL, 20f)));

        assertEquals("Cargo 340 / 1,200 · 340 after · Fuel 50 / 300 · 70 after",
                LootScreen.holdLabel(HOLD, plan, COPY::get));
        assertEquals("Unavailable", LootScreen.holdLabel(null, plan, COPY::get));
    }

    private static LootSettlementLine line(LootCapacityBucket bucket, float space) {
        LootStack stack = new LootStack(new LootCandidate(LootKind.COMMODITY, "x", "x", null,
                1, 1f, 1f, 1, 1), 1);
        return new LootSettlementLine(0, stack, bucket, 1, 0, 0, space);
    }
}
