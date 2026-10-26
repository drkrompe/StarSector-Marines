package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.command.objective.BlindWatchChecklist;
import com.dillon.starsectormarines.battle.command.objective.ChargeSiteObjective;
import com.dillon.starsectormarines.battle.command.objective.MissionObjectiveSnapshot;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.objective.side.SideObjective;
import com.dillon.starsectormarines.battle.setup.BlindWatchSequence.Sites;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Area;
import com.dillon.starsectormarines.battle.setup.DustLedgerSequence.Cell;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress.Alarm;
import com.dillon.starsectormarines.battle.story.BlindWatchProgress.Warning;
import com.dillon.starsectormarines.battle.story.StoryBeatLog;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRole;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the adapter does when its rules decide, on a small open arena: the alarm, the warning and each charge's effect,
 * and the two side jobs inside their windows. Each step ticks the side objectives, then the sequence, then the charges,
 * as the battle does.
 */
class BlindWatchSequenceTest {
    private static final int W = 100, H = 20;
    /** A short warning, so the bar fills in a few dozen ticks. */
    private static final float WARNING = 1f;
    private static final Sites SITES = new Sites(Area.rect(0, 0, 3, 19), new Cell(90, 2), new Cell(95, 17),
            new Cell(85, 10), Area.rect(92, 8, 94, 10), List.of(new Cell(93, 9), new Cell(94, 9)),
            Area.rect(84, 12, 85, 13), List.of(new Cell(88, 4)), List.of(new Cell(70, 10), new Cell(71, 10), new Cell(72, 10)));

    @Test void theThreeSitesAreAuthoredWithStableIdsInMarkerOrder() {
        var sites = BlindWatchSequence.siteLayout(SITES).sites();
        assertEquals(List.of("SAB-01", "SAB-02", "SAB-03"), sites.stream().map(SabotageSiteLayout.Site::id).toList());
        assertEquals(List.of("blindWatchHudSiteMast", "blindWatchHudSiteUplink", "blindWatchHudSiteDishes"),
                sites.stream().map(SabotageSiteLayout.Site::nameKey).toList());
        assertEquals(90, sites.get(0).cellX());
        var one = sites.get(0);
        assertThrows(IllegalArgumentException.class, () -> SabotageSiteLayout.authored(List.of(one, sites.get(1))));
        assertThrows(IllegalArgumentException.class, () -> SabotageSiteLayout.authored(List.of(one, sites.get(1),
                new SabotageSiteLayout.Site("SAB-03", one.kind(), one.cellX(), one.cellY()))));
    }

    @Test void theMastSpotsForSableWakeUntilItsChargeBlows() {
        Arena a = new Arena(false);
        var watch = a.watch = a.sim.installBlindWatch(SITES, a.charges, BlindWatchSequence.riggerSpecs(2), false);
        assertTrue(watch.mastSees(a.sim.getGrid(), Faction.DEFENDER, 70.5f, 2.5f), "inside its reach, on open ground");
        assertFalse(watch.mastSees(a.sim.getGrid(), Faction.DEFENDER, 20.5f, 2.5f), "beyond its reach");
        assertFalse(watch.mastSees(a.sim.getGrid(), Faction.MARINE, 70.5f, 2.5f), "it watches for Sable Wake only");
        a.sim.getGrid().setWalkable(80, 2, false);
        a.sim.getGrid().setSeeThrough(80, 2, false);
        assertFalse(watch.mastSees(a.sim.getGrid(), Faction.DEFENDER, 70.5f, 2.5f), "not through a wall");

        a.plant(watch.mastCharge());
        assertFalse(watch.mastUp());
        assertFalse(watch.mastSees(a.sim.getGrid(), Faction.DEFENDER, 88.5f, 4.5f), "blown, it sees nothing");
        assertTrue(a.beats().contains("corvus.blind_watch.mast_down"));
    }

    @Test void theMastRaisesTheAlarmAndTheUplinkChargeCutsTheWarningOff() {
        Arena a = new Arena(true);
        long scout = a.sim.spawn(new EntitySpec("scout", Faction.MARINE, UnitType.MARINE, 5, 18));
        a.step();
        assertTrue(a.watch.progress().quiet(), "the landing ground is out of the mast's reach");
        assertEquals(MissionObjectiveSnapshot.State.ACTIVE, BlindWatchChecklist.items(a.watch).get(0).state());

        a.sim.world().setPos(scout, 60.5f, 2.5f);
        a.step();
        assertEquals(Alarm.MAST, a.watch.progress().alarm());
        assertEquals(Warning.SENDING, a.watch.progress().warning());
        for (int i = 0; i < 18; i++) a.step();
        assertTrue(a.watch.progress().sentShare() >= .5f);
        a.plant(a.watch.uplinkCharge());
        assertEquals(Warning.STOPPED, a.watch.progress().warning());
        var report = a.watch.report();
        assertFalse(report.garnirWarned());
        assertTrue(report.warningSent() > .5f && report.warningSent() < 1f, "the share sent is reported");
        assertTrue(a.beats().containsAll(List.of("corvus.blind_watch.alarm_mast", "corvus.blind_watch.warning_started",
                "corvus.blind_watch.warning_half", "corvus.blind_watch.uplink_down", "corvus.blind_watch.warning_stopped")),
                a.beats().toString());
        assertFalse(a.beats().contains("corvus.blind_watch.warning_sent"));
        assertEquals(MissionObjectiveSnapshot.State.COMPLETE, BlindWatchChecklist.items(a.watch).get(0).state());
    }

    @Test void aSentryShotOrSightingRaisesItAndAnUnbrokenWarningReachesGarnir() {
        Arena a = new Arena(true);
        a.sim.spawn(new EntitySpec("sentry", Faction.DEFENDER, UnitType.MARINE, 40, 10));
        a.sim.spawn(new EntitySpec("scout", Faction.MARINE, UnitType.MARINE, 45, 10));
        a.step();
        assertEquals(Alarm.SIGHTED, a.watch.progress().alarm());
        for (int i = 0; i < 40; i++) a.step();
        assertTrue(a.watch.report().garnirWarned());
        assertTrue(a.beats().containsAll(List.of("corvus.blind_watch.alarm_sighted", "corvus.blind_watch.warning_sent")));
        assertEquals(MissionObjectiveSnapshot.State.FAILED, BlindWatchChecklist.items(a.watch).get(0).state());
    }

    @Test void theRiggersWalkOutOnlyBeforeTheDishFieldBlows() {
        Arena a = new Arena(true);
        var job = a.sim.getSideObjectives().job(BlindWatchSideObjectives.RIGGERS);
        long rigger = a.watch.riggers().get(0);
        assertEquals(Faction.CIVILIAN, a.sim.identity().faction(rigger));
        long escort = a.sim.spawn(new EntitySpec("escort", Faction.MARINE, UnitType.MARINE, 93, 8));
        a.step();
        a.step();
        assertTrue(a.beats().contains("corvus.blind_watch.riggers_found"));
        assertEquals(SideObjective.State.WORKING, job.state(), "freed and with the company: on the move");
        float before = a.sim.world().x(rigger);
        for (int i = 0; i < 20; i++) a.step();
        assertTrue(a.sim.world().x(rigger) < before, "they make for the landing ground");

        a.sim.world().setPos(escort, 2.5f, 5.5f);
        a.sim.world().setPos(rigger, 1.5f, 5.5f);
        a.step();
        a.step();
        assertEquals(SideObjective.State.DONE, job.state());
        assertTrue(a.watch.revealed(), "they gave up the bunkers and the carrier's track");
        assertTrue(a.beats().containsAll(List.of("corvus.blind_watch.riggers_moving", "corvus.blind_watch.riggers_out")));
        assertTrue(a.watch.report().riggersFreed());

        Arena late = new Arena(true);
        late.plant(late.watch.dishCharge());
        late.step();
        late.step();
        assertEquals(SideObjective.State.LAPSED, late.sim.getSideObjectives().job(BlindWatchSideObjectives.RIGGERS).state());
        assertFalse(late.watch.revealed());
    }

    @Test void theFalseScheduleIsFoundAtTheHutAndStopsTheWarningCompleting() {
        // A warning long enough for the hold: the mast sees the leader at the hut and raises the alarm.
        Arena a = new Arena(60f);
        var job = a.sim.getSideObjectives().job(BlindWatchSideObjectives.SCHEDULE);
        assertFalse(job.known(), "hidden until found");
        int squad = a.sim.mintSquad(Faction.MARINE, UnitType.MARINE);
        long leader = a.sim.spawn(new EntitySpec("leader", Faction.MARINE, UnitType.MARINE, 84, 12).squad(squad));
        a.sim.getSquad(squad).leaderId = leader;
        a.step();
        assertTrue(job.known());
        assertEquals(Warning.SENDING, a.watch.progress().warning());
        int ticks = (int) Math.ceil(BlindWatchSideObjectives.SCHEDULE_SECONDS / BattleSimulation.TICK_DT) + 2;
        for (int i = 0; i < ticks; i++) a.step();
        assertEquals(SideObjective.State.DONE, job.state());
        assertEquals(Warning.FALSE_SCHEDULE, a.watch.progress().warning());
        assertFalse(a.watch.report().garnirWarned());
        assertTrue(a.watch.report().falseSchedule());
        assertTrue(a.beats().containsAll(List.of("corvus.blind_watch.schedule_found", "corvus.blind_watch.schedule_sent")));
    }

    @Test void aStoodToPostSaysSo() {
        Arena a = new Arena(false);
        var watch = new BlindWatchSequence(SITES, a.charges, a.sim.getStoryBeatLog(), new BlindWatchProgress(WARNING, true));
        watch.begin(a.sim, BlindWatchSequence.riggerSpecs(1));
        assertTrue(a.beats().contains("corvus.blind_watch.stood_to"));
        assertTrue(watch.report().stoodTo());
    }

    /** An open floor with the three charges installed and, if asked, the sequence on a short warning. */
    private static final class Arena {
        final BattleSimulation sim;
        final List<ChargeSiteObjective> charges = new ArrayList<>();
        BlindWatchSequence watch;

        Arena(boolean install) { this(install ? WARNING : 0f); }

        /** With a warning of {@code warning} seconds, or no sequence at zero. */
        Arena(float warning) {
            NavigationGrid grid = new NavigationGrid(W, H);
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) grid.setWalkableFloor(x, y);
            sim = new BattleSimulation(grid, new CellTopology(W, H));
            for (var site : BlindWatchSequence.siteLayout(SITES).sites()) {
                var charge = new ChargeSiteObjective(site.cellX(), site.cellY(), .1f, site.id(), site.displayName());
                charges.add(charge);
                sim.addObjective(charge);
            }
            if (warning <= 0f) return;
            watch = new BlindWatchSequence(SITES, charges, sim.getStoryBeatLog(), new BlindWatchProgress(warning, false));
            watch.begin(sim, BlindWatchSequence.riggerSpecs(2));
        }

        void step() {
            sim.getSideObjectives().tick(sim);
            if (watch != null) watch.advance(sim);
            for (var charge : charges) charge.tick(sim);
        }

        /** A planter on the site until its charge blows, then off the field. */
        void plant(ChargeSiteObjective charge) {
            long planter = sim.spawn(new EntitySpec("planter", Faction.MARINE, UnitType.MARINE, charge.cellX(), charge.cellY())
                    .role(UnitRole.PLANTER).assignedObjective(charge));
            for (int i = 0; i < 10 && !charge.isComplete(); i++) step();
            assertTrue(charge.isComplete());
            sim.takeOffTheField(planter);
            step();
        }

        List<String> beats() { return sim.getStoryBeats().stream().map(StoryBeatLog.Event::id).toList(); }
    }
}
