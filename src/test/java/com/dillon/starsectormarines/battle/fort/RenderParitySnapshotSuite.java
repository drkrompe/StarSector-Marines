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
import com.dillon.starsectormarines.battle.world.gen.fortress.AirDefenceYard;
import com.dillon.starsectormarines.battle.world.gen.taxonomy.TacticalRegionMap;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.EmplacementWreck;
import com.dillon.starsectormarines.ops.battleview.BattleReviewFrameRenderer;
import com.dillon.starsectormarines.testsupport.InstalledHullSpecs;
import com.dillon.starsectormarines.testsupport.InstalledVanillaData;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * What the headless review frame draws of a fort's ground furniture, from one production-stamped battery fort with
 * its anti-air yard: the yard's gravel and two courses of sandbag, and the belt's trench, sandbag and industrial
 * fence. 1920x1080 at game scale, close views only (a whole-map frame is the wrong instrument for a prop one cell
 * across).
 *
 * <p>The fort scenes stand their world up from a bare grid and never hand the generator's doodads to the
 * simulation, so none of this furniture exists in their frames. This suite hands them over, which is what makes it
 * the check that the review drain draws earthworks at all.
 */
public final class RenderParitySnapshotSuite implements SnapshotSuite {

    private static final int WIDTH = 230;
    private static final int HEIGHT = 120;
    private static final int CX = 125;
    private static final int CY = 60;
    private static final int WIDTH_PX = 1920;
    private static final int HEIGHT_PX = 1080;

    @Override
    public String id() {
        return "render-parity";
    }

    @Override
    public String label() {
        return "Headless review renderer: fort earthworks (sandbag ring, belt fence) at game scale";
    }

    /** One stamped fort and every doodad the generator laid for it. */
    private record Fort(NavigationGrid grid, CellTopology topology, FortStage.Placed placed, List<Doodad> doodads,
                        List<DefensePost> posts) {}

    private static Fort stamp() {
        return stamp(FortKind.BATTERY_FORT, 0, CY);
    }

    private static Fort stamp(FortKind kind, int facingX, int facingY) {
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
        FortStage stage = FortStage.pinned(List.of(new FortStage.Pin(kind, CX, CY, facingX, facingY, true)));
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
            Fort fort = stamp();
            BattleSimulation sim = SceneBuilder.over(fort.grid(), fort.topology()).posts(fort.posts())
                    .missionCompletion(false).build().sim();
            for (Doodad doodad : fort.doodads()) sim.addDoodad(doodad);

            AirDefenceYard yard = fort.placed().plan().airDefence();
            int[] yardCentre = fort.placed().placement().cell(yard.centreX(), yard.centreY());
            Doodad fence = firstDoodad(fort.doodads(), "fence");
            Doodad sandbag = firstDoodad(fort.doodads(), "sandbag");
            String counts = fort.doodads().size() + " doodads (" + count(fort.doodads(), "sandbag") + " sandbag, "
                    + count(fort.doodads(), "fence") + " fence)";

            BattleReviewFrameRenderer yardView = new BattleReviewFrameRenderer(context.modRoot(), WIDTH_PX, HEIGHT_PX,
                    yardCentre[0] + 0.5f, yardCentre[1] + 0.5f, 56f);
            List<SnapshotArtifact> artifacts = new ArrayList<>();
            artifacts.add(new SnapshotArtifact("fort-yard-earthworks.png",
                    yardView.render(sim, "render-parity / anti-air yard / " + counts)));
            int[] worksCentre = fort.placed().placement().cell(fort.placed().plan().width() / 2, fort.placed().plan().height() / 2);
            BattleReviewFrameRenderer worksView = new BattleReviewFrameRenderer(context.modRoot(), WIDTH_PX, HEIGHT_PX,
                    worksCentre[0] + 0.5f, worksCentre[1] + 0.5f, 84f);
            artifacts.add(new SnapshotArtifact("fort-aa-placement.png",
                    worksView.render(sim, "render-parity / whole works: anti-air mounts on the bastions / " + counts)));
            Object[][] variants = {{"battery-north", FortKind.BATTERY_FORT, CX, 0}, {"battery-east", FortKind.BATTERY_FORT, WIDTH, CY},
                    {"shield-west", FortKind.SHIELD_FORT, 0, CY}, {"shield-south", FortKind.SHIELD_FORT, CX, HEIGHT}};
            for (Object[] v : variants) {
                Fort other = stamp((FortKind) v[1], (Integer) v[2], (Integer) v[3]);
                BattleSimulation otherSim = SceneBuilder.over(other.grid(), other.topology()).posts(other.posts())
                        .missionCompletion(false).build().sim();
                for (Doodad doodad : other.doodads()) otherSim.addDoodad(doodad);
                int[] c = other.placed().placement().cell(other.placed().plan().width() / 2, other.placed().plan().height() / 2);
                BattleReviewFrameRenderer view = new BattleReviewFrameRenderer(context.modRoot(), WIDTH_PX, HEIGHT_PX,
                        c[0] + 0.5f, c[1] + 0.5f, 120f);
                artifacts.add(new SnapshotArtifact("fort-works-" + v[0] + ".png",
                        view.render(otherSim, "render-parity / whole works / " + v[0])));
            }
            int[] nestCell = fort.placed().placement().cell(fort.placed().plan().nests().get(0)[0], fort.placed().plan().nests().get(0)[1]);
            BattleReviewFrameRenderer nestView = new BattleReviewFrameRenderer(context.modRoot(), WIDTH_PX, HEIGHT_PX,
                    nestCell[0] + 0.5f, nestCell[1] + 0.5f, 24f);
            artifacts.add(new SnapshotArtifact("fort-nest-close.png",
                    nestView.render(sim, "render-parity / bastion nest, 24 cells across / " + counts)));
            for (String state : new String[]{"damaged", "destroyed"}) {
                List<Doodad> shown = new ArrayList<>(fort.doodads());
                if (state.equals("damaged")) EmplacementWreck.damageAround(shown, nestCell[0], nestCell[1], 1);
                else EmplacementWreck.destroyAround(shown, nestCell[0], nestCell[1], 1);
                BattleSimulation stateSim = SceneBuilder.over(fort.grid(), fort.topology()).posts(fort.posts())
                        .missionCompletion(false).build().sim();
                for (Doodad doodad : shown) stateSim.addDoodad(doodad);
                artifacts.add(new SnapshotArtifact("fort-nest-" + state + ".png",
                        nestView.render(stateSim, "render-parity / bastion nest, " + state)));
            }
            if (fence != null) {
                BattleReviewFrameRenderer fenceView = new BattleReviewFrameRenderer(context.modRoot(), WIDTH_PX, HEIGHT_PX,
                        fence.cellX + 0.5f, fence.cellY + 0.5f, 56f);
                artifacts.add(new SnapshotArtifact("fort-belt-fence.png",
                        fenceView.render(sim, "render-parity / belt fence / " + counts)));
            }
            if (sandbag != null) {
                BattleReviewFrameRenderer bagView = new BattleReviewFrameRenderer(context.modRoot(), WIDTH_PX, HEIGHT_PX,
                        sandbag.cellX + 0.5f, sandbag.cellY + 0.5f, 28f);
                artifacts.add(new SnapshotArtifact("fort-sandbag-detail.png",
                        bagView.render(sim, "render-parity / sandbag line, 28 cells across / " + counts)));
            }
            return artifacts;
        } finally {
            TurretSlotResolver.useHullSpecs(null);
        }
    }

    private static Doodad firstDoodad(List<Doodad> doodads, String part) {
        for (Doodad doodad : doodads) {
            String id = doodad.definitionId();
            if (id != null && id.contains(part)) return doodad;
        }
        return null;
    }

    private static long count(List<Doodad> doodads, String part) {
        return doodads.stream().filter(d -> d.definitionId() != null && d.definitionId().contains(part)).count();
    }
}
