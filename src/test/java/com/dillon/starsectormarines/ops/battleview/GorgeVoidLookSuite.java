package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.command.ObjectiveAssignment;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.NavigationGrid.CellTag;
import com.dillon.starsectormarines.battle.scene.SceneBuilder;
import com.dillon.starsectormarines.battle.scene.SceneRegistries;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.NaturalVoid;
import com.dillon.starsectormarines.tools.snapshot.FfmpegVideo;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Look experiment for the gorge river, the first natural void style (`voids.md`): a meadow cut west to east by
 * a river gorge, crossed by a timber footbridge and a stone road bridge, everything in the gorge a void. Stills
 * at play zoom on each bridge and on a bend, one wide, and a pan down the gorge, so its walls and ledges can be
 * seen moving against the meadow. Opt-in: a look review, not part of {@code all}.
 *
 * <p>{@code -Dsnapshot.gorge.depth=50} sets the floor's depth in metres, the ledges scaled with it; {@code
 * -Dsnapshot.gorge.eyes=120,160} (the default) the camera heights to compare.
 */
public final class GorgeVoidLookSuite implements SnapshotSuite {

    static final int W = 200, H = 120;
    static final float PLAY = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS;
    static final int PAN_FRAMES = 240;
    /** The footbridge's and the road bridge's columns, and their widths in cells. */
    static final int FOOT_X = 70, FOOT_W = 2, ROAD_X = 142, ROAD_W = 5;
    static final float REVIEW_CLOCK = 40f;

    /** The gorge river: a lip, two ledges and the water, 50 m down at the default depth. */
    static NaturalVoid gorge(float depth, float eye) {
        float f = depth / 50f;
        return new NaturalVoid(NaturalVoid.Style.GORGE_RIVER, List.of(
                new NaturalVoid.Step(16f * f, .6f),
                new NaturalVoid.Step(33f * f, 1.3f),
                new NaturalVoid.Step(50f * f, 1.9f)),
                eye, "graphics/battle/voids/gorge-wall.png", 14f, 7);
    }

    @Override public String id() { return "gorge-void-look"; }

    @Override public String label() { return "River gorge through a meadow (natural void look experiment)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        boolean video = FfmpegVideo.executable() != null;
        List<SnapshotArtifact> out = new ArrayList<>();
        float depth = Float.parseFloat(System.getProperty("snapshot.gorge.depth", "50"));
        for (String eyeText : System.getProperty("snapshot.gorge.eyes", "120,160").split(",")) {
            float eye = Float.parseFloat(eyeText.trim());
            String tag = String.format(Locale.ROOT, "gorge-%03dm-eye%03dm", Math.round(depth), Math.round(eye));
            try (BattleSimulation sim = meadow(gorge(depth, eye))) {
                out.add(shot(context, sim, tag + "-1-footbridge.png", FOOT_X + 6f, centre(FOOT_X) + 4f, PLAY));
                out.add(shot(context, sim, tag + "-2-road-bridge.png", ROAD_X, centre(ROAD_X) - 3f, PLAY));
                out.add(shot(context, sim, tag + "-3-bend.png", 108f, centre(108) + 2f, PLAY));
                out.add(shot(context, sim, tag + "-4-wide.png", W / 2f, H / 2f, W));
                if (video) out.add(pan(context, sim, tag + "-5-pan.mp4", 40f, centre(40), 170f, centre(170)));
            }
        }
        return out;
    }

    /** The gorge's middle line at a column. */
    static float centre(int x) {
        return 60f + 15f * (float) Math.sin(x / 31f) + 5f * (float) Math.sin(x / 11f + 1f);
    }

    /** Half the gorge's width at a column, in cells. */
    static float halfWidth(int x) {
        return 7.5f + 2.5f * (float) Math.sin(x / 23f + 2f) + (float) Math.sin(x / 7f);
    }

    /** From one point to another and back, at play zoom. */
    private static SnapshotArtifact pan(SnapshotContext context, BattleSimulation sim, String name,
                                        float x0, float y0, float x1, float y1) throws Exception {
        Path encoded = Files.createTempFile("gorge-pan", ".mp4");
        try (FfmpegVideo encoder = FfmpegVideo.open(encoded, 1920, 1080, "30")) {
            for (int i = 0; i < PAN_FRAMES; i++) {
                float t = i / (float) (PAN_FRAMES - 1);
                float u = .5f - .5f * (float) Math.cos(t * 2f * Math.PI);
                encoder.add(BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                        x0 + (x1 - x0) * u, y0 + (y1 - y0) * u, PLAY).withoutRoofs()
                        .atClock(REVIEW_CLOCK + i / 30f).render(sim, name));
            }
            encoder.finish();
        }
        return SnapshotArtifact.video(name, encoded, List.of());
    }

    private static SnapshotArtifact shot(SnapshotContext context, BattleSimulation sim, String name,
                                         float x, float y, float cells) throws Exception {
        return new SnapshotArtifact(name, BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, x, y, cells)
                .withoutRoofs().atClock(REVIEW_CLOCK).render(sim, name));
    }

    /** The meadow and its gorge, a road north to south over the road bridge, a rifle squad by the footbridge. */
    static BattleSimulation meadow(NaturalVoid look) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        int timber = topology.surfaceIndex("colony.timber");
        int stone = topology.surfaceIndex("floors.stone");
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean inGorge = Math.abs(y + .5f - centre(x)) < halfWidth(x);
                boolean foot = x >= FOOT_X && x < FOOT_X + FOOT_W;
                boolean roadCol = x >= ROAD_X - ROAD_W / 2 && x <= ROAD_X + ROAD_W / 2;
                if (inGorge && !foot && !roadCol) {
                    grid.setWalkable(x, y, false);
                    grid.setTag(x, y, CellTag.SEE_THROUGH, true);
                    grid.setTag(x, y, CellTag.NO_EDGE_COVER, true);
                    grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
                    topology.setGroundKind(x, y, GroundKind.VOID);
                    continue;
                }
                grid.setWalkableFloor(x, y);
                if (inGorge && foot) {
                    topology.setGroundKind(x, y, GroundKind.DIRT);
                    topology.setSurface(x, y, timber);
                } else if (inGorge) {
                    topology.setGroundKind(x, y, GroundKind.STONE);
                    topology.setSurface(x, y, stone);
                } else if (roadCol) {
                    topology.setGroundKind(x, y, GroundKind.STREET);
                } else {
                    topology.setGroundKind(x, y, (x * 7 + y * 13) % 97 < 6 ? GroundKind.DIRT : GroundKind.GRASS);
                }
            }
        }
        topology.setNaturalVoid(look);
        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false);
        int sy = Math.round(centre(FOOT_X) + halfWidth(FOOT_X)) + 3;
        builder.squad("rifles").faction(Faction.MARINE).size(6).primary("weapon.field-rifle")
                .inFile(FOOT_X + 4, sy, 1, 0)
                .assigned(id -> ObjectiveAssignment.defendArea(id, FOOT_X + 6, sy))
                .done();
        return builder.build().sim();
    }
}
