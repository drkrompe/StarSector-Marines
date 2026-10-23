package com.dillon.starsectormarines.battle.squad;

import com.dillon.starsectormarines.battle.combat.ShotEvent;
import com.dillon.starsectormarines.battle.combat.ShotService;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.perception.NoiseEventBus;
import com.dillon.starsectormarines.battle.perception.NoiseKind;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Direct two-observer alert pass; no battle, scheduler, or world generation. */
class SquadAlertProfileTest {
    private final TickInnerProfile previousProfile = TickInnerProfile.currentIfBound();

    @AfterEach
    void restoreProfile() {
        if (previousProfile == null) TickInnerProfile.releaseCurrentThread();
        else TickInnerProfile.setCurrent(previousProfile);
    }

    @Test
    void recordsEveryStageOnceAndCountsActualCandidatePairsAndLosCalls() {
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        try (Fixture fixture = new Fixture()) {
            fixture.tick();
            fixture.assertState();
            for (TickInnerProfile.Bucket stage : new TickInnerProfile.Bucket[]{
                    TickInnerProfile.Bucket.ALERT_NOISE_DRAIN,
                    TickInnerProfile.Bucket.ALERT_BELIEF_RESET,
                    TickInnerProfile.Bucket.ALERT_AWARENESS,
                    TickInnerProfile.Bucket.ALERT_NOISE,
                    TickInnerProfile.Bucket.ALERT_INCOMING_FIRE,
                    TickInnerProfile.Bucket.ALERT_FINALIZE}) {
                assertEquals(1, profile.countOf(stage), stage.name());
                assertTrue(profile.nanosOf(stage) >= 0);
            }
            assertEquals(2, profile.countOf(TickInnerProfile.Bucket.ALERT_AWARENESS_CANDIDATE),
                    "each observer receives only the hostile combatant");
            assertEquals(2, profile.countOf(TickInnerProfile.Bucket.ALERT_AWARENESS_LOS));
            assertEquals(2, profile.countOf(TickInnerProfile.Bucket.ALERT_NOISE_CONSIDERED));
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.ALERT_NOISE_DETECTED));
            assertEquals(2, profile.countOf(TickInnerProfile.Bucket.ALERT_INCOMING_CANDIDATE));
            assertEquals(1, profile.countOf(TickInnerProfile.Bucket.ALERT_INCOMING_LOS));
            assertEquals(0, profile.nanosOf(TickInnerProfile.Bucket.ALERT_AWARENESS_CANDIDATE));
            assertEquals(0, profile.nanosOf(TickInnerProfile.Bucket.ALERT_INCOMING_LOS));
        }
    }

    @Test
    void unboundProfileLeavesAlertAndFireDecisionsUnchanged() {
        TickInnerProfile.releaseCurrentThread();
        try (Fixture fixture = new Fixture()) {
            fixture.tick();
            fixture.assertState();
        }
    }

    @Test
    void crowdedAwarenessKeepsInclusiveVisionAndKillZoneBoundaries() {
        try (CrowdedFixture f = new CrowdedFixture(true)) {
            for (int tick = 1; tick <= SquadAlertSystem.KILL_ZONE_LOS_TICKS_THRESHOLD; tick++) {
                f.alert.tick(1f / 30f, tick);
                assertTrue(f.observer.believedContact(f.atVisionBoundary) != null,
                        "cell-space range remains inclusive even when true positions need gather padding");
                assertEquals(null, f.observer.believedContact(f.outsideVisionBoundary));
                assertTrue(f.garrison._killZoneSightedThisTick,
                        "an ally garrison with zero awareness range still sees its eight-cell kill zone");
                assertTrue(f.garrison._underFireAtLosThisTick);
                assertFalse(f.garrison._engagedThisTick,
                        "kill-zone visibility alone does not grant awareness outside zero vision range");
            }
            assertEquals(SquadAlertSystem.KILL_ZONE_LOS_TICKS_THRESHOLD, f.garrison.killZoneLosTicks);
        }
    }

    @Test
    void killZoneGatherRejectsTheFirstPointOutsideItsInclusiveRadius() {
        try (CrowdedFixture f = new CrowdedFixture(false)) {
            f.alert.tick(1f / 30f, 1);
            assertFalse(f.garrison._killZoneSightedThisTick,
                    "the remaining enemy at offset (8,1) lies outside the eight-cell kill zone");
            assertEquals(0, f.garrison.killZoneLosTicks);
        }
    }

    /** Unsquadded bystanders are perception candidates, not extra observers. */
    private static final class CrowdedFixture implements AutoCloseable {
        final NavigationService navigation;
        final UnitRosterService roster;
        final Squad observer;
        final Squad garrison;
        final SquadAlertSystem alert;
        final long atVisionBoundary;
        final long outsideVisionBoundary;

        CrowdedFixture(boolean exactKillZoneTarget) {
            NavigationGrid grid = new NavigationGrid(24, 20);
            for (int y = 0; y < 20; y++) {
                for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
            }
            navigation = new NavigationService(grid, new CellTopology(24, 20));
            roster = new UnitRosterService(navigation.getUnitIndex(), null);
            navigation.setRoster(roster);
            roster.setNavigationGrid(grid);
            int observerId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
            int garrisonId = roster.mintSquad(Faction.ALLY, UnitType.MARINE);
            int defenderId = roster.mintSquad(Faction.DEFENDER, UnitType.MARINE);
            observer = roster.getSquad(observerId);
            garrison = roster.getSquad(garrisonId);
            garrison.holdsFireUntilKillZone = true;
            EntitySpec observerSpec = new EntitySpec("observer", Faction.MARINE, UnitType.MARINE, 10, 10)
                    .squad(observerId);
            observerSpec.spawnX = 10.01f;
            observerSpec.spawnY = 10.01f;
            long observerUnit = roster.spawn(observerSpec);
            long guard = roster.spawn(new EntitySpec("guard", Faction.ALLY, UnitType.MARINE, 2, 2)
                    .squad(garrisonId));
            long defender = roster.spawn(new EntitySpec("defender observer", Faction.DEFENDER,
                    UnitType.MARINE, 14, 10).squad(defenderId));
            roster.vision().setVisionRange(observerUnit, 5f);
            roster.vision().setVisionRange(guard, 0f);
            roster.vision().setVisionRange(defender, 5f);
            EntitySpec boundary = new EntitySpec("inclusive vision", Faction.DEFENDER, UnitType.MARINE, 13, 14);
            boundary.spawnX = 13.99f;
            boundary.spawnY = 14.99f;
            atVisionBoundary = roster.spawn(boundary);
            outsideVisionBoundary = roster.spawn(new EntitySpec("outside vision", Faction.DEFENDER,
                    UnitType.MARINE, 14, 14));
            if (exactKillZoneTarget) roster.spawn(new EntitySpec("inclusive kill zone", Faction.DEFENDER,
                    UnitType.MARINE, 10, 2));
            roster.spawn(new EntitySpec("outside kill zone", Faction.DEFENDER, UnitType.MARINE, 10, 3));
            for (int i = 0; i < 8; i++) {
                roster.spawn(new EntitySpec("friendly crowd " + i, Faction.MARINE, UnitType.MARINE, 10, 11));
                roster.spawn(new EntitySpec("allied crowd " + i, Faction.ALLY, UnitType.MARINE, 3, 2));
                roster.spawn(new EntitySpec("neutral crowd " + i, Faction.CIVILIAN, UnitType.CIVILIAN, 10, 10));
                roster.spawn(new EntitySpec("opposing noncombatant " + i, Faction.DEFENDER, UnitType.CIVILIAN, 10, 10));
            }
            navigation.getUnitIndex().rebuild(roster);
            NoiseEventBus noises = new NoiseEventBus(() -> 1);
            noises.post(2.5f, 2.5f, 100f, 0L, Faction.DEFENDER, NoiseKind.SHOT);
            ShotService shots = new ShotService();
            shots.postShot(new ShotEvent(10.5f, 2.5f, 2.5f, 2.5f, true, Faction.DEFENDER, 1f));
            alert = new SquadAlertSystem(navigation, roster, shots, noises);
        }

        @Override public void close() { navigation.close(); }
    }

    private static final class Fixture implements AutoCloseable {
        final NavigationService navigation;
        final UnitRosterService roster;
        final Squad marineSquad;
        final Squad defenderSquad;
        final NoiseEventBus noises = new NoiseEventBus(() -> 1);
        final ShotService shots = new ShotService();
        final SquadAlertSystem alert;

        Fixture() {
            NavigationGrid grid = new NavigationGrid(12, 6);
            for (int y = 0; y < 6; y++) {
                for (int x = 0; x < 12; x++) grid.setWalkableFloor(x, y);
            }
            navigation = new NavigationService(grid, new CellTopology(12, 6));
            roster = new UnitRosterService(navigation.getUnitIndex(), null);
            navigation.setRoster(roster);
            roster.setNavigationGrid(grid);
            int marineId = roster.mintSquad(Faction.MARINE, UnitType.MARINE);
            int defenderId = roster.mintSquad(Faction.DEFENDER, UnitType.MARINE);
            marineSquad = roster.getSquad(marineId);
            defenderSquad = roster.getSquad(defenderId);
            long marine = roster.spawn(new EntitySpec("marine", Faction.MARINE, UnitType.MARINE, 2, 2).squad(marineId));
            long defender = roster.spawn(new EntitySpec("defender", Faction.DEFENDER, UnitType.MARINE, 4, 2).squad(defenderId));
            roster.vision().setVisionRange(marine, 10f);
            roster.vision().setVisionRange(defender, 10f);
            navigation.getUnitIndex().rebuild(roster);
            float x = roster.world().x(marine);
            float y = roster.world().y(marine);
            noises.post(x, y, 100f, 0L, Faction.DEFENDER, NoiseKind.SHOT);
            shots.postShot(new ShotEvent(roster.world().x(defender), roster.world().y(defender),
                    x, y, true, Faction.DEFENDER, 1f));
            alert = new SquadAlertSystem(navigation, roster, shots, noises);
        }

        void tick() { alert.tick(1f / 30f, 1); }

        void assertState() {
            assertEquals(SquadAlertLevel.ENGAGED, marineSquad.alertLevel);
            assertEquals(SquadAlertLevel.ENGAGED, defenderSquad.alertLevel);
            assertTrue(marineSquad._underFireAtLosThisTick);
            assertFalse(defenderSquad._underFireAtLosThisTick);
            assertTrue(marineSquad._suspiciousThisTick);
            assertEquals(0, noises.pendingCount());
        }

        @Override public void close() { navigation.close(); }
    }
}
