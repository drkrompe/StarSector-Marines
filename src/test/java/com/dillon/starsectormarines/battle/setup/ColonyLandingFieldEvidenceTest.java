package com.dillon.starsectormarines.battle.setup;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.evacuation.CivilianEvacuationPlacement;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.Paths;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.LandingArea;
import com.dillon.starsectormarines.battle.world.gen.LandingPad;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.precinct.FieldOfFire;
import com.dillon.starsectormarines.battle.world.gen.precinct.LandingKind;
import com.dillon.starsectormarines.battle.world.model.MapScale;
import com.dillon.starsectormarines.ops.RiskLevel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether the colony's open-field landing keeps its promise across seeds: the
 * disc clear of anything built, walled or tall, the berths on it, and what it
 * costs in walk and in flight.
 *
 * <p>Evidence rather than a unit test: every seed generates a 400x240 colony
 * and stands a whole rescue up on it, and the question is about the space the
 * generator produces, not about one function. It asserts the promise; the
 * walk, the flight and the re-roll count are readings, written to
 * {@code build/reports/colony-landing/summary.md}.
 */
@Tag("generated-space-evidence")
class ColonyLandingFieldEvidenceTest {

    private static final int SEEDS = Integer.getInteger("colony.landing.evidence.seeds", 12);
    private static final int ATTEMPTS = 8;
    /** The same arithmetic {@code createCivilianRescue} steps its re-rolls by. */
    private static final long ATTEMPT_STEP = 0x9E3779B97F4A7C15L;

    @Test
    void theFieldOfFireHoldsAcrossSeeds() throws Exception {
        StringBuilder md = new StringBuilder("# Colony landing field\n\n")
                .append("Field of fire promised: ").append(LandingKind.OPEN_FIELD.fieldOfFire())
                .append(" cells. One row per seed: the map the rescue would play, then the battle"
                        + " stood up on it.\n\n")
                .append("| seed | attempts | side | centre | clear | berth reach | inland | walk to shelter"
                        + " | shelter to lift | lift on field | to first building | town edge"
                        + " | vehicles off it | battle clear | technicals in it |\n")
                .append("|---:|---:|---|---|---:|---:|---:|---:|---:|---|---:|---:|---|---|---:|\n");
        List<String> failures = new ArrayList<>();
        BspCityGenerator generator = new BspCityGenerator();
        for (long seed = 1; seed <= SEEDS; seed++) {
            MapResult map = null;
            long battleSeed = seed;
            int attempt = 0;
            EnumSet<MapFeature> missing = EnumSet.noneOf(MapFeature.class);
            for (; attempt < ATTEMPTS; attempt++) {
                battleSeed = seed + attempt * ATTEMPT_STEP;
                MapResult candidate = generator.generate(MapScale.COLONY.width, MapScale.COLONY.height,
                        battleSeed, null, TargetProfile.NEUTRAL,
                        BattleSetup.colonyPlanFor(MapScale.COLONY, battleSeed));
                missing = MissionMapRequirements.missingFromColony(candidate);
                if (missing.isEmpty()) {
                    map = candidate;
                    break;
                }
            }
            if (map == null) {
                failures.add("seed " + seed + ": no colony in " + ATTEMPTS + " attempts, last missing " + missing);
                md.append("| ").append(seed).append(" | ").append(ATTEMPTS).append(" | failed: ")
                        .append(missing).append(" ||||||||||||\n");
                continue;
            }
            FieldOfFire field = map.fieldOfFire;
            NavigationGrid grid = map.grid;
            if (field == null) {
                // The control: no landing place, so read only what it costs.
                assertTrue(!BattleSetup.colonyLandsInFields(), "a colony map passed its requirements with no field");
                md.append(controlRow(seed, attempt + 1, map, battleSeed));
                continue;
            }
            double clear = field.clearRadius(grid, map.topology, map.doodads, field.radius() + 30);
            double reach = 0;
            int inland = Integer.MAX_VALUE;
            for (LandingArea area : map.landingAreas) {
                for (LandingPad berth : area.berths()) {
                    for (int[] corner : new int[][]{{berth.left(), berth.bottom()}, {berth.right(), berth.top()},
                            {berth.left(), berth.top()}, {berth.right(), berth.bottom()}}) {
                        reach = Math.max(reach, Math.hypot(corner[0] - field.centerX(), corner[1] - field.centerY()));
                    }
                    inland = Math.min(inland, inlandFrom(berth, grid));
                }
            }
            String side = field.centerY() > grid.getHeight() / 2 ? "north" : "south";

            CivilianEvacuationPlacement placement = CivilianEvacuationPlacement.find(
                    grid, map.topology, map.pointsOfInterest, battleSeed, 8, field);
            String walk = "-";
            String liftWalk = "-";
            String liftOn = "-";
            if (placement != null) {
                walk = walk(grid, map.marineSpawnX, map.marineSpawnY, placement.shelterX, placement.shelterY);
                liftWalk = walk(grid, placement.shelterX, placement.shelterY, placement.liftX, placement.liftY);
                boolean onField = field.contains(placement.liftX, placement.liftY);
                liftOn = onField ? "yes" : "no";
                if (!onField) {
                    failures.add("seed " + seed + ": the lift at " + placement.liftX + "," + placement.liftY
                            + " is off the field");
                }
            }
            // The country is the field's: the town begins past it.
            int townEdge = field.countryDepth(grid.getWidth(), grid.getHeight());
            int toBuilding = stepsToBuilding(map);

            // Parked vehicles are stamped after generation; they must stay off it.
            BattleSetup.stampVehicles(map, new Random(battleSeed));
            int[] afterVehicles = field.firstObstruction(grid, map.topology, map.doodads);
            if (afterVehicles != null) {
                failures.add("seed " + seed + ": a parked vehicle stands in the field at "
                        + afterVehicles[0] + "," + afterVehicles[1]);
            }

            String battleClear;
            int technicalsInside = 0;
            try (BattleSimulation sim = BattleSetup.createCivilianRescue(seed, manifest(), false,
                    RiskLevel.HIGH, 64, TargetProfile.NEUTRAL, false)) {
                int[] obstruction = field.firstObstruction(sim.getGrid(), sim.getTopology(), List.of());
                battleClear = obstruction == null ? "yes" : "no: " + obstruction[0] + "," + obstruction[1];
                if (obstruction != null) {
                    failures.add("seed " + seed + ": the battle has something standing in the field at "
                            + obstruction[0] + "," + obstruction[1]);
                }
                for (LandingPad pad : sim.getMarineLandingPads()) {
                    if (!field.contains(pad.centerX, pad.centerY)) {
                        failures.add("seed " + seed + ": a marine pad at " + pad.centerX + "," + pad.centerY
                                + " is off the field");
                    }
                }
                for (long id : sim.convoy().entityIds()) {
                    if (field.contains((int) sim.world().x(id), (int) sim.world().y(id))) technicalsInside++;
                }
            }

            md.append("| ").append(seed)
                    .append(" | ").append(attempt + 1)
                    .append(" | ").append(side)
                    .append(" | ").append(field.centerX()).append(',').append(field.centerY())
                    .append(" | ").append(String.format("%.1f", clear))
                    .append(" | ").append(String.format("%.1f", reach))
                    .append(" | ").append(inland)
                    .append(" | ").append(walk)
                    .append(" | ").append(liftWalk)
                    .append(" | ").append(liftOn)
                    .append(" | ").append(toBuilding)
                    .append(" | ").append(townEdge)
                    .append(" | ").append(afterVehicles == null ? "yes" : "no")
                    .append(" | ").append(battleClear)
                    .append(" | ").append(technicalsInside)
                    .append(" |\n");
            assertNull(field.firstObstruction(grid, map.topology, map.doodads));
        }
        Path out = Path.of("build/reports/colony-landing").toAbsolutePath().normalize();
        Files.createDirectories(out);
        Files.writeString(out.resolve("summary.md"), md.toString(), StandardCharsets.UTF_8);
        System.out.println(md);
        System.out.println("[colony-landing-evidence] report " + out.resolve("summary.md"));
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /** What the control's arrival costs: where it came down, its flight and its walk. */
    private static String controlRow(long seed, int attempts, MapResult map, long battleSeed) {
        int inland = Integer.MAX_VALUE;
        for (LandingPad pad : map.landingPads) inland = Math.min(inland, inlandFrom(pad, map.grid));
        CivilianEvacuationPlacement placement = CivilianEvacuationPlacement.find(
                map.grid, map.topology, map.pointsOfInterest, battleSeed, 8);
        String walk = "-";
        String liftWalk = "-";
        if (placement != null) {
            walk = walk(map.grid, map.marineSpawnX, map.marineSpawnY, placement.shelterX, placement.shelterY);
            liftWalk = walk(map.grid, placement.shelterX, placement.shelterY, placement.liftX, placement.liftY);
        }
        return "| " + seed + " | " + attempts + " | control | " + map.marineSpawnX + ',' + map.marineSpawnY
                + " | - | - | " + inland + " | " + walk + " | " + liftWalk + " | - | " + stepsToBuilding(map)
                + " | - | - | - | - |\n";
    }

    /** Cells walked between two cells, or why there is no walk. */
    private static String walk(NavigationGrid grid, int fromX, int fromY, int toX, int toY) {
        int[] path = GridPathfinder.findPath(grid, fromX, fromY, toX, toY);
        return Paths.isEmpty(path) ? "unreachable" : String.valueOf(Paths.cellCount(path));
    }

    /** Cells from the berth's centre to the map edge its approach comes across. */
    private static int inlandFrom(LandingPad berth, NavigationGrid grid) {
        return switch (berth.approach) {
            case SOUTH -> berth.centerY;
            case NORTH -> grid.getHeight() - 1 - berth.centerY;
            case WEST -> berth.centerX;
            case EAST -> grid.getWidth() - 1 - berth.centerX;
        };
    }

    /** Walking steps from the marine spawn to the first cell beside a building, or -1. */
    private static int stepsToBuilding(MapResult map) {
        NavigationGrid grid = map.grid;
        int width = grid.getWidth();
        int height = grid.getHeight();
        int[][] dist = new int[width][height];
        for (int[] column : dist) Arrays.fill(column, -1);
        Deque<int[]> queue = new ArrayDeque<>();
        dist[map.marineSpawnX][map.marineSpawnY] = 0;
        queue.add(new int[]{map.marineSpawnX, map.marineSpawnY});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            for (int[] step : steps) {
                int nx = at[0] + step[0];
                int ny = at[1] + step[1];
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                if (map.topology.getBuildingId(nx, ny) != 0) return dist[at[0]][at[1]] + 1;
                if (dist[nx][ny] >= 0 || !grid.isWalkable(nx, ny)) continue;
                dist[nx][ny] = dist[at[0]][at[1]] + 1;
                queue.add(new int[]{nx, ny});
            }
        }
        return -1;
    }

    private static List<ShuttleAssignment> manifest() {
        List<ShuttleAssignment> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) out.add(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1));
        return out;
    }
}
