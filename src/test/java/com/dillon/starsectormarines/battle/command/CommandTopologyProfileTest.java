package com.dillon.starsectormarines.battle.command;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.zone.ZoneGraph;
import com.dillon.starsectormarines.battle.profile.TickInnerProfile;
import com.dillon.starsectormarines.battle.sim.BattleView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandTopologyProfileTest {
    private static final String CPU_PROPERTY = "battle.profile.commandTopologyCpu";
    private final TickInnerProfile previousProfile = TickInnerProfile.currentIfBound();
    private final String previousCpuProperty = System.getProperty(CPU_PROPERTY);

    @AfterEach
    void restoreProfile() {
        if (previousProfile == null) TickInnerProfile.releaseCurrentThread();
        else TickInnerProfile.setCurrent(previousProfile);
        if (previousCpuProperty == null) System.clearProperty(CPU_PROPERTY);
        else System.setProperty(CPU_PROPERTY, previousCpuProperty);
    }

    @Test
    void freezeRecordsDisjointStagesAndLogicalGeometryCounts() {
        Fixture fixture = new Fixture();
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        System.setProperty(CPU_PROPERTY, "false");

        CommandTopology topology = CommandTopology.freeze(fixture.sim);

        assertStages(profile, 1);
        assertEquals(45, profile.countOf(TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_MAP_CELLS));
        assertEquals(36, profile.countOf(TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_ZONE_CELLS));
        assertEquals(4, profile.countOf(TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_ZONES));
        assertEquals(0, profile.countOf(TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_CPU));
        assertGeometry(topology);
    }

    @Test
    void unboundProfilingPreservesFrozenGeometryAndDoesNotCreateAProfile() {
        Fixture fixture = new Fixture();
        TickInnerProfile.releaseCurrentThread();
        CommandTopology unprofiled = CommandTopology.freeze(fixture.sim);
        assertNull(TickInnerProfile.currentIfBound());
        TickInnerProfile.setCurrent(new TickInnerProfile());
        CommandTopology profiled = CommandTopology.freeze(fixture.sim);
        assertGeometry(unprofiled);
        assertGeometry(profiled);
        for (int zone = 0; zone < unprofiled.zones().size(); zone++) {
            assertArrayEquals(unprofiled.zone(zone).cells(), profiled.zone(zone).cells());
            assertEquals(unprofiled.zone(zone).adjacentZones(), profiled.zone(zone).adjacentZones());
        }

        int[] exposed = profiled.zone(profiled.zoneIdAt(1, 1)).cells();
        exposed[0] = -1;
        fixture.grid.setWalkable(1, 1, false);
        fixture.graph.rebuild();
        assertGeometry(profiled);
        assertTrue(profiled.zone(profiled.zoneIdAt(1, 1)).cells()[0] >= 0);
    }

    @Test
    void commanderCacheHitsAddNoFreezeWorkAndRevisionChangesStillRebuild() {
        Fixture fixture = new Fixture();
        CommanderService commander = new CommanderService();
        TickInnerProfile profile = new TickInnerProfile();
        TickInnerProfile.setCurrent(profile);
        CommandTopology first = commander.freezeTopology(fixture.sim);
        assertSame(first, commander.freezeTopology(fixture.sim));
        assertStages(profile, 1);

        fixture.grid.setWalkableFloor(6, 2);
        fixture.graph.rebuild();
        fixture.topologyRevision++;
        CommandTopology next = commander.freezeTopology(fixture.sim);
        assertNotSame(first, next);
        assertSame(next, commander.freezeTopology(fixture.sim));
        assertStages(profile, 2);
        assertEquals(90, profile.countOf(TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_MAP_CELLS));
        assertFalse(first.reachable(1, 2, 7, 2));
        assertTrue(next.reachable(1, 2, 7, 2));
    }

    private static void assertStages(TickInnerProfile profile, int count) {
        for (TickInnerProfile.Bucket bucket : new TickInnerProfile.Bucket[]{
                TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_GRID_COPY,
                TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_ZONE_COPY,
                TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_CELL_COMPONENTS,
                TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_ZONE_COMPONENTS,
                TickInnerProfile.Bucket.COMMANDER_TOPOLOGY_PUBLICATION}) {
            assertEquals(count, profile.countOf(bucket), bucket.name());
        }
    }

    private static void assertGeometry(CommandTopology topology) {
        assertEquals(9, topology.width());
        assertEquals(5, topology.height());
        assertEquals(4, topology.zones().size());
        assertTrue(topology.isWalkable(1, 1));
        assertFalse(topology.isWalkable(6, 2));
        assertTrue(topology.isDoorwayCell(2 * 9 + 3));
        assertTrue(topology.reachable(1, 2, 4, 2));
        assertFalse(topology.reachable(1, 2, 7, 2));
        assertTrue(topology.areZonesConnected(topology.zoneIdAt(1, 2), topology.zoneIdAt(4, 2)));
        assertFalse(topology.areZonesConnected(topology.zoneIdAt(1, 2), topology.zoneIdAt(7, 2)));
    }

    private static final class Fixture {
        final NavigationGrid grid = new NavigationGrid(9, 5);
        final ZoneGraph graph = new ZoneGraph(grid);
        final BattleView sim;
        long topologyRevision;

        Fixture() {
            for (int y = 0; y < 5; y++) {
                for (int x = 0; x < 9; x++) grid.setWalkableFloor(x, y);
                grid.setWalkable(3, y, false);
                grid.setWalkable(6, y, false);
            }
            grid.setWalkableFloor(3, 2);
            grid.setDoorway(3, 2, true);
            graph.rebuild();
            sim = (BattleView) Proxy.newProxyInstance(BattleView.class.getClassLoader(),
                    new Class<?>[]{BattleView.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getGrid" -> grid;
                        case "getZoneGraph" -> graph;
                        case "getNavigationGridRevision" -> grid.topologyRevision();
                        case "getNavigationTopologyRevision" -> topologyRevision;
                        default -> throw new AssertionError("Unexpected battle read: " + method.getName());
                    });
        }
    }
}
