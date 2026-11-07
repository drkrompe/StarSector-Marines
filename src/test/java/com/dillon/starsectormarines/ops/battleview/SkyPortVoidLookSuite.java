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
import com.dillon.starsectormarines.battle.world.model.VoidBackdrop;
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
 * Look experiment for the city drop void style (`voids.md`): a sky port's roof deck standing over an endless
 * city, everything off the deck a void showing the city far below. For each plate and depth: stills at play
 * zoom on the deck's east pier and north-west corner and one zoomed out, and a video panning from the deck out
 * past its north-east corner, so the city can be seen moving under the deck. Opt-in: a look review, not part
 * of {@code all}.
 *
 * <p>{@code -Dsnapshot.skyPort.depths=150,400,1000} picks the depths in metres; the ground below is scaled
 * with its depth, so it keeps about the same size on screen and its city blocks stay city-sized.
 */
public final class SkyPortVoidLookSuite implements SnapshotSuite {

    static final int W = 220, H = 150;
    /** Where the deck stands in the map, leaving void all round to look out over. */
    static final int OX = 60, OY = 40;
    /** The main deck. */
    static final int DX0 = OX, DY0 = OY, DX1 = OX + 82, DY1 = OY + 54;
    /** A landing pier off the deck's east side. */
    static final int PX0 = DX1, PY0 = OY + 20, PX1 = DX1 + 20, PY1 = OY + 34;
    static final float PLAY = 60f;
    static final int PAN_FRAMES = 120;

    record Plate(String name, String path, float r, float g, float b, boolean lit) { }

    static final List<Plate> PLATES = List.of(
            new Plate("day", "graphics/battle/voids/city-below-day.png", .408f, .450f, .511f, false),
            new Plate("dusk", "graphics/battle/voids/city-below-dusk.png", .345f, .358f, .433f, true));

    @Override public String id() { return "sky-port-void-look"; }

    @Override public String label() { return "Sky port roof deck over the city below (void look experiment)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        boolean video = FfmpegVideo.executable() != null;
        List<SnapshotArtifact> out = new ArrayList<>();
        for (String depthText : System.getProperty("snapshot.skyPort.depths", "150,400,1000").split(",")) {
            float depth = Float.parseFloat(depthText.trim());
            for (Plate plate : PLATES) {
                String tag = String.format(Locale.ROOT, "%s-%04dm", plate.name(), Math.round(depth));
                VoidBackdrop backdrop = new VoidBackdrop(plate.path(), depth, Math.max(96f, depth),
                        plate.r(), plate.g(), plate.b(), plate.lit());
                try (BattleSimulation sim = deck(backdrop)) {
                    out.add(shot(context, sim, tag + "-1-pier.png", PX1 - 6, (PY0 + PY1) / 2f, PLAY));
                    out.add(shot(context, sim, tag + "-2-corner.png", DX0 + 14, DY0 + 6, PLAY));
                    out.add(shot(context, sim, tag + "-3-wide.png", W / 2f, H / 2f, 150f));
                    if (video) out.add(pan(context, sim, tag + "-4-pan.mp4"));
                }
            }
        }
        return out;
    }

    /** From the deck's middle out past its north-east corner and back, at play zoom. */
    private static SnapshotArtifact pan(SnapshotContext context, BattleSimulation sim, String name) throws Exception {
        Path encoded = Files.createTempFile("sky-port-pan", ".mp4");
        float x0 = (DX0 + DX1) / 2f, y0 = (DY0 + DY1) / 2f;
        float x1 = DX1 + 22f, y1 = DY0 - 14f;
        try (FfmpegVideo encoder = FfmpegVideo.open(encoded, 1920, 1080, "30")) {
            for (int i = 0; i < PAN_FRAMES; i++) {
                float t = i / (float) (PAN_FRAMES - 1);
                float u = .5f - .5f * (float) Math.cos(t * 2f * Math.PI);
                encoder.add(BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080,
                        x0 + (x1 - x0) * u, y0 + (y1 - y0) * u, PLAY).withoutRoofs().render(sim, name));
            }
            encoder.finish();
        }
        return SnapshotArtifact.video(name, encoded, List.of());
    }

    private static SnapshotArtifact shot(SnapshotContext context, BattleSimulation sim, String name,
                                         float x, float y, float cells) throws Exception {
        return new SnapshotArtifact(name, BattleReviewFrameRenderer.look(context.modRoot(), 1920, 1080, x, y, cells)
                .withoutRoofs().render(sim, name));
    }

    /** The roof deck: apron, lanes, a few blocks of plant, a squad for scale; the rest void. */
    static BattleSimulation deck(VoidBackdrop backdrop) {
        NavigationGrid grid = new NavigationGrid(W, H);
        CellTopology topology = new CellTopology(W, H);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                boolean onDeck = inside(x, y, DX0, DY0, DX1, DY1) || inside(x, y, PX0, PY0, PX1, PY1);
                if (onDeck) {
                    grid.setWalkableFloor(x, y);
                    topology.setGroundKind(x, y, surface(x, y));
                } else {
                    grid.setWalkable(x, y, false);
                    grid.setTag(x, y, CellTag.SEE_THROUGH, true);
                    grid.setTag(x, y, CellTag.NO_EDGE_COVER, true);
                    grid.setTag(x, y, CellTag.NATURAL_SOLID, true);
                    topology.setGroundKind(x, y, GroundKind.VOID);
                }
            }
        }
        block(grid, topology, OX + 10, OY + 8, OX + 22, OY + 18);
        block(grid, topology, OX + 10, OY + 34, OX + 26, OY + 46);
        block(grid, topology, OX + 58, OY + 6, OX + 74, OY + 14);
        block(grid, topology, OX + 34, OY + 40, OX + 42, OY + 50);
        topology.setVoidBackdrop(backdrop);
        SceneBuilder builder = SceneBuilder.over(grid, topology).missionCompletion(false);
        builder.squad("rifles").faction(Faction.MARINE).size(6).primary("weapon.field-rifle")
                .inFile(PX1 - 10, (PY0 + PY1) / 2, 1, 0)
                .assigned(id -> ObjectiveAssignment.defendArea(id, PX1 - 8, (PY0 + PY1) / 2))
                .done();
        return builder.build().sim();
    }

    private static GroundKind surface(int x, int y) {
        // The pier and a landing apron are striped; a lane rings the deck; the rest is panel.
        if (inside(x, y, PX0 - 6, PY0, PX1, PY1)) return GroundKind.STRIPED;
        if (inside(x, y, OX + 30, OY + 20, OX + 54, OY + 34)) return GroundKind.STRIPED;
        if (x < DX0 + 4 || x > DX1 - 4 || y < DY0 + 4 || y > DY1 - 4) return GroundKind.STREET;
        if (x == OX + 28 || y == OY + 26) return GroundKind.STREET;
        return GroundKind.TILE;
    }

    /** A walled block of roof plant: closed walls with one door. */
    private static void block(NavigationGrid grid, CellTopology topology, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                topology.setGroundKind(x, y, GroundKind.INDOOR);
                if (x != x0 && x != x1 && y != y0 && y != y1) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, 400);
                topology.setTag(x, y, CellTopology.Tag.WALL, true);
            }
        }
        int doorX = (x0 + x1) / 2;
        grid.setWalkableFloor(doorX, y1);
        grid.setDoorway(doorX, y1, true);
        topology.setTag(doorX, y1, CellTopology.Tag.WALL, false);
    }

    private static boolean inside(int x, int y, int x0, int y0, int x1, int y1) {
        return x >= x0 && x <= x1 && y >= y0 && y <= y1;
    }
}
