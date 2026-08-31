package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A craft leaving its own hardstand climbs off it, and nothing pops it to
 * cruising altitude.
 *
 * <p>The mirror of {@code LandingIsFlownTest}. {@link ShuttleState#LOADING}
 * pins a craft to the ground, and {@link ShuttleState#INCOMING}'s altitude
 * ramp assumes the craft is already flying — its ratio is measured against
 * remaining leg distance, which is 100% of the leg on the very first sample.
 * Without {@link ShuttleState#PAD_ASCENT} between them, a sortie launched off
 * an authored airfield went from grounded to cruise-height in the single tick
 * boarding closed: altitude 0 → 1 and drawn scale 1.5 → 2.25, one frame apart.
 */
class PadAscentIsFlownTest {

    private static final int W = 60;
    private static final int H = 40;

    /**
     * Widest a single tick's altitude may move without reading as a pop.
     *
     * <p>The climb eases over {@code PAD_ASCENT_SEC} (0.9s); at 30 ticks/sec a
     * smoothstep's steepest tick moves under 6% of the full range. This is
     * generous slack above that — nowhere near the old fault's 70–100%.
     */
    private static final float MAX_ALTITUDE_STEP = 0.15f;

    private static BattleSimulation openSimulation() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    @Test
    void aShuttleClimbsOffItsPadRatherThanPoppingToCruise() {
        try (BattleSimulation sim = openSimulation()) {
            long craft = sim.spawnShuttle(ShuttleType.AEROSHUTTLE, Faction.DEFENDER,
                    50.5f, 20.5f, 10.5f, 10.5f, 10.5f, 10.5f, 0f, 2);
            ShuttleMission mission = sim.world().mission(craft);
            mission.state = ShuttleState.LOADING;
            mission.marinesRemaining = 0;
            mission.boardingPatience = 60f;
            sim.world().setAltitudeT(craft, 0f);

            int squadId = sim.mintSquad(Faction.DEFENDER, UnitType.MARINE);
            mission.embarkSquadId = squadId;
            for (int i = 0; i < 2; i++) {
                sim.spawn(new EntitySpec("m" + i, Faction.DEFENDER, UnitType.MARINE, 10, 10)
                        .squad(squadId));
            }

            EnumSet<ShuttleState> seen = EnumSet.noneOf(ShuttleState.class);
            float worstStep = 0f;
            float maxAltitudeSeen = 0f;
            for (int i = 0; i < 400; i++) {
                float before = sim.world().altitudeT(craft);
                sim.advance(BattleSimulation.TICK_DT);
                seen.add(mission.state);
                float after = sim.world().altitudeT(craft);
                worstStep = Math.max(worstStep, Math.abs(after - before));
                maxAltitudeSeen = Math.max(maxAltitudeSeen, after);
                if (mission.state == ShuttleState.INCOMING && after > 0.95f) break;
            }

            assertTrue(seen.contains(ShuttleState.PAD_ASCENT),
                    "lifted off without climbing off the pad: " + seen);
            assertTrue(seen.contains(ShuttleState.INCOMING),
                    "never actually flew away: " + seen);
            assertTrue(maxAltitudeSeen > 0.95f,
                    "never reached cruise height: peaked at " + maxAltitudeSeen);
            assertTrue(worstStep <= MAX_ALTITUDE_STEP,
                    "altitude moved " + worstStep + " in a single tick, which reads as a pop"
                            + " to cruise rather than a climb — seen phases: " + seen);
        }
    }
}
