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
 * Look experiment for the lava rift, a natural void style (`voids.md`): a basalt plain cut west to east by a rift
 * whose floor is molten rock, crossed by a stone road bridge, everything in the rift a void. Stills at play zoom
 * on the bridge, on a bend and on a straight, one wide, and a pan along the rift, so the walls lit from below,
 * the drifting crust, the embers and the smoke can be seen moving. Opt-in: a look review, not part of {@code all}.
 *
 * <p>{@code -Dsnapshot.lava.depth=40} sets the melt's depth in metres, the ledges scaled with it; {@code
 * -Dsnapshot.lava.eyes=120} the camera heights to compare. {@code -Dsnapshot.lava.setup} picks the rift: {@code
 * rift} (the default, the 40 m rift), {@code river} (a shallow lava river, running, {@code -Dsnapshot.lava.riverDepth=4} metres down), or {@code
 * vary} (the rift deep at the west, shallowing to a channel past the bridge and running out shallow in the east).
 */
public final class LavaVoidLookSuite implements SnapshotSuite {

    static final int W = 200, H = 120;
    static final float PLAY = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS;
    static final int PAN_FRAMES = 240;
    /** The road bridge's column and width in cells. */
    static final int BRIDGE_X = 96, BRIDGE_W = 5;
    static final float REVIEW_CLOCK = 40f;

    /** The lava rift: a lip, two ledges of cooled crust and the melt, 40 m down at the default depth. */
    static NaturalVoid rift(float depth, float eye) {
        float f = depth / 40f;
        return new NaturalVoid(NaturalVoid.Style.LAVA, List.of(
                new NaturalVoid.Step(14f * f, .6f),
                new NaturalVoid.Step(27f * f, 1.2f),
                new NaturalVoid.Step(40f * f, 1.7f)),
                eye, "graphics/battle/voids/basalt-wall.png", 12f, 11);
    }

    /** A shallow lava river: a low bank of cooled crust and the running melt, {@code depth} metres down (4 by default). */
    static NaturalVoid river(float eye, float depth) {
        return new NaturalVoid(NaturalVoid.Style.LAVA, List.of(
                new NaturalVoid.Step(1.6f * depth / 4f, .45f),
                new NaturalVoid.Step(depth, .9f)),
                eye, "graphics/battle/voids/basalt-wall.png", 12f, 11);
    }

    /** The rift deepening and shallowing along its length: 40 m in the west, 12 m by the bridge, 4 m in the east. */
    static NaturalVoid varying(float eye) {
        return rift(40f, eye).withMarks(List.of(
                new NaturalVoid.DepthMark(20f, centre(20), 40f),
                new NaturalVoid.DepthMark(BRIDGE_X + 14f, centre(BRIDGE_X + 14), 12f),
                new NaturalVoid.DepthMark(185f, centre(185), 4f)));
    }

    @Override public String id() { return "lava-void-look"; }

    @Override public String label() { return "Lava rift through a basalt plain (natural void look experiment)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        boolean video = FfmpegVideo.executable() != null && !Boolean.getBoolean("snapshot.lava.stillsOnly");
        List<SnapshotArtifact> out = new ArrayList<>();
        float depth = Float.parseFloat(System.getProperty("snapshot.lava.depth", "40"));
        String setup = System.getProperty("snapshot.lava.setup", "rift");
        float riverDepth = Float.parseFloat(System.getProperty("snapshot.lava.riverDepth", "4"));
        for (String eyeText : System.getProperty("snapshot.lava.eyes", "120").split(",")) {
            float eye = Float.parseFloat(eyeText.trim());
            NaturalVoid look = switch (setup) {
                case "river" -> river(eye, riverDepth);
                case "vary" -> varying(eye);
                default -> rift(depth, eye);
            };
            String tag = switch (setup) {
                case "river" -> riverDepth == 4f ? String.format(Locale.ROOT, "lava-river-eye%03dm", Math.round(eye))
                        : String.format(Locale.ROOT, "lava-river-%03.1fm-eye%03dm", riverDepth, Math.round(eye));
                case "vary" -> String.format(Locale.ROOT, "lava-vary-eye%03dm", Math.round(eye));
                default -> String.format(Locale.ROOT, "lava-%03dm-eye%03dm", Math.round(depth), Math.round(eye));
            };
            try (BattleSimulation sim = plain(look)) {
                out.add(shot(context, sim, tag + "-1-bridge.png", BRIDGE_X + 4f, centre(BRIDGE_X) + 3f, PLAY));
                out.add(shot(context, sim, tag + "-2-bend.png", 52f, centre(52) - 2f, PLAY));
                out.add(shot(context, sim, tag + "-3-straight.png", 150f, centre(150) + 2f, PLAY));
                out.add(shot(context, sim, tag + "-4-wide.png", W / 2f, H / 2f, W));
                if (video) out.add(pan(context, sim, tag + "-5-pan.mp4", 40f, centre(40), 160f, centre(160)));
            }
        }
        return out;
    }

    /** The rift's middle line at a column. */
    static float centre(int x) {
        return 60f + 13f * (float) Math.sin(x / 29f + .6f) + 4f * (float) Math.sin(x / 9f);
    }

    /** Half the rift's width at a column, in cells. */
    static float halfWidth(int x) {
        return 8f + 2.5f * (float) Math.sin(x / 19f + 1f) + .8f * (float) Math.sin(x / 6f);
    }

    /** From one point to another and back, at play zoom. */
    private static SnapshotArtifact pan(SnapshotContext context, BattleSimulation sim, String name,
                                        float x0, float y0, float x1, float y1) throws Exception {
        Path encoded = Files.createTempFile("lava-pan", ".mp4");
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

    /** The basalt plain and its rift, a stone road north to south over the bridge, a rifle squad by the bridge. */
    static BattleSimulation plain(NaturalVoid look) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        int talus = topology.surfaceIndex("terrain.talus");
        int stone = topology.surfaceIndex("floors.stone");
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean inRift = Math.abs(y + .5f - centre(x)) < halfWidth(x);
                boolean roadCol = x >= BRIDGE_X - BRIDGE_W / 2 && x <= BRIDGE_X + BRIDGE_W / 2;
                if (inRift && !roadCol) {
                    grid.setWalkable(x, y, false);
                    grid.setTag(x, y, CellTag.SEE_THROUGH, true);
                    grid.setTag(x, y, CellTag.NO_EDGE_COVER, true);
                    grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
                    topology.setGroundKind(x, y, GroundKind.VOID);
                    continue;
                }
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.STONE);
                topology.setSurface(x, y, roadCol ? stone : talus);
            }
        }
        topology.setNaturalVoid(look);
        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false);
        int sy = Math.round(centre(BRIDGE_X) + halfWidth(BRIDGE_X)) + 3;
        builder.squad("rifles").faction(Faction.MARINE).size(6).primary("weapon.field-rifle")
                .inFile(BRIDGE_X + 4, sy, 1, 0)
                .assigned(id -> ObjectiveAssignment.defendArea(id, BRIDGE_X + 6, sy))
                .done();
        return builder.build().sim();
    }
}
