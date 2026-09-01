package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleControl;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The runner's whole contract: in order, stop at the first interrupt, and say
 * which one it was.
 *
 * <p>Synthetic reflexes rather than any arm's real list — what is under test
 * here is the traversal. Whether a later reflex is <em>asked</em> matters as
 * much as the return value: an interrupt is supposed to consume the tick, and a
 * runner that kept going would have every entry firing at once while still
 * reporting only the first.
 */
public class ReflexChainTest {

    /** Records that it was asked, and answers however it was built. */
    private static final class Probe implements Reflex {
        private final String name;
        private final boolean interrupts;
        private final List<String> log;

        Probe(String name, boolean interrupts, List<String> log) {
            this.name = name;
            this.interrupts = interrupts;
            this.log = log;
        }

        @Override public String name() { return name; }

        @Override public boolean interrupt(long unit, Squad squad,
                                           ReflexContext context, BattleControl sim) {
            log.add(name);
            return interrupts;
        }
    }

    private static final ReflexContext ANY = new ReflexContext(true);

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(16, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(16, 16));
    }

    private static long spawnThinker(BattleSimulation sim) {
        return sim.spawn(new EntitySpec("probe", Faction.MARINE, UnitType.MARINE, 4, 4));
    }

    @Test
    public void theChainStopsAtTheFirstReflexThatInterrupts() {
        BattleSimulation sim = openSim();
        long unit = spawnThinker(sim);
        List<String> asked = new ArrayList<>();
        Probe passes = new Probe("PASSES", false, asked);
        Probe interrupts = new Probe("INTERRUPTS", true, asked);
        Probe later = new Probe("LATER", true, asked);

        Reflex fired = ReflexChain.run(List.of(passes, interrupts, later),
                unit, null, ANY, sim);

        assertSame(interrupts, fired,
                "the runner returns the reflex that consumed the tick, so a caller "
                        + "can say which one it was");
        assertEquals(List.of("PASSES", "INTERRUPTS"), asked,
                "an interrupt consumes the tick: nothing after it may run, or "
                        + "priority order means nothing");
        assertEquals("INTERRUPTS", sim.world().lastReflex(unit),
                "the interrupting reflex leaves its name behind, so a dump can say "
                        + "why the unit is off its step");
    }

    @Test
    public void aChainNobodyInterruptsReturnsNullHavingAskedEveryone() {
        BattleSimulation sim = openSim();
        long unit = spawnThinker(sim);
        List<String> asked = new ArrayList<>();

        Reflex fired = ReflexChain.run(
                List.of(new Probe("FIRST", false, asked),
                        new Probe("SECOND", false, asked)),
                unit, null, ANY, sim);

        assertNull(fired, "null is the runner's way of saying the unit may execute its step");
        assertEquals(List.of("FIRST", "SECOND"), asked);
        assertNull(sim.world().lastReflex(unit),
                "a tick nothing interrupted clears the diagnostic, or a stale name "
                        + "outlives the thing it described");
    }

    @Test
    public void anEmptyChainReturnsNull() {
        BattleSimulation sim = openSim();
        assertNull(ReflexChain.run(List.of(), spawnThinker(sim), null, ANY, sim),
                "an arm with nothing above its step is a legal arm");
    }
}
