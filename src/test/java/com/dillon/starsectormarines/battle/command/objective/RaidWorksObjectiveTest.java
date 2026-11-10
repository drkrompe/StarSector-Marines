package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import com.dillon.starsectormarines.battle.world.model.BuildingStanding;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A disrupting raid's works ({@code raid-mission.md}, Break): several machines, the way out open from the first; a
 * machine's hall brought down wrecks it ({@code raid-demolish-the-works.md}).
 */
class RaidWorksObjectiveTest {

    /** A charge of a tenth of a second: two ticks at the sim's step set one. */
    private static final float CHARGE = .1f;

    @Test
    void theWayOutOpensWithTheFirstMachineAndTheShareCountsWhatWasWrecked() {
        Fixture f = new Fixture();
        RaidObjective works = f.works();
        long marine = f.spawn(Faction.MARINE, 5, 5);

        for (int i = 0; i < 4; i++) works.tick(f.view);
        assertEquals(1, works.sitesDone());
        assertTrue(works.egressOpen());
        assertFalse(works.targetSecured());
        assertEquals(RaidObjective.Phase.EGRESS, works.phase());
        assertEquals(1f / 3f, works.share(), 1e-6);

        f.roster.world().setCellPos(marine, 20, 20);
        works.tick(f.view);
        assertTrue(works.isComplete());
        assertEquals(1f / 3f, works.share(), 1e-6);
    }

    @Test
    void theEgressDoesNothingBeforeAMachineIsWrecked() {
        Fixture f = new Fixture();
        RaidObjective works = f.works();
        f.spawn(Faction.MARINE, 20, 20);

        for (int i = 0; i < 10; i++) works.tick(f.view);
        assertFalse(works.isComplete());
        assertEquals(RaidObjective.Phase.APPROACH, works.phase());
    }

    @Test
    void machinesAreWorkedAtOnceAndEachKeepsItsOwnCharge() {
        Fixture f = new Fixture();
        RaidObjective works = f.works();
        f.spawn(Faction.MARINE, 5, 5);
        f.spawn(Faction.MARINE, 15, 5);

        for (int i = 0; i < 4; i++) works.tick(f.view);
        assertEquals(2, works.sitesDone());
        assertTrue(works.siteDone(0));
        assertTrue(works.siteDone(1));
        assertFalse(works.siteDone(2));
    }

    @Test
    void aDefenderAtTheMachineStopsTheChargeAndResetsIt() {
        Fixture f = new Fixture();
        RaidObjective works = RaidObjective.works("W", "the works", f.sites(), 20, 20, 1f);
        f.spawn(Faction.MARINE, 5, 5);
        works.tick(f.view);
        assertTrue(works.serviceProgress() > 0f);

        f.spawn(Faction.DEFENDER, 6, 5);
        works.tick(f.view);
        assertEquals(0f, works.serviceProgress());
        assertEquals(0, works.sitesDone());
    }

    @Test
    void everyMachineWreckedSecuresTheWorks() {
        Fixture f = new Fixture();
        RaidObjective works = f.works();
        f.spawn(Faction.MARINE, 5, 5);
        f.spawn(Faction.MARINE, 15, 5);
        f.spawn(Faction.MARINE, 5, 15);

        for (int i = 0; i < 4; i++) works.tick(f.view);
        assertTrue(works.targetSecured());
        assertEquals(1f, works.share());
        assertTrue(works.defenderAlarm().active());
    }

    @Test
    void bringingDownAMachinesHallWrecksItOnceWithNoChargeAndOpensTheWayOut() {
        Fixture f = new Fixture();
        // Machine 0 stands in a hall: a 3x3 floor at 4..6 walled round at 3 and 7, sixteen wall cells.
        for (int x = 3; x <= 7; x++) for (int y = 3; y <= 7; y++) {
            if (x == 3 || x == 7 || y == 3 || y == 7) f.topology.setWall(x, y, true);
        }
        int[] floorX = {4, 5, 6, 4, 5, 6, 4, 5, 6}, floorY = {4, 4, 4, 5, 5, 5, 6, 6, 6};
        List<int[]> hall = BuildingStanding.outline(floorX, floorY, f.topology::isWall, 24, 24);
        List<Integer> fallen = new ArrayList<>();
        RaidObjective works = f.works().withHalls(List.of(new RaidObjective.Hall(hall, List.of()),
                RaidObjective.Hall.NONE, RaidObjective.Hall.NONE), fallen::add);
        assertTrue(works.hasHall(0));
        assertFalse(works.hasHall(1));

        for (int i = 0; i < 7; i++) f.topology.setWall(hall.get(i)[0], hall.get(i)[1], false);
        works.tick(f.view);
        assertEquals(0, works.sitesDone(), "seven of sixteen down is not half");
        assertTrue(fallen.isEmpty());
        assertEquals(7f / 8f, works.hallProgress(0), 1e-6);
        assertEquals(7f / 8f, works.siteProgress(0), 1e-6, "the further way along is the hall");

        f.topology.setWall(hall.get(7)[0], hall.get(7)[1], false);
        works.tick(f.view);
        assertTrue(works.siteDone(0));
        works.tick(f.view);
        assertEquals(List.of(0), fallen, "the battle hears the hall fall once");
        assertTrue(works.egressOpen());
        assertTrue(works.defenderAlarm().active());
        assertEquals(0f, works.hallProgress(1));
        assertEquals(1f, works.siteProgress(0));
    }

    @Test
    void aSingleTargetRaidStillNeedsItsTargetBeforeTheWayOut() {
        Fixture f = new Fixture();
        RaidObjective raid = new RaidObjective("T", "target", 5, 5, f.zones.zoneIdAt(5, 5), 20, 20, CHARGE);
        assertFalse(raid.works());
        long marine = f.spawn(Faction.MARINE, 5, 5);

        for (int i = 0; i < 4; i++) raid.tick(f.view);
        assertTrue(raid.targetSecured());
        f.roster.world().setCellPos(marine, 20, 20);
        raid.tick(f.view);
        assertTrue(raid.isComplete());
        assertEquals("Seize target and extract", raid.displayName());
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(24, 24);
        final ZoneGraph zones = new ZoneGraph(grid);
        final CellTopology topology = new CellTopology(24, 24);
        final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(24, 24), null);
        final List<Long> units = new ArrayList<>();
        final BattleView view;

        Fixture() {
            for (int y = 0; y < 24; y++) for (int x = 0; x < 24; x++) grid.setWalkableFloor(x, y);
            zones.rebuild();
            roster.setNavigationGrid(grid);
            view = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                    new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getZoneGraph" -> zones;
                        case "getTopology" -> topology;
                        case "getSimTickIndex" -> 0;
                        case "world" -> roster.world();
                        case "identity" -> roster.identity();
                        case "movement" -> roster.movement();
                        case "liveUnitCount" -> units.size();
                        case "liveUnitAt" -> units.get((int) args[0]);
                        default -> throw new AssertionError(method.getName());
                    });
        }

        List<RaidObjective.Site> sites() {
            return List.of(new RaidObjective.Site(5, 5, zones.zoneIdAt(5, 5)),
                    new RaidObjective.Site(15, 5, zones.zoneIdAt(15, 5)),
                    new RaidObjective.Site(5, 15, zones.zoneIdAt(5, 15)));
        }

        RaidObjective works() {
            return RaidObjective.works("W", "the works", sites(), 20, 20, CHARGE);
        }

        long spawn(Faction faction, int x, int y) {
            int squadId = roster.mintSquad(faction, UnitType.MARINE);
            long id = roster.spawn(new EntitySpec("Unit", faction, UnitType.MARINE, x, y).squad(squadId));
            units.add(id);
            return id;
        }
    }
}
