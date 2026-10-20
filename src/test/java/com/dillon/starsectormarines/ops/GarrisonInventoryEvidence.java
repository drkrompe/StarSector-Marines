package com.dillon.starsectormarines.ops;

import com.dillon.starsectormarines.battle.air.ShuttleAssignment;
import com.dillon.starsectormarines.battle.air.ShuttleType;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage;
import com.dillon.starsectormarines.battle.decision.DefenseFrontage.Aperture;
import com.dillon.starsectormarines.battle.decision.TacticalNode;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.SharedEdgeBarrier;
import com.dillon.starsectormarines.battle.setup.BattleSetup;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.testsupport.DiskRegistries;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.testsupport.TileRegistryTestInstaller;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Inventory of every place a squad could hold on each story mission's production map at tick zero: per tactical
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
