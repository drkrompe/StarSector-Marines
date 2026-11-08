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
 * Look experiment for the city drop void style (`voids.md`): a sky port's roof deck, the crown of an industrial
 * megastructure stepping down in terraces to the hive city far below, everything off the deck a void. For each
 * depth: stills at play zoom on the deck's east pier, its north-west corner and out past its north-east corner,
 * one zoomed out, and a video panning from the deck out past that corner, so the city and the terraces can be
 * seen moving under the deck. Opt-in: a look review, not part of {@code all}.
 *
 * <p>{@code -Dsnapshot.skyPort.depth=400} picks the depth in metres, the terraces and the ground below scaled
 * with it; {@code -Dsnapshot.skyPort.eyes=1,2} the camera heights to compare, as multiples of the view's width.
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
    static final int PAN_FRAMES = 240;
    /** Where the presentation clock stands for the stills, so the traffic is caught mid-flight. */
    static final float REVIEW_CLOCK = 40f;

    /** The industrial hive sky port: a stepped pyramid of terraces down to the hive city (owner: direction C). */
    /** Vanilla hulls flying the bays and skyways; the last three, the freighters, stand parked on the terraces. */
    static final List<String> CRAFT = List.of(
            "graphics/ships/aeroshuttle/aeroshuttle_base.png", "graphics/ships/hermes/hermes_base.png",
            "graphics/ships/hound/hound_base.png", "graphics/ships/wayfarer/wayfarer.png",
            "graphics/ships/buffalo/buffalo_base.png", "graphics/ships/tarsus/tarsus_base.png",
            "graphics/ships/mule/mule_base.png");

    static VoidBackdrop industrial(float depth, float eye) {
        float f = depth / 400f;
        return new VoidBackdrop("graphics/battle/voids/industrial-city-floor.png", depth, Math.max(96f, depth),
                .42f, .41f, .40f,
                "graphics/battle/voids/industrial-facade.png", 32f,
                "graphics/battle/voids/industrial-terrace-roof.png", 40f,
                List.of(new VoidBackdrop.Tier(60f * f, 14f), new VoidBackdrop.Tier(130f * f, 34f),
                        new VoidBackdrop.Tier(220f * f, 60f), new VoidBackdrop.Tier(310f * f, 90f)), eye,
                CRAFT, .2f);
    }

    @Override public String id() { return "sky-port-void-look"; }

    @Override public String label() { return "Sky port roof deck over the city below (void look experiment)"; }

    @Override public boolean inAll() { return false; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        SceneRegistries.installArmoury(context.projectRoot());
        boolean video = FfmpegVideo.executable() != null;
        List<SnapshotArtifact> out = new ArrayList<>();
        float depth = Float.parseFloat(System.getProperty("snapshot.skyPort.depth", "400"));
        for (String eyeText : System.getProperty("snapshot.skyPort.eyes", "1").split(",")) {
            float eye = Float.parseFloat(eyeText.trim());
            String tag = String.format(Locale.ROOT, "industrial-%04dm-eye%.1f", Math.round(depth), eye);
            try (BattleSimulation sim = deck(industrial(depth, eye))) {
                out.add(shot(context, sim, tag + "-1-pier.png", PX1 - 6, (PY0 + PY1) / 2f, PLAY));
                out.add(shot(context, sim, tag + "-2-corner.png", DX0 + 14, DY0 + 6, PLAY));
                out.add(shot(context, sim, tag + "-3-wide.png", W / 2f, H / 2f, 150f));
                out.add(shot(context, sim, tag + "-5-off-corner.png", DX1 + 22f, DY0 - 14f, PLAY));
                if (video) out.add(pan(context, sim, tag + "-4-pan.mp4"));
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
                    topology.setSurface(x, y, topology.surfaceIndex(finish(x, y)));
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
        // Apron everywhere, crossed by lanes that ring the deck.
        if (inside(x, y, PX0 - 6, PY0, PX1, PY1)) return GroundKind.STRIPED;
        if (inside(x, y, OX + 30, OY + 20, OX + 54, OY + 34)) return GroundKind.STRIPED;
        if (x < DX0 + 4 || x > DX1 - 4 || y < DY0 + 4 || y > DY1 - 4) return GroundKind.STREET;
        if (x == OX + 28 || y == OY + 26) return GroundKind.STREET;
        return GroundKind.STRIPED;
    }

    /**
     * The deck's industrial finish: the sky port's own heavy plate (one picture laid across the cells by
     * position, grilles and hatches in it), freight lanes marked out in yellow, and
     * the pier and the central pad set out as landing pads.
     */
    private static String finish(int x, int y) {
        GroundKind kind = surface(x, y);
        if (kind == GroundKind.STREET) return "apron.marked";
        if (inside(x, y, PX0 - 6, PY0, PX1, PY1)) return pad(x, y, PX0 - 6, PY0, PX1, PY1);
        if (inside(x, y, OX + 30, OY + 20, OX + 54, OY + 34)) return pad(x, y, OX + 30, OY + 20, OX + 54, OY + 34);
        return "sky-port.deck-plate";
    }

    /** A landing pad: a hatched keep-clear rim around plain landing deck. */
    private static String pad(int x, int y, int x0, int y0, int x1, int y1) {
        boolean rim = x == x0 || x == x1 || y == y0 || y == y1;
        return rim ? "apron.keep-clear" : "sky-port.pad-plate";
    }

    /** A walled block of roof plant: closed walls with one door. */
    private static void block(NavigationGrid grid, CellTopology topology, int x0, int y0, int x1, int y1) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                topology.setGroundKind(x, y, GroundKind.INDOOR);
                topology.setSurface(x, y, topology.surfaceIndex("loading-bay.staging-deck"));
                if (x != x0 && x != x1 && y != y0 && y != y1) continue;
                grid.setWalkable(x, y, false);
                grid.setWallHp(x, y, 400);
                topology.setTag(x, y, CellTopology.Tag.WALL, true);
                topology.setSurface(x, y, topology.surfaceIndex("installation.dark-concrete-wall"));
            }
        }
        int doorX = (x0 + x1) / 2;
        grid.setWalkableFloor(doorX, y1);
        grid.setDoorway(doorX, y1, true);
        topology.setTag(doorX, y1, CellTopology.Tag.WALL, false);
        topology.setSurface(doorX, y1, topology.surfaceIndex("loading-bay.staging-deck"));
    }

    private static boolean inside(int x, int y, int x0, int y0, int x1, int y1) {
        return x >= x0 && x <= x1 && y >= y0 && y <= y1;
    }
}
