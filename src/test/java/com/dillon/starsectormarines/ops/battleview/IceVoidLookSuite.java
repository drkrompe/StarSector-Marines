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
 * Look experiment for the glacial crevasse, a natural void style (`voids.md`): a frozen world's snowfield split
 * west to east by a crevasse in the ice, crossed by a snow bridge and a timber plank span, everything in the crevasse
 * a void. Stills at play zoom on each bridge and on a bend, one wide, and a pan along the crevasse, so the ice walls,
 * the snow on the ledges, the mist and the blown snow can be seen moving. Opt-in: a look review, not part of
 * {@code all}.
 *
 * <p>{@code -Dsnapshot.ice.depth=36} sets the floor's depth in metres, the ledges scaled with it; {@code
 * -Dsnapshot.ice.eyes=120} the camera heights to compare; {@code -Dsnapshot.ice.stillsOnly} skips the pan.
 */
public final class IceVoidLookSuite implements SnapshotSuite {

    static final int W = 200, H = 120;
    static final float PLAY = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS;
    static final int PAN_FRAMES = 240;
    /** The snow bridge's column and width in cells, and the plank span's. */
    static final int SNOW_BRIDGE_X = 92, SNOW_BRIDGE_W = 4, PLANK_X = 124, PLANK_W = 2;
    static final float REVIEW_CLOCK = 40f;

    /** The crevasse: a cornice, two ledges of drifted snow and the ice floor, 36 m down at the default depth. */
    static NaturalVoid crevasse(float depth, float eye) {
        float f = depth / 36f;
        return new NaturalVoid(NaturalVoid.Style.ICE, List.of(
                new NaturalVoid.Step(11f * f, .5f),
                new NaturalVoid.Step(23f * f, 1.1f),
                new NaturalVoid.Step(36f * f, 1.6f)),
                eye, "graphics/battle/voids/ice-wall.png", 10f, 23);
    }

    @Override public String id() { return "ice-void-look"; }

    @Override public String label() { return "Glacial crevasse through a snowfield (natural void look experiment)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        boolean video = FfmpegVideo.executable() != null && !Boolean.getBoolean("snapshot.ice.stillsOnly");
        List<SnapshotArtifact> out = new ArrayList<>();
        float depth = Float.parseFloat(System.getProperty("snapshot.ice.depth", "36"));
        for (String eyeText : System.getProperty("snapshot.ice.eyes", "120").split(",")) {
            float eye = Float.parseFloat(eyeText.trim());
            String tag = String.format(Locale.ROOT, "ice-%03dm-eye%03dm", Math.round(depth), Math.round(eye));
            try (BattleSimulation sim = snowfield(crevasse(depth, eye))) {
                out.add(shot(context, sim, tag + "-1-snow-bridge.png", SNOW_BRIDGE_X + 4f,
                        centre(SNOW_BRIDGE_X) + 3f, PLAY));
                out.add(shot(context, sim, tag + "-2-bend.png", 50f, centre(50) - 2f, PLAY));
                out.add(shot(context, sim, tag + "-3-plank.png", PLANK_X - 3f, centre(PLANK_X) + 2f, PLAY));
                out.add(shot(context, sim, tag + "-4-wide.png", W / 2f, H / 2f, W));
                if (video) out.add(pan(context, sim, tag + "-5-pan.mp4", 40f, centre(40), 160f, centre(160)));
            }
        }
        return out;
    }

    /** The crevasse's middle line at a column. */
    static float centre(int x) {
        return 60f + 11f * (float) Math.sin(x / 31f + .9f) + 3.5f * (float) Math.sin(x / 8f);
    }

    /** Half the crevasse's width at a column, in cells: narrower than a gorge, pinching and opening. */
    static float halfWidth(int x) {
        return 6.5f + 2f * (float) Math.sin(x / 17f + 2f) + .9f * (float) Math.sin(x / 5f);
    }

    /** From one point to another and back, at play zoom. */
    private static SnapshotArtifact pan(SnapshotContext context, BattleSimulation sim, String name,
                                        float x0, float y0, float x1, float y1) throws Exception {
        Path encoded = Files.createTempFile("ice-pan", ".mp4");
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

    /** The snowfield and its crevasse, a snow bridge and a plank span over it, a rifle squad by the snow bridge. */
    static BattleSimulation snowfield(NaturalVoid look) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        int snow = topology.surfaceIndex("floors.snow");
        int timber = topology.surfaceIndex("colony.timber");
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean inCrevasse = Math.abs(y + .5f - centre(x)) < halfWidth(x);
                boolean snowBridge = x >= SNOW_BRIDGE_X && x < SNOW_BRIDGE_X + SNOW_BRIDGE_W;
                boolean plank = x >= PLANK_X && x < PLANK_X + PLANK_W;
                if (inCrevasse && !snowBridge && !plank) {
                    grid.setWalkable(x, y, false);
                    grid.setTag(x, y, CellTag.SEE_THROUGH, true);
                    grid.setTag(x, y, CellTag.NO_EDGE_COVER, true);
                    grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
                    topology.setGroundKind(x, y, GroundKind.VOID);
                    continue;
                }
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, GroundKind.SNOW);
                topology.setSurface(x, y, inCrevasse && plank ? timber : snow);
            }
        }
        topology.setNaturalVoid(look);
        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false);
        int sy = Math.round(centre(SNOW_BRIDGE_X) + halfWidth(SNOW_BRIDGE_X)) + 3;
        builder.squad("rifles").faction(Faction.MARINE).size(6).primary("weapon.field-rifle")
                .inFile(SNOW_BRIDGE_X + 5, sy, 1, 0)
                .assigned(id -> ObjectiveAssignment.defendArea(id, SNOW_BRIDGE_X + 7, sy))
                .done();
        return builder.build().sim();
    }
}
