package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.warship.Warship;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Choosing a hull as a strike target: hostile only, ordnance that can hurt it, and two craft at most. */
class HullStrikeTest {

    private static BattleSimulation sim() {
        return SceneBuilder.openGround(200, 64).missionCompletion(false)
                .unit("m", Faction.MARINE, UnitType.MARINE, 5, 30)
                .unit("d", Faction.DEFENDER, UnitType.MARINE, 190, 30)
                .build().sim();
    }

    private static void prime() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    @Test
    void onlyAHostileLiveHullIsAHullTarget() throws Exception {
        prime();
        BattleSimulation sim = sim();
        sim.addWarship(Warship.fit("enforcer", "enforcer_Balanced", Faction.MARINE, 100f, 30f, 90f));
        sim.addWarship(Warship.fit("enforcer", "enforcer_Balanced", Faction.DEFENDER, 120f, 30f, 90f));

        assertEquals(1, sim.hostileHulls(Faction.DEFENDER).size(), "the marine hull is hostile to the defenders");
        assertEquals(1, sim.hostileHulls(Faction.MARINE).size(), "and only that one");
        assertTrue(sim.hostileHulls(Faction.DEFENDER).get(0).x() < 110f, "the marine hull, not their own");
    }

    @Test
    void anOrdnanceTheArmourGlancesOffIsNeverChosen() throws Exception {
        prime();
        BattleSimulation sim = sim();
        sim.addWarship(Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 100f, 30f, 90f));

        assertNull(HullStrike.choose(sim, Faction.DEFENDER, h -> AirOrdnance.BEAM), "13 x 13 against 1750 armour glances");
        assertNull(HullStrike.choose(sim, Faction.DEFENDER, h -> null), "nothing to drop");
        assertNotNull(HullStrike.choose(sim, Faction.DEFENDER, h -> AirOrdnance.BOMBS));
    }

    @Test
    void aHullIsDroppedFromTheChoiceOnceTwoCraftAreOnIt() throws Exception {
        prime();
        BattleSimulation sim = sim();
        Warship ship = Warship.fit("enforcer", "enforcer_Balanced", Faction.MARINE, 100f, 30f, 90f);
        sim.addWarship(ship);
        HullTarget hull = HullStrike.choose(sim, Faction.DEFENDER, h -> AirOrdnance.BOMBS);
        assertNotNull(hull);

        for (int i = 0; i < HullStrike.MAX_ATTACKERS; i++) {
            assertEquals(i, HullStrike.attackers(sim, Faction.DEFENDER, hull));
            sim.spawnSortie(FighterProfile.DAGGER, Faction.DEFENDER, hull.x(), hull.y(), 199f, 30f, 199f, 30f, 0f);
            sim.world().mission(lastAir(sim)).strikeSortie = true;
        }
        assertEquals(HullStrike.MAX_ATTACKERS, HullStrike.attackers(sim, Faction.DEFENDER, hull));
        assertNull(HullStrike.choose(sim, Faction.DEFENDER, h -> AirOrdnance.BOMBS), "the third goes to the ground");
        assertFalse(sim.hostileHulls(Faction.MARINE).iterator().hasNext(), "their own side's craft never count against a rival");
    }

    private static long lastAir(BattleSimulation sim) {
        long[] ids = sim.getAirEntityIds();
        long last = ids[0];
        for (long id : ids) last = Math.max(last, id);
        return last;
    }
}
