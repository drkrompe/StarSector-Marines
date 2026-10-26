package com.dillon.starsectormarines.battle.infantry;

import com.dillon.starsectormarines.battle.command.objective.Objective;
import com.dillon.starsectormarines.battle.mech.MechRole;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One {@link EquipmentDropSystem} sweep over a bare roster: kit retrieval is infantry work, a mech is
 * {@code MECH_LOADOUT} membership, and a turret does not move.
 */
class KitRetrieverExcludesMechsTest {

    @Test
    void theMarineIsRecruitedEvenWithALiveMechNearerTheDrop() {
        Fixture f = new Fixture();
        long mech = f.spawnMech(UnitType.HEAVY_MECH, 11, 10);
        long marine = f.spawnMarine(18, 10);
        EquipmentDrop drop = f.drop(10, 10);

        f.system.tick();

        assertEquals(UnitRole.KIT_RETRIEVER, f.roster.role().role(marine));
        assertEquals(drop, f.roster.task().equipmentDropTarget(marine));
        assertEquals(List.of(marine), f.cleared);
        assertNotEquals(UnitRole.KIT_RETRIEVER, f.roster.role().role(mech));
        assertNull(f.roster.task().equipmentDropTarget(mech));
    }

    @Test
    void withOnlyMechsAvailableTheDropStaysUnassigned() {
        Fixture f = new Fixture();
        long first = f.spawnMech(UnitType.HEAVY_MECH, 11, 10);
        long second = f.spawnMech(UnitType.HEAVY_MECH, 10, 12);
        EquipmentDrop drop = f.drop(10, 10);

        f.system.tick();

        for (long mech : new long[]{first, second}) {
            assertNotEquals(UnitRole.KIT_RETRIEVER, f.roster.role().role(mech));
            assertNull(f.roster.task().equipmentDropTarget(mech));
        }
        assertTrue(f.cleared.isEmpty(), "no mech had its path cleared for a retrieval");
        assertFalse(drop.consumed);
        assertEquals(List.of(drop), f.drops.getEquipmentDrops());
    }

    @Test
    void exclusionFollowsTheCapabilityNotTheUnitType() {
        Fixture f = new Fixture();
        long loaded = f.spawnMech(UnitType.MARINE, 11, 10);
        f.drop(10, 10);

        f.system.tick();

        assertNotEquals(UnitRole.KIT_RETRIEVER, f.roster.role().role(loaded));
        assertTrue(f.cleared.isEmpty());
    }

    @Test
    void aMechStandingOnTheDropDoesNotPickItUp() {
        Fixture f = new Fixture();
        long mech = f.spawnMech(UnitType.HEAVY_MECH, 10, 10);
        EquipmentDrop drop = f.drop(10, 10);

        f.system.tick();

        assertFalse(drop.consumed);
        assertNotEquals(UnitRole.PLANTER, f.roster.role().role(mech));
    }

    @Test
    void aMarineTurretNearerTheDropIsNeverRecruited() {
        Fixture f = new Fixture();
        long turret = f.spawn(new EntitySpec("Emplacement", Faction.MARINE, UnitType.TURRET, 11, 10), UnitType.TURRET);
        long marine = f.spawnMarine(18, 10);
        EquipmentDrop drop = f.drop(10, 10);

        f.system.tick();

        assertNotEquals(UnitRole.KIT_RETRIEVER, f.roster.role().role(turret));
        assertEquals(UnitRole.KIT_RETRIEVER, f.roster.role().role(marine));
        assertEquals(drop, f.roster.task().equipmentDropTarget(marine));
    }

    private static final class Fixture {
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(24, 24), null);
        final EquipmentDropService drops = new EquipmentDropService(roster);
        final List<Long> cleared = new ArrayList<>();
        final EquipmentDropSystem system = new EquipmentDropSystem(roster, cleared::add, drops);

        Fixture() {
            NavigationGrid grid = new NavigationGrid(24, 24);
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
            roster.setNavigationGrid(grid);
        }

        long spawnMarine(int x, int y) {
            return spawn(new EntitySpec("Marine", Faction.MARINE, UnitType.MARINE, x, y), UnitType.MARINE);
        }

        /** A marine-faction body carrying {@code MECH_LOADOUT}; {@code type} lets one test decouple the two. */
        long spawnMech(UnitType type, int x, int y) {
            EntitySpec spec = new EntitySpec("Mech", Faction.MARINE, type, x, y);
            if (type.isMech()) spec = MechVariant.BULWARK.applyTo(spec);
            long id = spawn(spec, type);
            roster.world().attachMechLoadout(id, MechVariant.BULWARK.createLoadout(MechRole.BALANCED));
            return id;
        }

        long spawn(EntitySpec spec, UnitType type) {
            int squadId = roster.mintSquad(Faction.MARINE, type);
            long id = roster.spawn(spec.squad(squadId));
            var squad = roster.getSquad(squadId);
            squad.leaderId = id;
            squad.aliveMembers = 1;
            return id;
        }

        EquipmentDrop drop(int x, int y) {
            EquipmentDrop drop = new EquipmentDrop(x, y, new PendingObjective());
            drops.getEquipmentDrops().add(drop);
            return drop;
        }
    }

    private static final class PendingObjective implements Objective {
        @Override public Faction owningFaction() { return Faction.MARINE; }
        @Override public void tick(BattleView sim) { }
        @Override public boolean isComplete() { return false; }
        @Override public boolean isFailed() { return false; }
        @Override public String displayName() { return "Charge"; }
    }
}
