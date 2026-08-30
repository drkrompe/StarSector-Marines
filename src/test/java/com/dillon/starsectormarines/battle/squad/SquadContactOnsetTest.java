package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One member's contact becoming the squad's. The moments themselves are pinned
 * next door; this is about the publication — which contact a squad ends up
 * facing when several of its people are in trouble at once, and that it stops
 * being published the instant it stops being true.
 */
class SquadContactOnsetTest {

    private static final int W = 64;
    private static final int H = 64;
    private static final int SQUAD_X = 20;
    private static final int SQUAD_Y = 20;

    private static BattleSimulation openArena() {
        NavigationGrid grid = new NavigationGrid(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
        }
        BattleSimulation sim = new BattleSimulation(grid, new CellTopology(W, H));
        sim.setMissionCompletionEnabled(false);
        return sim;
    }

    /**
     * A marine squad whose members stand where they are put. Inert by role so
     * the reading is about the publication rather than about whatever their own
     * behaviour chose this second.
     */
    private static Squad squadAt(BattleSimulation sim, int size, int x, int y) {
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        for (int i = 0; i < size; i++) {
            sim.spawn(new EntitySpec("m" + squadId + "-" + i, Faction.MARINE,
                    UnitType.MARINE, x + i, y)
                    .role(UnitRole.STRUCTURE)
                    .squad(squadId));
        }
        Squad squad = sim.getSquad(squadId);
        squad.originalSize = size;
        squad.aliveMembers = size;
        return squad;
    }

    private static long hostile(BattleSimulation sim, int x, int y) {
        return sim.spawn(new EntitySpec("d" + x + "-" + y + "-" + sim.liveUnitCount(),
                Faction.DEFENDER, UnitType.MARINE, x, y)
                .role(UnitRole.STRUCTURE));
    }

    /** One tick is enough: the system republishes from scratch every time. */
    private static void settle(BattleSimulation sim) {
        sim.advance(BattleSimulation.TICK_DT);
    }

    @Test
    void aSquadWithNobodyInTroublePublishesNothing() {
        BattleSimulation sim = openArena();
        Squad squad = squadAt(sim, 4, SQUAD_X, SQUAD_Y);
        hostile(sim, W - 2, H - 2);
        settle(sim);

        assertEquals(0L, squad.onsetContactId);
        assertEquals(-1, squad.onsetTick);
    }

    @Test
    void oneMemberWalkingIntoSomebodyIsTheWholeSquadsContact() {
        BattleSimulation sim = openArena();
        Squad squad = squadAt(sim, 4, SQUAD_X, SQUAD_Y);
        // Beside the last member of the file and nowhere near the first.
        long met = hostile(sim, SQUAD_X + 3, SQUAD_Y + 3);
        settle(sim);

        assertEquals(met, squad.onsetContactId,
                "what one of them is looking at is what the squad is facing");
        assertTrue(squad.onsetAtCloseQuarters);
        assertNotEquals(0L, squad.onsetSeenBy, "and it is somebody in particular");
    }

    @Test
    void theThingOnTopOfThemOutranksTheOneAtRange() {
        BattleSimulation sim = openArena();
        Squad squad = squadAt(sim, 4, SQUAD_X, SQUAD_Y);
        long sniper = hostile(sim, SQUAD_X + 30, SQUAD_Y);
        sim.world().setTargetId(sniper, sim.squadMemberAt(squad.id, 0));
        long met = hostile(sim, SQUAD_X + 2, SQUAD_Y + 2);
        settle(sim);

        assertEquals(met, squad.onsetContactId,
                "a squad can only face one way, and it faces the near thing");
        assertTrue(squad.onsetAtCloseQuarters);
    }

    @Test
    void aSquadRangedOnFromDownALaneStillPublishesTheBearing() {
        BattleSimulation sim = openArena();
        Squad squad = squadAt(sim, 4, SQUAD_X, SQUAD_Y);
        long sniper = hostile(sim, SQUAD_X + 30, SQUAD_Y);
        sim.world().setTargetId(sniper, sim.squadMemberAt(squad.id, 0));
        settle(sim);

        assertEquals(sniper, squad.onsetContactId);
        assertFalse(squad.onsetAtCloseQuarters,
                "and it is marked as the kind that must not stop the advance");
    }

    @Test
    void theOnsetStopsBeingPublishedWhenItStopsBeingTrue() {
        BattleSimulation sim = openArena();
        Squad squad = squadAt(sim, 4, SQUAD_X, SQUAD_Y);
        long met = hostile(sim, SQUAD_X + 2, SQUAD_Y + 2);
        settle(sim);
        assertEquals(met, squad.onsetContactId);

        // Killed through the damage path, so the death is dispatched the way a
        // real one is rather than the roster being edited underneath.
        sim.applyDamage(met, 100_000f, 100_000f);
        settle(sim);
        settle(sim);

        assertEquals(0L, squad.onsetContactId,
                "release needs no timer: the situation simply is not there");
        assertEquals(-1, squad.onsetTick);
    }

    @Test
    void aSquadThatHasBeenKilledPublishesNothing() {
        BattleSimulation sim = openArena();
        // A marine outside the squad, so the side is never empty and the clock
        // keeps running once the squad itself is gone.
        sim.spawn(new EntitySpec("bystander", Faction.MARINE, UnitType.MARINE,
                W - 2, 2).role(UnitRole.STRUCTURE));
        Squad squad = squadAt(sim, 2, SQUAD_X, SQUAD_Y);
        hostile(sim, SQUAD_X + 2, SQUAD_Y);
        settle(sim);
        assertNotEquals(0L, squad.onsetContactId);

        for (int i = sim.squadMemberCount(squad.id) - 1; i >= 0; i--) {
            sim.applyDamage(sim.squadMemberAt(squad.id, i), 100_000f, 100_000f);
        }
        settle(sim);
        settle(sim);

        assertEquals(0, squad.aliveMembers, "the squad is actually gone");
        assertEquals(0L, squad.onsetContactId,
                "and is not still reported as looking at anybody");
    }
}
