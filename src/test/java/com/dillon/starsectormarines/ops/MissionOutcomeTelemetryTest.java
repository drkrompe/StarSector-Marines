package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.sim.CombatTelemetryRow;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.campaign.AbandonedColonyArchiveOutcome;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the telemetry payload {@link MissionOutcome} carries across the
 * battle/campaign seam — progression S3 slice 2
 * ({@code s3-per-soldier-telemetry.md}).
 *
 * <p>The battle world is ephemeral and never serializes, so what crosses has
 * to be plain frozen data. These guard that it is frozen at outcome time, for
 * the same replay-determinism reason as the rest of the class: recomputing an
 * outcome must not be able to see a different battle.
 */
class MissionOutcomeTelemetryTest {

    private static CombatTelemetryRow row(String soldierId, int kills) {
        return new CombatTelemetryRow(1L, "marine", Faction.MARINE, UnitType.MARINE,
                soldierId, true, 20, 7, 90f, 0f, 12f, kills, 0);
    }

    @Test
    void telemetryIsFrozenAtOutcomeTime() {
        Map<String, CombatTelemetryRow> live = new HashMap<>();
        live.put("soldier-1", row("soldier-1", 2));

        MissionOutcome outcome = outcome(live);
        live.put("soldier-2", row("soldier-2", 5));
        live.clear();

        assertEquals(1, outcome.soldierTelemetry.size(),
                "later battle edits cannot reach a computed outcome");
        assertEquals(2, outcome.soldierTelemetry.get("soldier-1").kills());
        assertThrows(UnsupportedOperationException.class,
                () -> outcome.soldierTelemetry.put("soldier-3", row("soldier-3", 1)));
    }

    @Test
    void anOutcomeBuiltWithoutABattleCarriesNoTelemetry() {
        MissionOutcome outcome = new MissionOutcome(true, "no-battle", "No Battle",
                MissionType.ASSAULT, RiskLevel.MEDIUM, MissionSource.GENERATED,
                0, 0, 0, 0,
                null, null, null, null,
                0, 0f, null,
                null, null, null,
                -1L, -1L, -1, -1L, 0, -1,
                -1, -1, AbandonedColonyArchiveOutcome.NONE, 0, 0, 0,
                Set.of(), Set.of(), Set.of());

        assertTrue(outcome.soldierTelemetry.isEmpty(),
                "the telemetry-free overload is what every campaign-side caller uses");
    }

    @Test
    void nullKeysAndRowsAreDroppedRatherThanStored() {
        Map<String, CombatTelemetryRow> live = new HashMap<>();
        live.put("soldier-1", row("soldier-1", 1));
        live.put(null, row("soldier-x", 1));
        live.put("soldier-2", null);

        MissionOutcome outcome = outcome(live);

        assertEquals(Set.of("soldier-1"), outcome.soldierTelemetry.keySet());
        assertFalse(outcome.soldierTelemetry.containsKey("soldier-2"));
    }

    private static MissionOutcome outcome(Map<String, CombatTelemetryRow> telemetry) {
        return new MissionOutcome(true, "telemetry-test", "Telemetry Test",
                MissionType.ASSAULT, RiskLevel.MEDIUM, MissionSource.GENERATED,
                0, 0, 1, 0,
                null, null, null, null,
                0, 0f, null,
                null, null, null,
                -1L, -1L, -1, -1L, 0, -1,
                -1, -1, AbandonedColonyArchiveOutcome.NONE, 0, 0, 0,
                Collections.singleton("soldier-1"), Set.of(), Set.of(),
                telemetry);
    }
}
