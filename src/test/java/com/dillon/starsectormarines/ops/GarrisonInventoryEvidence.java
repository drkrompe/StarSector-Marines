package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage.Aperture;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.evacuation.CivilianRedoubt;
import com.dillon.starsectormarines.battle.fixture.BattleFixtureJson;
import com.dillon.starsectormarines.battle.fort.FortKind;
import com.dillon.starsectormarines.battle.nav.Direction;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.scene.LookGround;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.battle.world.model.GarrisonableRegistry;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.json.JSONObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Inventory of every place a squad could hold on each story mission's and each procedural mission's production map
 * at tick zero: per tactical
 * node, the windows, entrances and distinct stand cells the defense frontage finds at building and compound scope,
 * and the fighting-position runs on its ground; map-wide, the windows and runs that no node covers. Read-only
 * evidence about the maps, not about any battle.
 */
@Tag("garrison-evidence")
class GarrisonInventoryEvidence {

    /** How far outside a node's compound ground its prepared ground may stand: an apron stands off its door. */
    private static final int GROUND_MARGIN = 7;

    private record Case(String name, Supplier<BattleSimulation> build) {}

    @Test
    void inventoriesGarrisonablePlaces() throws Exception {
        Path root = Path.of(System.getProperty("user.dir"));
        DiskRegistries.installMapGeneration(root);
        TileRegistryTestInstaller.install(root);
        InstalledHullSpecs.install();
        InstalledVanillaData.install();

        List<ShuttleAssignment> lift = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        List<ShuttleAssignment> opening = List.of(new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 1, 6),
                new ShuttleAssignment(ShuttleType.AEROSHUTTLE, 2, 6));
        TargetProfile rock = new TargetProfile(0, 0, 0, 0, "pirates", Set.of(), SurfacePalette.ROCK, SettlementLink.ROAD);

        List<Case> cases = new ArrayList<>();
        for (DebugMissionCatalog.Scenario scenario : DebugMissionCatalog.Scenario.values()) {
            long seed = scenario.mission().battleSeed;
            List<ShuttleAssignment> manifest = scenario.sequence().startsWith("R") || scenario == DebugMissionCatalog.Scenario.RELIEF
                    || scenario == DebugMissionCatalog.Scenario.COUNTERATTACK ? opening : lift;
            cases.add(new Case(scenario.name(), () -> DebugMissionCatalog.build(scenario, seed, manifest)));
        }
        cases.add(new Case("WATER_UNDER_SEAL", () -> BattleSetup.createWaterUnderSeal(15301, lift, rock)));
        cases.add(new Case("LIMITED_WARRANT", () -> BattleSetup.createLimitedWarrant(15302, lift, rock, false)));
        cases.add(new Case("YARD_OWNERS", () -> BattleSetup.createYardOwners(15303, lift, rock)));
        // The procedural missions, on the maps a contract stands them up on.
        cases.add(new Case("RAID", () -> BattleSetup.createRaid(15311)));
        cases.add(new Case("ASSAULT", () -> BattleSetup.createPlaceholder(15312, lift)));
        cases.add(new Case("SABOTAGE", () -> BattleSetup.createSabotage(15313, lift, false)));
        cases.add(new Case("EXTRACTION", () -> BattleSetup.createExtraction(15314)));
        cases.add(new Case("CIVILIAN_RESCUE", () -> BattleSetup.createCivilianRescue(15315, lift, false, RiskLevel.MEDIUM)));
        // The Colony Breach colony, on the fixture its commander evidence plays, so a redoubt here is that run's.
        cases.add(new Case("COLONY_BREACH", GarrisonInventoryEvidence::colonyBreach));
        // Each fort kind on the ground its look study stands it on, so a fort's buildings are tallied one by one.
        for (FortKind kind : FortKind.values()) cases.add(new Case("FORT_" + kind.name(), () -> fort(kind)));
        // -Dgarrison.cases=FORT_BATTERY_FORT,... surveys only those.
        String only = System.getProperty("garrison.cases", "");
        if (!only.isBlank()) cases.removeIf(c -> !List.of(only.split(",")).contains(c.name));

        StringBuilder out = new StringBuilder("# Garrisonable inventory\n\n");
        out.append("Per node: kind, garrison size, footprint; building scope W/E/S = windows / entrances / distinct stand cells; ")
                .append("compound scope likewise; FP = fighting-position runs/cells on the node's compound ground or within " + GROUND_MARGIN + " cells of it (an apron stands off its door).\n");
        out.append("Map: tagged window cells, edge-window barriers, and how many of each no node covers.\n\n");
        for (Case c : cases) {
            out.append("## ").append(c.name).append('\n');
            try (BattleSimulation sim = c.build.get()) {
                survey(sim, out);
            } catch (RuntimeException | Error e) {
                out.append("BUILD FAILED: ").append(e).append('\n');
            }
            out.append('\n');
        }
        Path dir = Path.of("build/reports/garrison");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("summary.md"), out.toString());
        System.out.println(out);
    }

    private static void survey(BattleSimulation sim, StringBuilder out) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        FightingPositionIndex positions = sim.fightingPositions();
        List<TacticalNode> nodes = sim.getTacticalMap() == null ? List.of() : sim.getTacticalMap().all();
        out.append("map ").append(grid.getWidth()).append('x').append(grid.getHeight())
                .append(", nodes ").append(nodes.size()).append('\n');
        redoubts(sim, nodes, out);
        fortBuildings(sim, out);

        for (TacticalNode node : nodes) {
            List<Aperture> structure = DefenseFrontage.forStructure(node, sim);
            boolean wider = node.compoundLeft() != node.left || node.compoundTop() != node.top
                    || node.compoundRight() != node.right || node.compoundBottom() != node.bottom;
            List<Aperture> compound = wider ? DefenseFrontage.forCompound(node, sim) : List.of();
            Set<Integer> runs = new HashSet<>();
            int runCells = 0;
            for (int y = Math.max(0, node.compoundTop() - GROUND_MARGIN); y <= Math.min(grid.getHeight() - 1, node.compoundBottom() + GROUND_MARGIN); y++) {
                for (int x = Math.max(0, node.compoundLeft() - GROUND_MARGIN); x <= Math.min(grid.getWidth() - 1, node.compoundRight() + GROUND_MARGIN); x++) {
                    int run = positions == null ? -1 : positions.runIdAt(x, y);
                    if (run >= 0) {
                        runs.add(run);
                        runCells++;
                    }
                }
            }
            out.append(String.format("- %-16s g%-2d %3dx%-3d bldg %s%s FP %d/%d%n", node.kind, node.garrisonSize,
                    node.right - node.left + 1, node.bottom - node.top + 1, counts(structure),
                    wider ? " cmpd " + counts(compound) : "", runs.size(), runCells));
        }

        int taggedWindows = 0, taggedUncovered = 0;
        for (int y = 0; y < grid.getHeight(); y++) {
            for (int x = 0; x < grid.getWidth(); x++) {
                if (!topology.isWindow(x, y)) continue;
                taggedWindows++;
                if (!covered(nodes, x, y)) taggedUncovered++;
            }
        }
        int edgeWindows = 0, edgeUncovered = 0;
        for (SharedEdgeBarrier barrier : grid.getEdgeBarriers()) {
            if (barrier.kind() != SharedEdgeBarrier.Kind.WINDOW) continue;
            edgeWindows++;
            if (!covered(nodes, barrier.cellX(), barrier.cellY())) edgeUncovered++;
        }
        int runCount = positions == null ? 0 : positions.runCount(), runsUncovered = 0;
        for (int r = 0; r < runCount; r++) {
            if (positions.runSize(r) > 0 && !covered(nodes, positions.runCellX(r, 0), positions.runCellY(r, 0))) runsUncovered++;
        }
        out.append(String.format("map windows: tagged %d (%d uncovered), edge %d (%d uncovered); FP runs %d (%d uncovered)%n",
                taggedWindows, taggedUncovered, edgeWindows, edgeUncovered, runCount, runsUncovered));
        GarrisonableRegistry places = sim.garrisonables();
        int posts = 0;
        for (int g = places.stretchCount(); g < places.count(); g++) posts += places.size(g);
        out.append(String.format("garrisonables: stretches %d, building envelopes %d (%d posts)%n",
                places.stretchCount(), places.count() - places.stretchCount(), posts));
    }

    /**
     * Each redoubt as a place to hold: its claimed ground, the held building's envelope, and what stands
     * on that ground to fight from — fighting-position cells, wall outside the held building and how much
     * of it a Brute can break, props, gun emplacements, and the garrison.
     */
    private static void redoubts(BattleSimulation sim, List<TacticalNode> nodes, StringBuilder out) {
        List<CivilianRedoubt> redoubts = sim.getCivilianRedoubts();
        if (redoubts.isEmpty()) return;
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        FightingPositionIndex positions = sim.fightingPositions();
        out.append("\n| redoubt | door | ground | bldg W/E/S | FP runs/cells | wall outside the building (light) | props | guns | garrison |\n")
                .append("|---|---|---|---|---|---|---|---|---|\n");
        for (CivilianRedoubt r : redoubts) {
            TacticalNode held = null;
            for (TacticalNode node : nodes) {
                if (r.contains(node.anchorX, node.anchorY, 0) && topology.getBuildingId(node.anchorX, node.anchorY) != 0) {
                    held = node;
                    break;
                }
            }
            Set<Integer> runs = new HashSet<>();
            int runCells = 0, freeWall = 0, lightWall = 0, props = 0;
            for (int y = Math.max(0, r.top() - 1); y <= Math.min(grid.getHeight() - 1, r.bottom() + 1); y++) {
                for (int x = Math.max(0, r.left() - 1); x <= Math.min(grid.getWidth() - 1, r.right() + 1); x++) {
                    int run = positions == null ? -1 : positions.runIdAt(x, y);
                    if (run >= 0) {
                        runs.add(run);
                        runCells++;
                    }
                    boolean building = held != null && x >= held.left && x <= held.right && y >= held.top && y <= held.bottom;
                    if (!building && topology.isWall(x, y) && !grid.isWalkable(x, y)) {
                        freeWall++;
                        int hp = grid.getWallHp(x, y);
                        if (hp > 0 && hp <= 120) lightWall++;
                    }
                }
            }
            for (Doodad d : sim.getDoodads()) {
                if (r.contains(d.cellX, d.cellY, 1)) props++;
            }
            int guns = 0;
            for (int i = 0, n = sim.liveUnitCount(); i < n; i++) {
                long unit = sim.liveUnitAt(i);
                if (sim.identity().faction(unit) != Faction.ALLY || !sim.identity().type(unit).isTurret()) continue;
                if (r.contains(sim.world().cellX(unit), sim.world().cellY(unit), 4)) guns++;
            }
            Squad garrison = r.garrisonSquadId() >= 0 ? sim.getSquad(r.garrisonSquadId()) : null;
            out.append(String.format("| %d%s | %d,%d | %dx%d | %s | %d/%d | %d (%d) | %d | %d | %s |%n",
                    r.number(), r.isMain() ? " (shelter)" : "", r.approachX(), r.approachY(),
                    r.right() - r.left() + 1, r.bottom() - r.top() + 1,
                    held == null ? "-" : counts(DefenseFrontage.forStructure(held, sim)),
                    runs.size(), runCells, freeWall, lightWall, props, guns,
                    garrison == null ? "hidden" : String.valueOf(garrison.originalSize)));
        }
        out.append('\n');
    }

    /** One fort of {@code kind} on a generated map, built as the fort look study builds it. */
    private static BattleSimulation fort(FortKind kind) {
        long seed = 9100L + kind.ordinal();
        TargetProfile profile = LookGround.of(SurfacePalette.ARID, seed).profile().withDeclaredDefences(
                new GroundDefences(0, false).withForts(kind, 1));
        MapResult map = new BspCityGenerator().generate(400, 240, seed, null, profile);
        return BattleSetup.buildMap(map, Collections.emptyList(), new ArrayList<>(map.defensePosts), seed).sim();
    }

    /**
     * Each fort building one by one: every connected run of floor of one room purpose inside a fortification lot, with
     * the windows cut through its ring, its doors and the wall cells of its ring that face open ground (its exterior).
     */
    private static void fortBuildings(BattleSimulation sim, StringBuilder out) {
        NavigationGrid grid = sim.getGrid();
        CellTopology topology = sim.getTopology();
        int w = grid.getWidth(), h = grid.getHeight();
        boolean[][] seen = new boolean[w][h];
        List<String> rows = new ArrayList<>();
        for (int y0 = 0; y0 < h; y0++) {
            for (int x0 = 0; x0 < w; x0++) {
                RoomPurpose purpose = topology.getRoomPurpose(x0, y0);
                if (seen[x0][y0] || topology.getWorksStyle(x0, y0) == 0 || !grid.isWalkable(x0, y0)
                        || grid.isDoorway(x0, y0) || purpose == null || purpose == RoomPurpose.GENERIC
                        || purpose == RoomPurpose.CORRIDOR) continue;
                Set<Long> room = new HashSet<>();
                List<int[]> open = new ArrayList<>(List.of(new int[]{x0, y0}));
                seen[x0][y0] = true;
                while (!open.isEmpty()) {
                    int[] c = open.remove(open.size() - 1);
                    room.add(key(c[0], c[1]));
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = c[0] + d[0], ny = c[1] + d[1];
                        if (!grid.inBounds(nx, ny) || seen[nx][ny] || !grid.isWalkable(nx, ny) || grid.isDoorway(nx, ny)
                                || topology.getRoomPurpose(nx, ny) != purpose
                                || !grid.isEdgePassable(c[0], c[1], direction(d))) continue;
                        seen[nx][ny] = true;
                        open.add(new int[]{nx, ny});
                    }
                }
                int windows = 0;
                for (SharedEdgeBarrier barrier : grid.getEdgeBarriers()) {
                    if (barrier.kind() == SharedEdgeBarrier.Kind.WINDOW
                            && (room.contains(key(barrier.structureCellX(), barrier.structureCellY()))
                            || touches(room, barrier.structureCellX(), barrier.structureCellY()))) windows++;
                }
                Set<Long> doors = new HashSet<>(), exterior = new HashSet<>();
                for (long cell : room) {
                    int x = (int) (cell >> 32), y = (int) cell;
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        int nx = x + d[0], ny = y + d[1];
                        if (!grid.inBounds(nx, ny)) continue;
                        if (grid.isDoorway(nx, ny)) doors.add(key(nx, ny));
                        int ox = nx + d[0], oy = ny + d[1];
                        if (topology.isWall(nx, ny) && grid.inBounds(ox, oy) && grid.isWalkable(ox, oy)
                                && !room.contains(key(ox, oy))) exterior.add(key(nx, ny));
                    }
                }
                rows.add(String.format("| %s | %d | %d | %d | %d |", purpose, room.size(), exterior.size(), doors.size(),
                        windows));
            }
        }
        if (rows.isEmpty()) return;
        out.append("\n| fort building | floor | exterior wall | doors | windows |\n|---|---|---|---|---|\n");
        for (String row : rows) out.append(row).append('\n');
        out.append('\n');
    }

    private static boolean touches(Set<Long> room, int x, int y) {
        return room.contains(key(x + 1, y)) || room.contains(key(x - 1, y)) || room.contains(key(x, y + 1))
                || room.contains(key(x, y - 1));
    }

    private static Direction direction(int[] d) {
        return d[0] > 0 ? Direction.E : d[0] < 0 ? Direction.W : d[1] > 0 ? Direction.N : Direction.S;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /** The colony the Colony Breach commander evidence plays: its fixture, built as that harness builds it. */
    private static BattleSimulation colonyBreach() {
        try (InputStream stream = GarrisonInventoryEvidence.class.getResourceAsStream("/battle-fixtures/colony-breach-v1.json")) {
            if (stream == null) throw new IllegalStateException("missing colony-breach-v1.json");
            return BattleFixtureJson.fromJson(new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8))).build();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean covered(List<TacticalNode> nodes, int x, int y) {
        for (TacticalNode node : nodes) {
            if (x >= node.compoundLeft() - GROUND_MARGIN && x <= node.compoundRight() + GROUND_MARGIN
                    && y >= node.compoundTop() - GROUND_MARGIN && y <= node.compoundBottom() + GROUND_MARGIN) return true;
        }
        return false;
    }

    private static String counts(List<Aperture> apertures) {
        int windows = 0, entrances = 0;
        Set<Long> stances = new HashSet<>();
        for (Aperture a : apertures) {
            if (a.kind() == DefenseFrontage.Kind.WINDOW) windows++;
            else entrances++;
            stances.add(((long) a.stanceX() << 32) | (a.stanceY() & 0xffffffffL));
        }
        return String.format("W%d/E%d/S%d", windows, entrances, stances.size());
    }
}
