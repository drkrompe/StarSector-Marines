package com.dillon.starsectormarines.battle.command.objective;

import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.sim.BattleView;
import com.dillon.starsectormarines.battle.unit.EntitySpec;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.unit.UnitType;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The zone graph is rebuilt when topology changes mid-battle, and a rebuild renumbers zones. An
 * objective holding the zone id it was set up with must still see a marine standing in its room.
 */
class ObjectiveZoneRenumberTest {
    private static final int SIZE = 16;
    private static final int TARGET_X = 12;
    private static final int TARGET_Y = 8;

    private final NavigationGrid grid = new NavigationGrid(SIZE, SIZE);
    private final ZoneGraph zones = new ZoneGraph(grid);
    private final UnitRosterService roster = new UnitRosterService(new UnitSpatialIndex(SIZE, SIZE), null);
    private long marine;
    private int frozenZone;

    private BattleView setUp() {
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if (x != 5) grid.setWalkableFloor(x, y);
        }
        zones.rebuild();
        frozenZone = zones.zoneIdAt(TARGET_X, TARGET_Y);
        roster.setNavigationGrid(grid);
        marine = roster.spawn(new EntitySpec("Marine", Faction.MARINE, UnitType.MARINE, TARGET_X, TARGET_Y)
                .squad(roster.mintSquad(Faction.MARINE, UnitType.MARINE)));
        return (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getZoneGraph" -> zones;
                    case "getSimTickIndex" -> 0;
                    case "world" -> roster.world();
                    case "identity" -> roster.identity();
                    case "movement" -> roster.movement();
                    case "liveUnitCount" -> 1;
                    case "liveUnitAt" -> marine;
                    default -> throw new AssertionError(method.getName());
                });
    }

    /** Seals the west room: the target's room is the only one left and is renumbered. */
    private void renumber() {
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < 5; x++) {
            grid.setTag(x, y, CellTag.WALKABLE, false);
        }
        zones.rebuild();
        assertNotEquals(frozenZone, zones.zoneIdAt(TARGET_X, TARGET_Y), "fixture must renumber the room");
    }

    @Test void raidServicesTargetAfterZoneRenumber() {
        BattleView sim = setUp();
        RaidObjective raid = new RaidObjective("records", "Records", TARGET_X, TARGET_Y, frozenZone, 1, 1, .1f);
        renumber();
        for (int i = 0; i < 4; i++) raid.tick(sim);
        assertTrue(raid.targetSecured());
        assertEquals(zones.zoneIdAt(TARGET_X, TARGET_Y), raid.targetZoneId(sim));
    }

    @Test void archiveRecoversAfterZoneRenumber() {
        BattleView sim = setUp();
        ColonyArchiveObjective archive = new ColonyArchiveObjective(TARGET_X, TARGET_Y, frozenZone, .1f);
        renumber();
        for (int i = 0; i < 4; i++) archive.tick(sim);
        assertTrue(archive.isRecovered());
        assertEquals(zones.zoneIdAt(TARGET_X, TARGET_Y), archive.zoneId(sim));
    }
}
