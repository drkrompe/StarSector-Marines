package com.dillon.starsectormarines.battle.fort;

import com.dillon.starsectormarines.battle.air.engine.TurretSlotResolver;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.turret.DefensePost;
import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.GroundDefences;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspKeys;
import com.dillon.starsectormarines.battle.world.gen.bsp.BuildingFloodFill;
import com.dillon.starsectormarines.battle.world.gen.bsp.FortStage;
import com.dillon.starsectormarines.battle.world.gen.fortress.BatteryFort;
import com.dillon.starsectormarines.battle.world.gen.fortress.CompoundLayout.Placement;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortBelt;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortPlan;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.FightingPositionIndex;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Each fort kind's earthworks as the headless review frame draws them: the whole fort and a close view of the works,
 * from one production-stamped fort per kind with its anti-air yard, with the generator's doodads handed to the
 * simulation. 1920x1080 at game scale. It also prints the earthwork census (props by kind, and the fighting-position
 * runs split into belt and interior), which is the audit's measurement of what the generator lays.
 */
public final class FortEarthworksSnapshotSuite implements SnapshotSuite {

    private static final int WIDTH = 240;
    private static final int HEIGHT = 160;
    private static final int CX = 120;
    private static final int CY = 80;
    private static final int WIDTH_PX = 1920;
    private static final int HEIGHT_PX = 1080;

    @Override
    public String id() {
        return "fort-earthworks";
    }

    @Override
    public String label() {
        return "Fort earthworks (battery and shield fort), whole and close, with the generator's doodads";
    }

    private record Fort(NavigationGrid grid, CellTopology topology, FortStage.Placed placed, List<Doodad> doodads,
                        List<DefensePost> posts) {}

    private static Fort stamp(FortKind kind) {
        NavigationGrid grid = new NavigationGrid(WIDTH, HEIGHT);
        CellTopology topology = new CellTopology(WIDTH, HEIGHT);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.DIRT);
            }
        }
        GenContext ctx = new GenContext(grid, topology, new Random(7L), WIDTH, HEIGHT, 7L);
        ctx.put(BspKeys.TACTICAL_REGIONS, TacticalRegionMap.build(grid, topology, null));
        ctx.put(BspKeys.BUILDINGS, BuildingFloodFill.populate(topology, 7L));
        ctx.put(BspKeys.MARKET_PROFILE, TargetProfile.NEUTRAL.withDeclaredDefences(
                new GroundDefences(0, false).withForts(kind, 1)));
        FortStage stage = FortStage.pinned(List.of(new FortStage.Pin(kind, CX, CY, 0, CY, true)));
        stage.run(ctx);
        return new Fort(grid, topology, stage.placed().get(0), List.copyOf(ctx.doodads),
                List.copyOf(ctx.defensePosts));
    }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        InstalledHullSpecs.install();
        InstalledVanillaData.install();
        try {
            List<SnapshotArtifact> artifacts = new ArrayList<>();
            for (FortKind kind : new FortKind[]{FortKind.BATTERY_FORT, FortKind.SHIELD_FORT}) {
                Fort fort = stamp(kind);
                String name = kind == FortKind.BATTERY_FORT ? "battery" : "shield";
                String census = census(name, fort);
                BattleSimulation sim = SceneBuilder.over(fort.grid(), fort.topology()).posts(fort.posts())
                        .missionCompletion(false).build().sim();
                for (Doodad doodad : fort.doodads()) sim.addDoodad(doodad);
                FortPlan plan = fort.placed().plan();
                Placement at = fort.placed().placement();
                int[] whole = at.cell(plan.width() / 2, plan.height() / 2);
                BattleReviewFrameRenderer wholeView = new BattleReviewFrameRenderer(context.modRoot(), WIDTH_PX,
                        HEIGHT_PX, whole[0] + 0.5f, whole[1] + 0.5f, 128f);
                artifacts.add(new SnapshotArtifact(name + "-fort-whole.png", wholeView.render(sim, "earthworks / " + census)));
                BattleReviewFrameRenderer closeView = new BattleReviewFrameRenderer(context.modRoot(), WIDTH_PX,
                        HEIGHT_PX, whole[0] + 0.5f, whole[1] + 0.5f, 64f);
                artifacts.add(new SnapshotArtifact(name + "-fort-works.png", closeView.render(sim, "earthworks works / " + census)));
                if (kind == FortKind.BATTERY_FORT) {
                    int[] bay = BatteryFort.PLAN.suiteBounds(FortAspect.MAGAZINE);
                    int[] magazine = at.cell(bay[0] + bay[2] / 2, bay[1] + bay[3] / 2);
                    BattleReviewFrameRenderer magazineView = new BattleReviewFrameRenderer(context.modRoot(), WIDTH_PX,
                            HEIGHT_PX, magazine[0] + 0.5f, magazine[1] + 0.5f, 40f);
                    artifacts.add(new SnapshotArtifact(name + "-magazine.png", magazineView.render(sim, "magazine berm / " + census)));
                }
                for (FortBelt.Cell cell : plan.belt()) {
                    if (cell.role() != FortBelt.Role.NEST) continue;
                    int[] nest = at.cell(cell.x(), cell.y());
                    BattleReviewFrameRenderer nestView = new BattleReviewFrameRenderer(context.modRoot(), WIDTH_PX,
                            HEIGHT_PX, nest[0] + 0.5f, nest[1] + 0.5f, 40f);
                    artifacts.add(new SnapshotArtifact(name + "-belt-nest.png", nestView.render(sim, "belt nest / " + census)));
                    break;
                }
            }
            return artifacts;
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }

    private static String census(String name, Fort fort) {
        FortPlan plan = fort.placed().plan();
        Placement at = fort.placed().placement();
        Set<Long> works = new HashSet<>();
        for (int lx = 0; lx < plan.width(); lx++) {
            for (int ly = 0; ly < plan.height(); ly++) {
                if (!plan.isWorks(lx, ly)) continue;
                int[] cell = at.cell(lx, ly);
                works.add((long) cell[1] * WIDTH + cell[0]);
            }
        }
        long sandbag = fort.doodads().stream().filter(d -> d.definitionId() != null && d.definitionId().contains("sandbag")).count();
        long fence = fort.doodads().stream().filter(d -> d.definitionId() != null && d.definitionId().contains("fence")).count();
        long trench = fort.doodads().stream().filter(d -> d.definitionId() != null && d.definitionId().contains("trench")).count();
        long bagsInside = fort.doodads().stream().filter(d -> d.definitionId() != null && d.definitionId().contains("sandbag")
                && works.contains((long) d.cellY * WIDTH + d.cellX)).count();
        FightingPositionIndex index = FightingPositionIndex.build(WIDTH, HEIGHT, fort.doodads(), fort.grid()::isWalkable);
        int beltRuns = 0, beltCells = 0, innerRuns = 0, innerCells = 0;
        for (int r = 0; r < index.runCount(); r++) {
            int inside = 0;
            for (int i = 0; i < index.runSize(r); i++) {
                if (works.contains((long) index.runCellY(r, i) * WIDTH + index.runCellX(r, i))) inside++;
            }
            if (inside * 2 > index.runSize(r)) {
                innerRuns++;
                innerCells += index.runSize(r);
            } else {
                beltRuns++;
                beltCells += index.runSize(r);
            }
        }
        String line = name + ": doodads " + fort.doodads().size() + ", sandbag " + sandbag + " (" + bagsInside
                + " inside works), trench props " + trench + ", fence " + fence + "; fighting positions belt " + beltRuns
                + " runs/" + beltCells + " cells, interior " + innerRuns + " runs/" + innerCells + " cells";
        System.out.println("[fort-earthworks] " + line);
        return line;
    }
}
