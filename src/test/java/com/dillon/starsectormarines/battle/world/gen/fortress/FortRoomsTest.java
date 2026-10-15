package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.fort.FortAspect;
import com.dillon.starsectormarines.battle.world.gen.fit.ConsoleBankLayout;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPose;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.RoomLayoutCheck;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The authored rooms of every aspect, asked of the rooms themselves: each layout replays clean through the same guards as
 * any fitted room, the suite's rooms do not collide and open onto one another, the main room keeps the footprint its roof
 * art is registered to, and nothing is furnished onto the core's ground or onto a seat the garrison stands at.
 */
class FortRoomsTest {

    private static final List<FortPlan> PLANS = List.of(BatteryFort.PLAN, ShieldFort.PLAN);

    private static List<FortRoom> everyRoom() {
        List<FortRoom> rooms = new ArrayList<>();
        for (FortPlan plan : PLANS) {
            rooms.addAll(plan.rooms());
            rooms.add(plan.airDefence().room());
        }
        return rooms;
    }

    @Test
    void everyRoomsLayoutReplaysCleanThroughTheOrdinaryGuards() {
        for (FortRoom room : everyRoom()) {
            RoomLayoutCheck.Report report = RoomLayoutCheck.replay(room.layout());
            assertTrue(report.clean(), room.aspect() + " " + room.purpose() + " at " + room.x() + "," + room.y()
                    + ": " + report.complaints());
        }
    }

    @Test
    void everyLayoutSurvivesTheFourTurnsAFortIsStampedAt() {
        for (FortRoom room : everyRoom()) {
            List<RoomPose> good = room.layout().posesThatFit();
            for (int turns = 0; turns < 4; turns++) {
                assertTrue(good.contains(new RoomPose(turns, false)), room.aspect() + " " + room.purpose() + " turn " + turns);
            }
        }
    }

    @Test
    void roomsOfOneFortNeitherOverlapNorShareAFloorWithAnotherRoomsRing() {
        for (FortPlan plan : PLANS) {
            List<FortRoom> rooms = new ArrayList<>(plan.rooms());
            rooms.add(plan.airDefence().room());
            for (int i = 0; i < rooms.size(); i++) {
                for (int j = i + 1; j < rooms.size(); j++) {
                    int[] a = rooms.get(i).floor(), b = rooms.get(j).floor();
                    boolean apart = a[0] + a[2] + 1 <= b[0] || b[0] + b[2] + 1 <= a[0]
                            || a[1] + a[3] + 1 <= b[1] || b[1] + b[3] + 1 <= a[1];
                    assertTrue(apart, plan.kind() + ": " + rooms.get(i).purpose() + " and " + rooms.get(j).purpose()
                            + " floors must be at least a wall apart");
                }
            }
        }
    }

    @Test
    void everyDoorIsInTheMiddleOfAWallNotAtACorner() {
        for (FortRoom room : everyRoom()) {
            assertFalse(room.doors().isEmpty(), room.purpose() + " has a way in");
            for (var door : room.doors()) {
                boolean inX = door.x() >= 0 && door.x() < room.width(), inY = door.y() >= 0 && door.y() < room.height();
                boolean onX = door.x() == -1 || door.x() == room.width(), onY = door.y() == -1 || door.y() == room.height();
                assertTrue((inX && onY) || (inY && onX), room.purpose() + " door " + door + " is in a wall, not a corner");
            }
        }
    }

    @Test
    void theRoomsOfEachAspectOpenOntoOneAnotherSoTheSuiteIsOnePlace() {
        for (FortPlan plan : PLANS) {
            for (FortAspect aspect : plan.aspects()) {
                List<FortRoom> suite = plan.rooms().stream().filter(r -> r.aspect() == aspect).toList();
                assertEquals(1, suite.stream().filter(FortRoom::main).count(), aspect + " has exactly one main room");
                Set<FortRoom> reached = new HashSet<>();
                ArrayDeque<FortRoom> open = new ArrayDeque<>(List.of(suite.stream().filter(FortRoom::main).findFirst().orElseThrow()));
                while (!open.isEmpty()) {
                    FortRoom room = open.poll();
                    if (!reached.add(room)) continue;
                    for (FortRoom other : suite) if (!reached.contains(other) && opensInto(room, other)) open.add(other);
                }
                assertEquals(suite.size(), reached.size(), plan.kind() + " " + aspect + ": every room is reached through the suite's doors");
            }
        }
    }

    /** Whether a door of either room leads straight onto the other's floor, or the two rooms' doors share a wall. */
    private static boolean opensInto(FortRoom a, FortRoom b) {
        if (leadsOnto(a, b) || leadsOnto(b, a)) return true;
        for (int[] da : a.doorCells()) {
            for (int[] db : b.doorCells()) {
                if (Math.abs(da[0] - db[0]) + Math.abs(da[1] - db[1]) <= 1) return true;
            }
        }
        return false;
    }

    private static boolean leadsOnto(FortRoom from, FortRoom to) {
        int[] f = to.floor();
        for (int[] door : from.doorCells()) {
            for (int[] step : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = door[0] + step[0], y = door[1] + step[1];
                if (x >= f[0] && x < f[0] + f[2] && y >= f[1] && y < f[1] + f[3]) return true;
            }
        }
        return false;
    }

    @Test
    void theMainRoomKeepsTheFootprintItsRoofArtIsRegisteredTo() {
        assertArrayEquals(7, 5, BatteryFort.floor(FortAspect.FIRE_CONTROL));
        assertArrayEquals(8, 6, BatteryFort.floor(FortAspect.MAGAZINE));
        assertArrayEquals(11, 5, BatteryFort.floor(FortAspect.POWER));
        assertArrayEquals(7, 5, ShieldFort.floor(FortAspect.CONTROL_ROOM));
        assertArrayEquals(11, 5, ShieldFort.floor(FortAspect.POWER));
    }

    private static void assertArrayEquals(int width, int height, int[] floor) {
        assertEquals(width, floor[2]);
        assertEquals(height, floor[3]);
    }

    @Test
    void theCoreStandsInsideItsRoomAndNothingIsFurnishedOntoItsGround() {
        for (FortRoom room : everyRoom()) {
            if (!room.main()) continue;
            int[] core = room.coreCentre();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    assertTrue(inside(room, core[0] + dx, core[1] + dy), room.aspect() + " core is wholly on its floor");
                }
            }
            for (int[] cell : furnishedCells(room)) {
                assertFalse(Math.abs(room.x() + cell[0] - core[0]) <= 1 && Math.abs(room.y() + cell[1] - core[1]) <= 1,
                        room.aspect() + " furnishes the core's ground at " + cell[0] + "," + cell[1]);
            }
        }
    }

    @Test
    void theGarrisonStandsOnFloorThatIsNeitherFurnitureNorTheCoreAndSitsAtAStool() {
        for (FortRoom room : everyRoom()) {
            Set<String> furnished = new HashSet<>();
            for (int[] cell : furnishedCells(room)) furnished.add(cell[0] + "," + cell[1]);
            Set<String> stools = new HashSet<>();
            for (LayoutOp op : room.ops()) {
                if (op instanceof LayoutOp.Fixture f && f.doodadId().equals(FortRoomKit.STOOL)) stools.add(f.x() + "," + f.y());
            }
            for (int[] stand : room.stands()) {
                assertTrue(stand[0] >= 0 && stand[0] < room.width() && stand[1] >= 0 && stand[1] < room.height(), "on the floor");
                String key = stand[0] + "," + stand[1];
                assertTrue(stools.contains(key), room.aspect() + " " + room.purpose() + " crew at " + key + " has a stool");
                if (room.main()) {
                    int[] core = room.core();
                    assertFalse(Math.abs(stand[0] - core[0]) <= 1 && Math.abs(stand[1] - core[1]) <= 1, "not on the core");
                }
            }
        }
    }

    @Test
    void theAntiAirRoomIsAHardenedSingleDoorRoomWithItsConsoleAtTheBackAndACrew() {
        for (FortPlan plan : PLANS) {
            AirDefenceYard yard = plan.airDefence();
            FortRoom room = yard.room();
            assertEquals(1, room.doors().size());
            assertEquals(AirDefenceYard.CREW, room.stands().size());
            assertTrue(room.ops().stream().anyMatch(op -> op instanceof LayoutOp.ConsoleBank), "banks of plotters");
        }
    }

    /** Every cell a layout puts a fixture or console on, in the room's own frame. */
    /** A fixture whose doodad is not registered is silently refused and the room merely comes out sparser. */
    @Test
    void everyFixtureIsAShippedDoodad() throws Exception {
        TileRegistryTestInstaller.install(Path.of("").toAbsolutePath());
        TileRegistry registry = TileRegistry.installed();
        for (FortRoom room : everyRoom()) {
            for (LayoutOp op : room.ops()) {
                if (op instanceof LayoutOp.Fixture f) {
                    assertNotNull(registry.doodad(f.doodadId()), room.aspect() + " names " + f.doodadId());
                }
            }
        }
    }

    private static List<int[]> furnishedCells(FortRoom room) {
        List<int[]> cells = new ArrayList<>();
        for (LayoutOp op : room.ops()) {
            if (op instanceof LayoutOp.Fixture f) cells.add(new int[]{f.x(), f.y()});
            if (op instanceof LayoutOp.ConsoleBank bank) {
                for (ConsoleBankLayout.Cell c : bank.cells()) cells.add(new int[]{c.x(), c.y()});
            }
        }
        return cells;
    }

    private static boolean inside(FortRoom room, int x, int y) {
        return x >= room.x() && x < room.x() + room.width() && y >= room.y() && y < room.y() + room.height();
    }
}
