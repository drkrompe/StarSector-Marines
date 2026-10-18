package com.dillon.starsectormarines.battle.warship;

import com.dillon.starsectormarines.battle.flyby.FighterProfile;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneWorld;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The escort relation: who may follow whom, where an escort sits, what its threat picture and station do. */
class WarshipEscortTest {

    private static SceneWorld world() {
        return SceneBuilder.openGround(160, 60).missionCompletion(false)
                .unit("d", Faction.DEFENDER, UnitType.MARINE, 150, 30)
                .unit("m", Faction.MARINE, UnitType.MARINE, 10, 30)
                .build();
    }

    private static void prime() throws Exception {
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
    }

    @Test
    void onlyASmallerCombatHullMayEscortALargerOne() {
        ShipClass frigate = new ShipClass(ShipClass.HullSize.FRIGATE, ShipClass.Role.COMBAT, 0, ShipClass.Shield.NONE);
        ShipClass cruiser = new ShipClass(ShipClass.HullSize.CRUISER, ShipClass.Role.COMBAT, 0, ShipClass.Shield.NONE);
        ShipClass capital = new ShipClass(ShipClass.HullSize.CAPITAL, ShipClass.Role.COMBAT, 0, ShipClass.Shield.NONE);
        ShipClass carrier = new ShipClass(ShipClass.HullSize.CAPITAL, ShipClass.Role.CARRIER, 4, ShipClass.Shield.NONE);
        ShipClass tender = new ShipClass(ShipClass.HullSize.FRIGATE, ShipClass.Role.SUPPORT, 1, ShipClass.Shield.NONE);

        assertTrue(WarshipEscort.mayEscort(frigate, capital));
        assertTrue(WarshipEscort.mayEscort(cruiser, carrier), "the lead may be a carrier");
        assertTrue(WarshipEscort.mayEscort(frigate, cruiser));
        assertFalse(WarshipEscort.mayEscort(capital, capital), "a capital escorts nobody");
        assertFalse(WarshipEscort.mayEscort(cruiser, frigate), "never a larger hull");
        assertFalse(WarshipEscort.mayEscort(frigate, frigate), "never an equal hull");
        assertFalse(WarshipEscort.mayEscort(carrier, capital), "a carrier is not an escort");
        assertFalse(WarshipEscort.mayEscort(tender, capital), "nor a support hull");
    }

    @Test
    void automaticAssignmentFollowsTheSideFlagshipAndNobodyElse() throws Exception {
        prime();
        Warship flagship = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 20f, 30f, 0f);
        Warship destroyer = Warship.fit("enforcer", "enforcer_Balanced", Faction.MARINE, 20f, 10f, 0f);
        Warship frigate = Warship.fit("hound", "hound_Assault", Faction.MARINE, 20f, 50f, 0f);
        Warship enemyFrigate = Warship.fit("hound", "hound_Assault", Faction.DEFENDER, 120f, 50f, 0f);
        List<Warship> all = List.of(flagship, destroyer, frigate, enemyFrigate);

        assertSame(flagship, WarshipEscort.autoLead(destroyer, all));
        assertSame(flagship, WarshipEscort.autoLead(frigate, all));
        assertNull(WarshipEscort.autoLead(flagship, all), "the flagship follows nobody");
        assertNull(WarshipEscort.autoLead(enemyFrigate, all), "the other side's lone hull has no larger friend");
        assertNull(WarshipEscort.autoLead(destroyer, List.of(destroyer, frigate)),
                "a destroyer is the flagship of a side with only a frigate beside it, so it follows nobody");
        assertSame(destroyer, WarshipEscort.autoLead(frigate, List.of(destroyer, frigate)));
    }

    @Test
    void escortsScreenInLineBesideTheLeadOnTheThreatBeam() {
        // The lead at (50, 30) sailing north (bearing 90); the threat to the east (bearing 0).
        float[] only = WarshipEscort.station(50f, 30f, 90f, 20f, 0f, 0, 1, 10f);
        assertEquals(70f, only[0], 1e-3f);
        assertEquals(30f, only[1], 1e-3f);
        float[] west = WarshipEscort.station(50f, 30f, 90f, 20f, 170f, 0, 1, 10f);
        assertEquals(30f, west[0], 1e-3f, "the beam the threat is on");

        float[] first = WarshipEscort.station(50f, 30f, 90f, 20f, 30f, 0, 2, 10f);
        float[] second = WarshipEscort.station(50f, 30f, 90f, 20f, 30f, 1, 2, 10f);
        assertEquals(70f, first[0], 1e-3f, "one line parallel to the lead's course");
        assertEquals(70f, second[0], 1e-3f);
        assertEquals(25f, first[1], 1e-3f, "spaced along it, centred on the lead");
        assertEquals(35f, second[1], 1e-3f);
    }

    @Test
    void anArmedCraftAimedAtTheLeadCountsForTheEscortAtTwiceTheOrdinaryWeight() throws Exception {
        prime();
        SceneWorld w = world();
        BattleSimulation sim = w.sim();
        Warship lead = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 110f, 30f, 0f);
        Warship escort = Warship.fit("enforcer", "enforcer_Balanced", Faction.MARINE, 100f, 20f, 0f);
        List<Warship> both = List.of(lead, escort);
        long aimed = sim.spawnSortie(FighterProfile.DAGGER, Faction.DEFENDER, lead.centerX(), lead.centerY(),
                150f, 30f, 150f, 30f, 0f);
        sim.world().mission(aimed).strikeSortie = true;
        for (int i = 0; i < 80; i++) sim.advance(BattleSimulation.TICK_DT);

        AirThreatPicture alone = AirThreatPicture.take(sim, escort, both);
        assertEquals(0, alone.leadCraft(), "no lead, no lead craft");
        assertEquals(1, alone.armedCraft());

        escort.escort(lead);
        AirThreatPicture escorting = AirThreatPicture.take(sim, escort, both);
        assertEquals(1, escorting.leadCraft());
        assertEquals(0, escorting.armedCraft(), "a craft is counted once");
        assertEquals(AirThreatPicture.LEAD_CRAFT_WEIGHT, escorting.score(), 1e-4f);
        assertTrue(escorting.score() >= WarshipDoctrine.AIR_ENTER, "one craft at the lead turns the escort to the air fight");
        assertTrue(alone.score() < WarshipDoctrine.AIR_ENTER, "the same craft alone does not turn this destroyer: " + alone.score());
        assertTrue(escorting.describe().contains("at the lead"), escorting.describe());
        assertTrue(escorting.hasThreatAxis());
    }

    @Test
    void aCraftAimedAwayFromTheLeadIsOnlyTheEscortsOwnConcern() throws Exception {
        prime();
        SceneWorld w = world();
        BattleSimulation sim = w.sim();
        Warship lead = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 110f, 30f, 0f);
        Warship escort = Warship.fit("enforcer", "enforcer_Balanced", Faction.MARINE, 100f, 20f, 0f);
        escort.escort(lead);
        long elsewhere = sim.spawnSortie(FighterProfile.DAGGER, Faction.DEFENDER, 30f, 5f, 150f, 5f, 150f, 5f, 0f);
        sim.world().mission(elsewhere).strikeSortie = true;
        for (int i = 0; i < 80; i++) sim.advance(BattleSimulation.TICK_DT);

        AirThreatPicture picture = AirThreatPicture.take(sim, escort, List.of(lead, escort));
        assertEquals(0, picture.leadCraft());
        assertEquals(1, picture.armedCraft());
        assertFalse(picture.hasThreatAxis(), "nothing is coming for the lead");
    }

    @Test
    void theEscortKeepsStationAtTheLeadAndRevertsWhenTheLeadIsLost() throws Exception {
        prime();
        // Room round the lead for its whole screen ring, clear of every edge's margin.
        SceneWorld w = SceneBuilder.openGround(480, 320).missionCompletion(false)
                .unit("d", Faction.DEFENDER, UnitType.MARINE, 470, 160)
                .unit("m", Faction.MARINE, UnitType.MARINE, 10, 160)
                .build();
        BattleSimulation sim = w.sim();
        Warship lead = Warship.fit("onslaught", "onslaught_Standard", Faction.MARINE, 240f, 160f, 0f);
        lead.stationTo(240f, 160f);
        Warship escort = Warship.fit("enforcer", "enforcer_Balanced", Faction.MARINE, 150f, 140f, 0f);
        escort.stationTo(150f, 140f);
        sim.addWarship(lead);
        sim.addWarship(escort);
        escort.escort(lead);
        assertTrue(escort.hasDoctrine(), "an escort takes a doctrine so its posture can turn to the air fight");

        for (int i = 0; i < 120; i++) sim.advance(BattleSimulation.TICK_DT);
        float ring = WarshipEscort.ringRadius(lead, escort);
        assertEquals(ring, Math.hypot(escort.stationX() - lead.centerX(), escort.stationY() - lead.centerY()), 0.5f,
                "the station is on the lead's ring");
        assertTrue(escort.escorting());

        lead.takeDamage(1e9f, 1e9f);
        assertTrue(lead.destroyed());
        for (int i = 0; i < 120; i++) sim.advance(BattleSimulation.TICK_DT);
        assertFalse(escort.escorting());
        assertNull(escort.escortLead(), "a lost lead ends the relation");
        assertEquals(150f, escort.stationX(), 1e-3f, "the escort goes back to the station the mission gave");
        assertEquals(140f, escort.stationY(), 1e-3f);
    }
}
