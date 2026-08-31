package com.dillon.starsectormarines.battle.sim;

import com.dillon.starsectormarines.battle.air.BasedAircraft;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.mech.MechVariant;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.turret.MapTurret;
import com.dillon.starsectormarines.battle.turret.TurretCatalogRegistry;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * How big a body is, asked the two ways the battle asks it.
 *
 * <p>{@code UnitRosterService.radius} answers by id for selection, ballistics,
 * blast catch and the spatial index; {@link SeparationSystem} answers columnar,
 * off the archetype tables, for the shove. Both derivations exist on purpose —
 * one lookup per shot against one array read per unit per tick — and both were
 * written out longhand for a while, which is how they came to disagree: the
 * airframe branch landed on the by-id side only, so a parked Valkyrie was
 * several cells of aircraft to a round and half a cell to a shoulder.
 *
 * <p>Nothing about that failure was loud. Both numbers were plausible floats
 * and nothing threw, so this asks every kind of body that has a per-instance
 * size for both answers and requires them to match. A new source of size added
 * to one caller and not the other fails here.
 */
class OneBodyIsOneSizeTest {

    private static final float EPS = 1e-4f;

    private static BattleSimulation arena() {
        NavigationGrid grid = new NavigationGrid(60, 60);
        for (int y = 0; y < 60; y++) {
            for (int x = 0; x < 60; x++) grid.setWalkableFloor(x, y);
        }
        return new BattleSimulation(grid, new CellTopology(60, 60));
    }

    @Test
    void everyKindOfBodyIsTheSameSizeToAShoveAsToAShot() {
        BattleSimulation sim = arena();
        long infantry = sim.spawn(
                new EntitySpec("m", Faction.MARINE, UnitType.MARINE, 10, 10));
        long mech = sim.spawn(
                new EntitySpec("k", Faction.MARINE, UnitType.HEAVY_MECH, 20, 10)
                        .mechVariant(MechVariant.HOUND));
        long aircraft = sim.spawn(BasedAircraft.create("af", Faction.DEFENDER,
                ShuttleType.VALKYRIE, 30, 10, ShuttleType.VALKYRIE.maxHp()));
        long turret = sim.spawn(MapTurret.create("t", Faction.DEFENDER,
                TurretCatalogRegistry.VULCAN_STRUCTURE_ID, 40, 10));

        SeparationSystem separation = new SeparationSystem(
                sim.getRoster(), sim.getUnitIndex(), sim.getGrid());
        separation.tick(BattleSimulation.TICK_DT);

        assertEquals(sim.getRoster().radius(infantry), separation.cachedRadius(infantry), EPS,
                "plain infantry, whose whole archetype is one size");
        assertEquals(sim.getRoster().radius(mech), separation.cachedRadius(mech), EPS,
                "a mech, sized by its variant");
        assertEquals(sim.getRoster().radius(aircraft), separation.cachedRadius(aircraft), EPS,
                "a parked aircraft, sized by its airframe");
        assertEquals(sim.getRoster().radius(turret), separation.cachedRadius(turret), EPS,
                "a turret, sized by its structure");
    }

    /**
     * Agreement alone would still be satisfied by both sides falling back to
     * the archetype, which is the shape the defect actually had — so each
     * per-instance body must also have moved off its {@link UnitType}.
     *
     * <p>No turret here: every emplacement in the catalog today is authored at
     * exactly {@link UnitType#TURRET}'s placeholder, so the structure branch
     * and the fallback are the same number and this assertion could only pass
     * for the wrong reason. The turret's agreement is pinned above.
     */
    @Test
    void aPerInstanceBodyIsNotTheArchetypesSize() {
        BattleSimulation sim = arena();
        long mech = sim.spawn(
                new EntitySpec("k", Faction.MARINE, UnitType.HEAVY_MECH, 20, 10)
                        .mechVariant(MechVariant.HOUND));
        long aircraft = sim.spawn(BasedAircraft.create("af", Faction.DEFENDER,
                ShuttleType.VALKYRIE, 30, 10, ShuttleType.VALKYRIE.maxHp()));

        SeparationSystem separation = new SeparationSystem(
                sim.getRoster(), sim.getUnitIndex(), sim.getGrid());
        separation.tick(BattleSimulation.TICK_DT);

        assertNotEquals(UnitType.HEAVY_MECH.radius, separation.cachedRadius(mech), EPS,
                "a Hound is not a stock heavy");
        assertNotEquals(UnitType.BASED_AIRCRAFT.radius, separation.cachedRadius(aircraft), EPS,
                "a Valkyrie on its chocks is several cells of aircraft");
    }
}
