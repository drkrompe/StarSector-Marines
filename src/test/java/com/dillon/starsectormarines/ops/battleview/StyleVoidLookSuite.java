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
 * Look experiment for one natural void style (`voids.md`): a plain of the style's own ground split west to east by
 * its drop, crossed by a wide bridge of the ground's own stuff and a timber plank span, everything in the drop a void.
 * Stills at play zoom on each bridge and on a bend, one wide, and a pan along the drop. Opt-in: a look review, not
 * part of {@code all}. Each style is a subclass naming its ground, its drop and its property prefix:
 * {@code -Dsnapshot.<key>.depth} sets the floor's depth in metres, the ledges scaled with it,
 * {@code -Dsnapshot.<key>.eyes} the camera heights to compare, {@code -Dsnapshot.<key>.stillsOnly} skips the pan.
 */
abstract class StyleVoidLookSuite implements SnapshotSuite {

    static final int W = 200, H = 120;
    static final float PLAY = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS;
    static final int PAN_FRAMES = 240;
    /** The wide bridge's column and width in cells, and the plank span's. */
    static final int BRIDGE_X = 92, BRIDGE_W = 4, PLANK_X = 124, PLANK_W = 2;
    static final float REVIEW_CLOCK = 40f;

    /** The property prefix and file tag, {@code canyon} for {@code -Dsnapshot.canyon.depth}. */
    abstract String key();

    /** The floor's depth in metres when no property sets it. */
    abstract float defaultDepth();

    /** The drop, its floor {@code depth} metres down, seen from {@code eye} metres up. */
    abstract NaturalVoid drop(float depth, float eye);

    /** The plain's ground kind, and the surface its tiles and the wide bridge are laid in. */
    abstract GroundKind ground();

    abstract String surface();

    /** Half the drop's width at a column, in cells. */
    abstract float halfWidth(int x);

    /** The drop's middle line at a column. */
    float centre(int x) {
        return 60f + 11f * (float) Math.sin(x / 31f + .9f) + 3.5f * (float) Math.sin(x / 8f);
    }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        String p = "snapshot." + key() + ".";
        boolean video = FfmpegVideo.executable() != null && !Boolean.getBoolean(p + "stillsOnly");
        List<SnapshotArtifact> out = new ArrayList<>();
        float depth = Float.parseFloat(System.getProperty(p + "depth", Float.toString(defaultDepth())));
        for (String eyeText : System.getProperty(p + "eyes", "120").split(",")) {
            float eye = Float.parseFloat(eyeText.trim());
            String tag = String.format(Locale.ROOT, "%s-%03dm-eye%03dm", key(), Math.round(depth), Math.round(eye));
            try (BattleSimulation sim = plain(drop(depth, eye))) {
                out.add(shot(context, sim, tag + "-1-bridge.png", BRIDGE_X + 4f, centre(BRIDGE_X) + 3f, PLAY));
                out.add(shot(context, sim, tag + "-2-bend.png", 50f, centre(50) - 2f, PLAY));
                out.add(shot(context, sim, tag + "-3-plank.png", PLANK_X - 3f, centre(PLANK_X) + 2f, PLAY));
                out.add(shot(context, sim, tag + "-4-wide.png", W / 2f, H / 2f, W));
                if (video) out.add(pan(context, sim, tag + "-5-pan.mp4", 40f, centre(40), 160f, centre(160)));
            }
        }
        return out;
    }

    /** From one point to another and back, at play zoom. */
    private SnapshotArtifact pan(SnapshotContext context, BattleSimulation sim, String name,
                                 float x0, float y0, float x1, float y1) throws Exception {
        Path encoded = Files.createTempFile(key() + "-pan", ".mp4");
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

    /** The plain and its drop, a wide bridge and a plank span over it, a rifle squad by the wide bridge. */
    BattleSimulation plain(NaturalVoid look) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        int ground = topology.surfaceIndex(surface());
        int timber = topology.surfaceIndex("colony.timber");
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean inDrop = Math.abs(y + .5f - centre(x)) < halfWidth(x);
                boolean bridge = x >= BRIDGE_X && x < BRIDGE_X + BRIDGE_W;
                boolean plank = x >= PLANK_X && x < PLANK_X + PLANK_W;
                if (inDrop && !bridge && !plank) {
                    grid.setWalkable(x, y, false);
                    grid.setTag(x, y, CellTag.SEE_THROUGH, true);
                    grid.setTag(x, y, CellTag.NO_EDGE_COVER, true);
                    grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
                    topology.setGroundKind(x, y, GroundKind.VOID);
                    continue;
                }
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, ground());
                topology.setSurface(x, y, inDrop && plank ? timber : ground);
            }
        }
        topology.setNaturalVoid(look);
        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false);
        int sy = Math.round(centre(BRIDGE_X) + halfWidth(BRIDGE_X)) + 3;
        builder.squad("rifles").faction(Faction.MARINE).size(6).primary("weapon.field-rifle")
                .inFile(BRIDGE_X + 5, sy, 1, 0)
                .assigned(id -> ObjectiveAssignment.defendArea(id, BRIDGE_X + 7, sy))
                .done();
        return builder.build().sim();
    }
}
