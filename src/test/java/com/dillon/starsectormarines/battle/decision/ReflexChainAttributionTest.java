package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ReflexChain#run} charges every reflex it consults to that reflex's
 * bucket, fired or not — the chain runs for every combatant every tick, and
 * a link that declines is still work the tick paid for.
 */
class ReflexChainAttributionTest {

    @AfterEach
    void unbindProfile() {
        TickInnerProfile.releaseCurrentThread();
    }

    private static Reflex reflex(String name, boolean fires) {
        return new Reflex() {
            @Override public String name() { return name; }
            @Override public boolean interrupt(long unit, Squad squad,
                                               ReflexContext context, BattleControl sim) {
                return fires;
            }
        };
    }

    private static BattleSimulation simulation() {
        NavigationGrid grid = new NavigationGrid(8, 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(8, 8));
    }

    @Test
    void everyConsultedReflexIsChargedToItsBucketAndTheChainStopsAtTheFirstThatFires() {
        BattleSimulation sim = simulation();
        long unit = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE_BLUE, 2, 2));
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        List<Reflex> chain = List.of(
                reflex("COOLDOWNS", false),
                reflex("REJOIN", true),
                reflex("LANE_SIDESTEP", true));

        Reflex fired = ReflexChain.run(chain, unit, null, new ReflexContext(true), sim);

        assertSame(chain.get(1), fired);
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.REFLEX_COOLDOWNS));
        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.REFLEX_REJOIN));
        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.REFLEX_LANE_SIDESTEP),
                "a reflex after the one that fired was never consulted");
        assertTrue(profile.nanosOf(TickInnerProfile.Bucket.REFLEX_COOLDOWNS) >= 0L);
    }

    @Test
    void aReflexWithoutABucketOfItsOwnIsChargedToOther() {
        BattleSimulation sim = simulation();
        long unit = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE_BLUE, 2, 2));
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);

        ReflexChain.run(List.of(reflex("PLAYER_LOCOMOTION", false)), unit, null,
                new ReflexContext(true), sim);

        assertEquals(1, profile.countOf(TickInnerProfile.Bucket.REFLEX_OTHER));
    }

    @Test
    void withNoProfileBoundTheChainStillRuns() {
        BattleSimulation sim = simulation();
        long unit = sim.spawn(new EntitySpec("m", Faction.MARINE, UnitType.MARINE_BLUE, 2, 2));
        TickInnerProfile.releaseCurrentThread();

        Reflex fired = ReflexChain.run(List.of(reflex("REJOIN", true)), unit, null,
                new ReflexContext(true), sim);

        assertEquals("REJOIN", fired.name());
    }
}
