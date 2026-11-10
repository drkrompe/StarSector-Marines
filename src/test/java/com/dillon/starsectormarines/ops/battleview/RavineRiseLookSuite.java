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
import com.dillon.starsectormarines.battle.world.model.NaturalRise;
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
 * Look experiment for the ravine wall, the first natural rise (`voids.md`): the battle on a ravine's floor,
 * cliffs standing up on both sides to wooded high ground, a side canyon opening north, and a rock pillar standing
 * in the floor. Stills at play zoom at the foot of a wall with a squad there, at the pillar and at the side
 * canyon's mouth, one wide, and a pan down the ravine, so the walls can be seen leaning out as the camera
 * passes. Opt-in: a look review, not part of {@code all}.
 *
 * <p>{@code -Dsnapshot.ravine.height=36} sets the top's height in metres, the ledges scaled with it; {@code
 * -Dsnapshot.ravine.eyes=120} the camera heights to compare; {@code -Dsnapshot.ravine.sheer=true} draws the first
 * pass's cliffs in place of the terraced bank.
 */
public final class RavineRiseLookSuite implements SnapshotSuite {

    static final int W = 200, H = 120;
    static final float PLAY = BattleReviewFrameRenderer.PLAY_ZOOM_CELLS;
    static final int PAN_FRAMES = 240;
    /** {@code -Dsnapshot.ravine.ice=true}: a crevasse's ice walls over a snow floor in place of the ravine's rock. */
    static final boolean ICE = Boolean.getBoolean("snapshot.ravine.ice");
    static final float REVIEW_CLOCK = 40f;
    /** The pillar's centre and radius, and the side canyon's column and half width, in cells. */
    static final int PILLAR_X = 62, PILLAR_R = 4, CANYON_X = 128, CANYON_HALF = 5;

    /**
     * The ravine's walls: a terraced bank to the top, 36 m up at the default height ({@link NaturalRise#bank}); with
     * {@code sheer} the first pass's cliffs instead, two ledges and the top, for comparison.
     */
    static NaturalRise walls(float height, float eye, boolean sheer) {
        float f = height / 36f;
        List<NaturalRise.Step> steps = sheer ? List.of(
                new NaturalRise.Step(11f * f, .5f),
                new NaturalRise.Step(23f * f, 1.05f),
                new NaturalRise.Step(36f * f, 1.6f)) : NaturalRise.bank(height);
        if (ICE) return new NaturalRise(NaturalRise.Style.ICE_WALL, steps, eye, "graphics/battle/voids/ice-wall.png",
                10f, 11);
        return new NaturalRise(NaturalRise.Style.RAVINE_WALL, steps, eye, "graphics/battle/voids/gorge-wall.png", 14f,
                11);
    }

    /**
     * The ravine's walls climbing and sinking along its length ({@code -Dsnapshot.ravine.vary=true}): low banks in the
     * west, as high as their steps by the pillar, and higher still past the side canyon.
     */
    static NaturalRise varying(NaturalRise look) {
        float f = look.topMeters();
        return look.withMarks(List.of(
                new NaturalRise.HeightMark(15f, centre(15), f * .25f),
                new NaturalRise.HeightMark(PILLAR_X, centre(PILLAR_X), f),
                new NaturalRise.HeightMark(175f, centre(175), f * 1.5f)));
    }

    @Override public String id() { return "ravine-rise-look"; }

    @Override public String label() { return "Ravine floor between cliffs (natural rise look experiment)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        boolean video = FfmpegVideo.executable() != null;
        List<SnapshotArtifact> out = new ArrayList<>();
        float height = Float.parseFloat(System.getProperty("snapshot.ravine.height", "36"));
        // The bank's width and terrace count for the owner's pick: "-Dsnapshot.ravine.banks=10x6,6x4,16x8" draws
        // each, cells back to the top by terraces, at the wall's foot and wide.
        String banks = System.getProperty("snapshot.ravine.banks", "");
        if (!banks.isBlank()) {
            for (String bank : banks.split(",")) {
                String[] parts = bank.trim().split("x");
                List<NaturalRise.Step> steps = NaturalRise.bank(height, Float.parseFloat(parts[0]),
                        Integer.parseInt(parts[1]));
                NaturalRise look = ICE
                        ? new NaturalRise(NaturalRise.Style.ICE_WALL, steps, 120f, "graphics/battle/voids/ice-wall.png", 10f, 11)
                        : new NaturalRise(NaturalRise.Style.RAVINE_WALL, steps, 120f, "graphics/battle/voids/gorge-wall.png",
                        14f, 11);
                String tag = String.format(Locale.ROOT, "ravine-%03dm%s-bank%s", Math.round(height), ICE ? "-ice" : "",
                        bank.trim());
                try (BattleSimulation sim = ravine(look)) {
                    out.add(shot(context, sim, tag + "-1-wall-foot.png", 40f, centre(40) - 3f, PLAY));
                    out.add(shot(context, sim, tag + "-4-wide.png", W / 2f, H / 2f, W));
                }
            }
            return out;
        }
        for (String eyeText : System.getProperty("snapshot.ravine.eyes", "120").split(",")) {
            float eye = Float.parseFloat(eyeText.trim());
            boolean vary = Boolean.getBoolean("snapshot.ravine.vary");
            boolean sheer = Boolean.getBoolean("snapshot.ravine.sheer");
            String tag = String.format(Locale.ROOT, "ravine-%03dm%s%s-eye%03dm", Math.round(height), vary ? "-vary" : "",
                    (sheer ? "-sheer" : "") + (ICE ? "-ice" : ""),
                    Math.round(eye));
            try (BattleSimulation sim = ravine(vary ? varying(walls(height, eye, sheer)) : walls(height, eye, sheer))) {
                out.add(shot(context, sim, tag + "-1-wall-foot.png", 40f, centre(40) - 3f, PLAY));
                out.add(shot(context, sim, tag + "-2-pillar.png", PILLAR_X, centre(PILLAR_X), PLAY));
                out.add(shot(context, sim, tag + "-3-side-canyon.png", CANYON_X, centre(CANYON_X) - 12f, PLAY));
                out.add(shot(context, sim, tag + "-4-wide.png", W / 2f, H / 2f, W));
                if (video) out.add(pan(context, sim, tag + "-5-pan.mp4", 30f, centre(30), 160f, centre(160)));
            }
        }
        return out;
    }

    /** The floor's middle line at a column. */
    static float centre(int x) {
        return 62f + 12f * (float) Math.sin(x / 34f) + 4f * (float) Math.sin(x / 13f + 2f);
    }

    /** Half the floor's width at a column, in cells. */
    static float halfWidth(int x) {
        return 9f + 3f * (float) Math.sin(x / 21f + 1f);
    }

    /** Whether a cell is on the ravine's floor: the main run, the side canyon, less the pillar. */
    static boolean floor(int x, int y) {
        float dx = x + .5f - PILLAR_X, dy = y + .5f - centre(PILLAR_X);
        if (dx * dx + dy * dy < PILLAR_R * PILLAR_R) return false;
        if (Math.abs(y + .5f - centre(x)) < halfWidth(x)) return true;
        // The side canyon: north from the main run, wandering a little, narrowing as it goes.
        float axis = CANYON_X + 4f * (float) Math.sin(y / 9f);
        float half = CANYON_HALF * (.6f + .4f * Math.min(1f, y / centre(CANYON_X)));
        return y < centre(CANYON_X) && y > 4 && Math.abs(x + .5f - axis) < half;
    }

    /** From one point to another and back, at play zoom. */
    private static SnapshotArtifact pan(SnapshotContext context, BattleSimulation sim, String name,
                                        float x0, float y0, float x1, float y1) throws Exception {
        Path encoded = Files.createTempFile("ravine-pan", ".mp4");
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

    /**
     * The ravine: its floor walkable dirt, everything else a rise (unwalkable, natural solid, and
     * blocking sight, as high ground does), a rifle squad at the foot of the north wall.
     */
    static BattleSimulation ravine(NaturalRise look) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (!floor(x, y)) {
                    grid.setWalkable(x, y, false);
                    grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
                    topology.setGroundKind(x, y, GroundKind.VOID);
                    continue;
                }
                grid.setWalkableFloor(x, y);
                topology.setGroundKind(x, y, ICE ? GroundKind.SNOW : GroundKind.DIRT);
            }
        }
        topology.setNaturalRise(look);
        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false);
        int sy = Math.round(centre(40) - halfWidth(40)) + 2;
        builder.squad("rifles").faction(Faction.MARINE).size(6).primary("weapon.field-rifle")
                .inFile(36, sy, 1, 0)
                .assigned(id -> ObjectiveAssignment.defendArea(id, 38, sy))
                .done();
        return builder.build().sim();
    }
}
