package com.dillon.starsectormarines.battle.decision;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Pins what a unit is allowed to know when it picks a target.
 *
 * <p>Acquisition used to be omniscient in two separate ways, and neither was a
 * deliberate design: the visibility test is a line between two cells and carries
 * no distance bound, so a marine acquired anybody down an unobstructed lane at
 * any range at all; and the any-distance fallback handed back the nearest
 * hostile with no line-of-sight requirement whatsoever, so a unit that could see
 * nothing still walked at whoever happened to be closest through the walls.
 *
 * <p>What replaces it is the model the rest of the AI already runs on. A unit
 * may target what it can see itself, or what its squad believes — and squad
 * belief is exactly that seeing pooled over everybody sharing a net, which is
 * why there is no separate fire-team rule: a fire team is part of one squad and
 * already reads the whole squad's picture.
 */
public class PerceptionGatedTargetingTest {

    private static final int W = 100;
    private static final int H = 40;
    private static final int ROW = 20;

    /** A marine sees 36 cells (UnitType.MARINE) and shoots 24. */
    private static final int SPOTTER_X = 40;
    private static final int HOSTILE_X = 60;
    /** 55 cells from the hostile: well outside a marine's own sight. */
    private static final int BLIND_X = 5;

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        // The question is about acquisition, not about who wins; a decided
        // battle stops ticking the pass that builds belief.
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    private static long marine(BattleSimulation sim, int squadId, String name, int x) {
        return sim.spawn(new EntitySpec(name, Faction.MARINE, UnitType.MARINE, x, ROW)
                .squad(squadId));
    }

    private static long hostile(BattleSimulation sim) {
        EntitySpec spec = new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE,
                HOSTILE_X, ROW);
        spec.moveSpeed = 0f;
        return sim.spawn(spec);
    }

    @Test
    public void aHostileBeyondItsOwnSightIsNotAcquired() {
        BattleSimulation sim = arena();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long blind = marine(sim, squadId, "m0", BLIND_X);
        hostile(sim);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(0L, sim.getTacticalScoring().findBestTarget(blind),
                "an unobstructed line across 55 cells of open ground is not sight; "
                        + "a marine sees 36 and nobody has told it about this one");
    }

    /**
     * The comms law, and the whole of why belief is the right source: the same
     * marine, the same hostile, the same 55 cells — and a squadmate standing
     * where it can see.
     */
    @Test
    public void aSquadmateSeeingItIsEnoughForTheWholeSquadToTargetIt() {
        BattleSimulation sim = arena();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long blind = marine(sim, squadId, "m0", BLIND_X);
        marine(sim, squadId, "m1", SPOTTER_X);
        long enemy = hostile(sim);
        sim.advance(BattleSimulation.TICK_DT);

        Squad squad = sim.getSquad(squadId);
        assertNotEquals(null, squad.believedContact(enemy),
                "the spotter is 20 cells away with a clear line, so the squad believes in it");
        assertEquals(enemy, sim.getTacticalScoring().findBestTarget(blind),
                "and a squad that believes in a hostile may act on it, whichever member "
                        + "of it happens to be doing the acting");
    }

    /**
     * The fallback that used to make this moot. It returned the nearest hostile
     * with no line-of-sight test at all, so a unit sealed away from one still
     * acquired it and walked at a wall.
     */
    @Test
    public void aHostileBehindAWallIsNotAcquiredMerelyForBeingNearest() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        for (int y = 0; y < H; y++) grid.setWalkable(SPOTTER_X, y, false);
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);

        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        // Ten cells short of the wall, and the hostile ten cells past it: well
        // inside sight, and nothing to see.
        long walled = marine(sim, squadId, "m0", SPOTTER_X - 10);
        sim.spawn(new EntitySpec("d0", Faction.DEFENDER, UnitType.MARINE,
                SPOTTER_X + 10, ROW));
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(0L, sim.getTacticalScoring().findBestTarget(walled),
                "nearest is not the same as known; there is a wall in the way and "
                        + "no squadmate has seen anybody");
    }

    /**
     * The invariant {@code UnitType} states and {@code setAttackRange} can
     * silently break. A unit that cannot acquire something inside its own
     * weapon range would hold its fire while being shot by an enemy it was
     * perfectly able to hit.
     */
    @Test
    public void aUnitCanAlwaysAcquireInsideItsOwnWeaponRangeEvenIfThatOutrunsItsSightStat() {
        BattleSimulation sim = arena();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long sniper = marine(sim, squadId, "m0", HOSTILE_X - 50);
        // Fifty cells of reach on a marine whose sight stat says 36.
        sim.world().setAttackRange(sniper, 50f);
        long enemy = hostile(sim);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(enemy, sim.getTacticalScoring().findBestTarget(sniper),
                "reach is a floor under sight: whatever the sight stat says, a unit "
                        + "can acquire what it is able to shoot");
    }
}
