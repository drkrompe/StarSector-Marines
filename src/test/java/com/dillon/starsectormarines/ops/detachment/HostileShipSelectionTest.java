package com.dillon.starsectormarines.ops.detachment;

import com.dillon.starsectormarines.battle.power.HostileShipSupport;
import com.dillon.starsectormarines.battle.warship.ShipSupportSource;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.fs.starfarer.api.campaign.FleetAssignment;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class HostileShipSelectionTest {

    private static final TargetProfile DEFENDED = world(HostileShipSupport.DEFENSE_LEVEL_THRESHOLD);

    private static TargetProfile world(int defence) {
        return new TargetProfile(6, 5, defence, 1, "hegemony", Set.<EconomicFunction>of(),
                SurfacePalette.ROCK, SettlementLink.ROAD);
    }

    private static HostileShipSelection.Candidate ship(String name, HullSize size, float weight) {
        return new HostileShipSelection.Candidate(
                new ShipSupportSource("hull_" + name, name, false, Map.of("WS0001", "gun")),
                "Hull " + name, size, weight);
    }

    private static HostileShipSelection.Fleet fleet(String name, HostileFleetStatus status,
                                                    HostileShipSelection.Candidate... ships) {
        return new HostileShipSelection.Fleet() {
            @Override public String name() { return name; }
            @Override public HostileFleetStatus status() { return status; }
            @Override public List<HostileShipSelection.Candidate> ships() { return List.of(ships); }
        };
    }

    @Test
    void aFreeFleetsLargestEligibleHullIsTheSource() {
        HostileShipReport r = HostileShipSelection.choose(DEFENDED, List.of(
                fleet("Patrol", HostileFleetStatus.AVAILABLE,
                        ship("Cruiser", HullSize.CRUISER, 30f), ship("Capital", HullSize.CAPITAL_SHIP, 20f))));
        assertEquals(HostileShipReport.Outcome.COMING, r.outcome());
        assertEquals("Capital", r.shipName());
        assertEquals("Hull Capital", r.hullName());
        assertEquals("Patrol", r.fleetName());
        assertEquals("hull_Capital", r.source().hullId());
        assertNull(r.source().memberId());
    }

    @Test
    void theLargestAcrossFleetsWinsAndWeightBreaksASizeTie() {
        HostileShipReport r = HostileShipSelection.choose(DEFENDED, List.of(
                fleet("A", HostileFleetStatus.AVAILABLE, ship("Small", HullSize.CAPITAL_SHIP, 20f)),
                fleet("B", HostileFleetStatus.AVAILABLE, ship("Big", HullSize.CAPITAL_SHIP, 45f))));
        assertEquals("Big", r.shipName());
        assertEquals("B", r.fleetName());
    }

    @Test
    void aBusyFleetsCapitalIsPassedOverForAFreeCruiser() {
        HostileShipReport r = HostileShipSelection.choose(DEFENDED, List.of(
                fleet("Engaged", HostileFleetStatus.IN_BATTLE, ship("Capital", HullSize.CAPITAL_SHIP, 60f)),
                fleet("Picket", HostileFleetStatus.AVAILABLE, ship("Cruiser", HullSize.CRUISER, 20f))));
        assertEquals(HostileShipReport.Outcome.COMING, r.outcome());
        assertEquals("Cruiser", r.shipName());
    }

    @Test
    void everyFleetBoggedDownMeansNoShipAndSaysWhy() {
        for (HostileFleetStatus status : HostileFleetStatus.values()) {
            if (status.free()) continue;
            HostileShipReport r = HostileShipSelection.choose(DEFENDED, List.of(
                    fleet("Home Fleet", status, ship("Capital", HullSize.CAPITAL_SHIP, 60f))));
            assertEquals(HostileShipReport.Outcome.HELD, r.outcome(), status.name());
            assertNull(r.sourceOrNull());
            assertEquals(status, r.held());
            assertEquals("Capital", r.shipName());
            assertEquals("Home Fleet", r.fleetName());
        }
    }

    @Test
    void theHeldReportNamesTheLargestHeldHull() {
        HostileShipReport r = HostileShipSelection.choose(DEFENDED, List.of(
                fleet("A", HostileFleetStatus.BUSY, ship("Cruiser", HullSize.CRUISER, 90f)),
                fleet("B", HostileFleetStatus.FLEEING, ship("Capital", HullSize.CAPITAL_SHIP, 10f))));
        assertEquals("Capital", r.shipName());
        assertEquals(HostileFleetStatus.FLEEING, r.held());
    }

    @Test
    void noFleetOrNoEligibleHullMeansAbsent() {
        assertSame(HostileShipReport.ABSENT, HostileShipSelection.choose(DEFENDED, List.of()));
        assertSame(HostileShipReport.ABSENT, HostileShipSelection.choose(DEFENDED, List.of(
                fleet("Frigates", HostileFleetStatus.AVAILABLE))));
        assertSame(HostileShipReport.ABSENT, HostileShipSelection.choose(DEFENDED, null));
    }

    @Test
    void aTenderIsNeverAHostileCapital() {
        HostileShipSelection.Candidate tender = new HostileShipSelection.Candidate(
                new ShipSupportSource(ShipSupportSource.TENDER_HULL_ID, "Shep", true, Map.of()),
                "Shepherd", HullSize.CRUISER, 5f);
        assertSame(HostileShipReport.ABSENT, HostileShipSelection.choose(DEFENDED, List.of(
                fleet("Support", HostileFleetStatus.AVAILABLE, tender))));
    }

    @Test
    void anUndefendedWorldFieldsNothingWhateverIsInTheSystem() {
        HostileShipReport r = HostileShipSelection.choose(world(HostileShipSupport.DEFENSE_LEVEL_THRESHOLD - 1),
                List.of(fleet("Patrol", HostileFleetStatus.AVAILABLE, ship("Capital", HullSize.CAPITAL_SHIP, 60f))));
        assertSame(HostileShipReport.UNDEFENDED, r);
    }

    @Test
    void aDeclarationIsTheSourceWithoutASearch() {
        ShipSupportSource declared = new ShipSupportSource("onslaught", "Script Anvil", false, Map.of("WS0001", "gun"));
        HostileShipReport r = HostileShipSelection.resolve(declared, false, world(0), List.of());
        assertEquals(HostileShipReport.Outcome.COMING, r.outcome());
        assertSame(declared, r.sourceOrNull());
        // Declared even on a campaign mission: the declaration wins over any fleet.
        HostileShipReport c = HostileShipSelection.resolve(declared, true, DEFENDED, List.of(
                fleet("Patrol", HostileFleetStatus.AVAILABLE, ship("Capital", HullSize.CAPITAL_SHIP, 60f))));
        assertSame(declared, c.sourceOrNull());
    }

    @Test
    void aNonCampaignMissionWithNoDeclarationSearchesNothing() {
        HostileShipReport r = HostileShipSelection.resolve(null, false, DEFENDED, List.of(
                fleet("Patrol", HostileFleetStatus.AVAILABLE, ship("Capital", HullSize.CAPITAL_SHIP, 60f))));
        assertSame(HostileShipReport.UNDEFENDED, r);
    }

    // ---- fleet status ----

    @Test
    void aBattleOutranksEveryOtherReading() {
        assertEquals(HostileFleetStatus.IN_BATTLE, HostileFleetStatus.classify(
                true, true, true, true, FleetAssignment.PATROL_SYSTEM, true));
    }

    @Test
    void despawningFleeingAndBusyAreEachBoggedDown() {
        assertEquals(HostileFleetStatus.DESPAWNING, HostileFleetStatus.classify(
                false, true, false, false, null, false));
        assertEquals(HostileFleetStatus.DESPAWNING, HostileFleetStatus.classify(
                false, false, false, false, FleetAssignment.GO_TO_LOCATION_AND_DESPAWN, false));
        assertEquals(HostileFleetStatus.FLEEING, HostileFleetStatus.classify(
                false, false, true, false, FleetAssignment.PATROL_SYSTEM, false));
        assertEquals(HostileFleetStatus.BUSY, HostileFleetStatus.classify(
                false, false, false, true, FleetAssignment.PATROL_SYSTEM, false));
    }

    @Test
    void anAssignmentThatTakesTheFleetAwayIsBusy() {
        for (FleetAssignment a : List.of(FleetAssignment.INTERCEPT, FleetAssignment.FOLLOW, FleetAssignment.RAID_SYSTEM,
                FleetAssignment.RESUPPLY, FleetAssignment.DELIVER_SUPPLIES)) {
            assertEquals(HostileFleetStatus.BUSY, HostileFleetStatus.classify(false, false, false, false, a, true), a.name());
        }
        assertEquals(HostileFleetStatus.BUSY, HostileFleetStatus.classify(
                false, false, false, false, FleetAssignment.GO_TO_LOCATION, false));
        assertEquals(HostileFleetStatus.BUSY, HostileFleetStatus.classify(
                false, false, false, false, FleetAssignment.ATTACK_LOCATION, false));
    }

    @Test
    void stationedPatrollingAndTargetBoundFleetsAreFree() {
        for (FleetAssignment a : List.of(FleetAssignment.PATROL_SYSTEM, FleetAssignment.DEFEND_LOCATION,
                FleetAssignment.ORBIT_AGGRESSIVE, FleetAssignment.ORBIT_PASSIVE, FleetAssignment.HOLD)) {
            assertEquals(HostileFleetStatus.AVAILABLE, HostileFleetStatus.classify(false, false, false, false, a, false), a.name());
        }
        assertEquals(HostileFleetStatus.AVAILABLE, HostileFleetStatus.classify(
                false, false, false, false, FleetAssignment.GO_TO_LOCATION, true));
        assertEquals(HostileFleetStatus.AVAILABLE, HostileFleetStatus.classify(
                false, false, false, false, FleetAssignment.ATTACK_LOCATION, true));
        assertEquals(HostileFleetStatus.AVAILABLE, HostileFleetStatus.classify(false, false, false, false, null, false));
    }
}
