package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.decision.goap.Predicate;
import com.dillon.starsectormarines.battle.decision.goap.WorldState;
import com.dillon.starsectormarines.battle.decision.goap.world.WorldStateBuilder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SquadBeliefTest {

    private static BattleSimulation openSim() {
        NavigationGrid grid = new NavigationGrid(36, 18);
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                grid.setWalkableFloor(x, y);
            }
        }
        return new BattleSimulation(grid,
                new CellTopology(grid.getWidth(), grid.getHeight()));
    }

    @Test
    void directObservationSharesEveryContactAndRefreshesMovedCell() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        sim.spawn(new EntitySpec("observer", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        long first = sim.spawn(new EntitySpec("first", Faction.DEFENDER,
                UnitType.MARINE, 10, 5));
        sim.spawn(new EntitySpec("second", Faction.DEFENDER,
                UnitType.MARINE, 10, 6));

        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(2, squad.believedContacts().size());
        assertEquals(10, squad.lastSeenEnemyX,
                "freshest-contact tie resolves deterministically by unit id");
        assertEquals(5, squad.lastSeenEnemyY);
        BelievedContact original = squad.believedContact(first);
        assertNotNull(original);
        assertEquals(1f, original.confidence());
        assertTrue(original.observedOnTick(sim.simTickIndex));

        sim.world().setCellPos(first, 11, 5);
        sim.advance(BattleSimulation.TICK_DT);

        assertEquals(2, squad.believedContacts().size(),
                "refresh replaces the id-keyed contact instead of duplicating it");
        assertEquals(11, squad.believedContact(first).lastSeenCellX());
        assertEquals(sim.simTickIndex,
                squad.believedContact(first).lastSeenTick());
    }

    @Test
    void directObservationUsesPerMemberVisionRangeAcrossBucketBoundary() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        long observer = sim.spawn(new EntitySpec("observer", Faction.MARINE,
                UnitType.MARINE, 15, 5).squad(squadId).visionRange(3f));
        long inside = sim.spawn(new EntitySpec("inside", Faction.DEFENDER,
                UnitType.MARINE, 18, 5));
        long outside = sim.spawn(new EntitySpec("outside", Faction.DEFENDER,
                UnitType.MARINE, 19, 5));
        sim.world().setPos(observer, 15.01f, 5.5f);
        sim.world().setPos(inside, 18.99f, 5.5f);

        sim.advance(BattleSimulation.TICK_DT);

        assertNotNull(squad.believedContact(inside),
                "cell-range visibility survives a bucket seam and opposing subcell offsets");
        assertNull(squad.believedContact(outside),
                "open line of sight does not reveal a target beyond the observer's vision range");
    }

    @Test
    void beliefPredicatesDoNotDiscoverEnemyBehindWallAndMemoryExpires() {
        BattleSimulation sim = openSim();
        int squadId = sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        Squad squad = sim.getSquad(squadId);
        sim.spawn(new EntitySpec("observer", Faction.MARINE,
                UnitType.MARINE, 5, 5).squad(squadId));
        sim.spawn(new EntitySpec("enemy", Faction.DEFENDER,
                UnitType.MARINE, 20, 5));
        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            sim.getGrid().setWalkable(12, y, false);
        }

        sim.advance(BattleSimulation.TICK_DT);
        WorldState unknown = WorldStateBuilder.build(squad, sim);

        assertFalse(unknown.get(Predicate.HAS_TARGET));
        assertFalse(unknown.get(Predicate.HAS_LOS_TO_TARGET));
        assertFalse(unknown.get(Predicate.IN_RANGE_OF_TARGET));

        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            sim.getGrid().setWalkableFloor(12, y);
        }
        sim.advance(BattleSimulation.TICK_DT);
        assertTrue(WorldStateBuilder.build(squad, sim)
                .get(Predicate.HAS_LOS_TO_TARGET));

        for (int y = 0; y < sim.getGrid().getHeight(); y++) {
            sim.getGrid().setWalkable(12, y, false);
        }
        sim.advance(BattleSimulation.TICK_DT);
        BelievedContact fading = squad.believedContacts().get(0);
        assertTrue(fading.confidence() < 1f);
        assertFalse(fading.observedOnTick(sim.simTickIndex));
        assertTrue(WorldStateBuilder.build(squad, sim).get(Predicate.HAS_TARGET));
        assertFalse(WorldStateBuilder.build(squad, sim)
                .get(Predicate.HAS_LOS_TO_TARGET));

        sim.advance(Squad.BELIEF_LIFETIME_SECONDS);

        assertTrue(squad.believedContacts().isEmpty());
        assertEquals(-1, squad.lastSeenEnemyX);
        assertEquals(-1, squad.lastSeenEnemyY);
        assertFalse(WorldStateBuilder.build(squad, sim).get(Predicate.HAS_TARGET));
    }
}
