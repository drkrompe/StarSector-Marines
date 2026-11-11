package com.dillon.starsectormarines.ops.battleview;

import com.dillon.starsectormarines.battle.world.gen.TownNight;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.combat.fx.WallScars;
import com.dillon.starsectormarines.battle.sim.BattleSimulation;
import com.dillon.starsectormarines.battle.world.gen.Climate;
import com.dillon.starsectormarines.battle.world.gen.EconomicFunction;
import com.dillon.starsectormarines.battle.world.gen.MapResult;
import com.dillon.starsectormarines.battle.world.gen.SettlementLink;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.TargetProfile;
import com.dillon.starsectormarines.battle.world.gen.bsp.BspCityGenerator;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan;
import com.dillon.starsectormarines.battle.world.gen.precinct.PrecinctPlan.Sprawl;
import com.dillon.starsectormarines.battle.world.model.Building;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RoofDamage;
import com.dillon.starsectormarines.testsupport.HeadlessBattles;
import com.dillon.starsectormarines.testsupport.HeadlessStrings;
import com.dillon.starsectormarines.tools.snapshot.SnapshotArtifact;
import com.dillon.starsectormarines.tools.snapshot.SnapshotContext;
import com.dillon.starsectormarines.tools.snapshot.SnapshotSuite;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Building walls stood up, world by world ({@code voids.md}, buildings in depth; {@code BuildingFaces}): one defended
 * market's precinct town on a verdant, arid, frozen, toxic, irradiated and volcanic world, each framed on a street with
 * buildings round the frame's edges, where the walls stand tallest. Per world and frame: intact
 * ({@code <world>-<n>-intact.png}), the nearest building cut away to its rooms ({@code -cutaway.png}), then the
 * buildings in frame damaged by their own number, some scorched and holed, some with storeys fallen in, some razed
 * ({@code -damaged.png}); each also zoomed in, 40 cells across ({@code -close}). Look preset; nothing is played. Opt-in.
 *
 * <p>{@code -Dsnapshot.walls.seed=7} picks the map; {@code -Dsnapshot.walls.worlds=arid,toxic} narrows the worlds;
 * {@code -Dsnapshot.walls.frame=curtain} frames the joints where a diagonal freestanding wall meets a straight one
 * instead of the buildings; {@code =whole} shoots each map whole ({@code -whole.png}) and its right half
 * ({@code -half.png}), {@code =doors} frames street doors close; {@code -Dsnapshot.walls.depth=off} shoots with the Settings page's 3D walls off;
 * {@code -Dsnapshot.walls.night=on} shoots the town at night.
 */
public final class BuildingWallsLookSuite implements SnapshotSuite {

    private static final int FRAME_W = 1920, FRAME_H = 1080;
    private static final int MAP_W = 200, MAP_H = 128;
    private static final float PLAY = 60f, CLOSE = 40f;
    /** The whole map across, cells, for {@code -Dsnapshot.walls.frame=whole}. */
    private static final float WHOLE = MAP_W * 1.1f;

    private record World(String id, SurfacePalette palette, Climate climate) { }

    private static final List<World> WORLDS = List.of(
            new World("verdant", SurfacePalette.VERDANT, Climate.NONE),
            new World("arid", SurfacePalette.ARID, Climate.NONE),
            new World("frozen", SurfacePalette.FROZEN, Climate.NONE),
            new World("toxic", SurfacePalette.TOXIC, new Climate(EnumSet.of(Climate.Hazard.TOXIC))),
            new World("irradiated", SurfacePalette.IRRADIATED, new Climate(EnumSet.of(Climate.Hazard.RADIATION))),
            new World("volcanic", SurfacePalette.VOLCANIC, Climate.NONE));

    @Override public String id() { return "building-walls-look"; }

    @Override public boolean inAll() { return false; }

    @Override public String label() { return "Building walls stood up on six worlds: intact, cut away, damaged"; }

    @Override
    public List<SnapshotArtifact> render(SnapshotContext context) throws Exception {
        long seed = Long.getLong("snapshot.walls.seed", 7L);
        String only = System.getProperty("snapshot.walls.worlds", "");
        List<SnapshotArtifact> out = new ArrayList<>();
        // -Dsnapshot.walls.depth=off shoots the Settings page's 3D walls turned off (WallDepth).
        WallDepth.lookAs("off".equals(System.getProperty("snapshot.walls.depth")) ? Boolean.FALSE : null);
        try (HeadlessStrings strings = HeadlessBattles.install(context.projectRoot())) {
            for (World world : WORLDS) {
                if (!only.isBlank() && !List.of(only.split(",")).contains(world.id())) continue;
                TargetProfile profile = new TargetProfile(5, 5, 2, 1, "independent",
                        EnumSet.of(EconomicFunction.HABITATION, EconomicFunction.COMMERCE,
                                EconomicFunction.HEAVY_INDUSTRY, EconomicFunction.MILITARY),
                        world.palette(), SettlementLink.ROAD).withClimate(world.climate());
                PrecinctPlan plan = PrecinctPlan.derive(profile, Sprawl.BALANCED, MAP_W, MAP_H,
                        new Random(seed * 31 + 5));
                MapResult map = new BspCityGenerator().generate(MAP_W, MAP_H, seed, null, profile, plan);
                try (BattleSimulation sim = new BattleSimulation(map.grid, map.topology)) {
                    for (Doodad d : map.doodads) sim.addDoodad(d);
                    sim.setBuildings(map.buildings);
                    sim.setSurface(world.palette());
                    if ("on".equals(System.getProperty("snapshot.walls.night"))) {
                        // A dark town, its wall lamps lit (StreetLights) and some windows with it.
                        TownNight.fall(map);
                        System.out.println("building-walls-look " + world.id() + ": "
                                + map.topology.nightLighting().lamps().size() + " lights");
                    }
                    if ("whole".equals(System.getProperty("snapshot.walls.frame"))) {
                        // The whole map in one frame, as a player zoomed all the way out sees it.
                        BattleReviewFrameRenderer whole = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W,
                                FRAME_H, MAP_W / 2f, MAP_H / 2f, WHOLE);
                        out.add(new SnapshotArtifact(world.id() + "-whole.png", whole.render(sim, "")));
                        whole.refocus(MAP_W * .75f, MAP_H / 2f, WHOLE / 2f);
                        out.add(new SnapshotArtifact(world.id() + "-half.png", whole.render(sim, "")));
                        continue;
                    }
                    String framing = System.getProperty("snapshot.walls.frame");
                    if ("doors".equals(framing)) {
                        // Street doors, framed from a few cells out so their faces lean toward the view.
                        List<float[]> doors = doorFrames(sim, 3);
                        BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W,
                                FRAME_H, doors.get(0)[0], doors.get(0)[1], PLAY);
                        for (int f = 0; f < doors.size(); f++) {
                            // The door toward the frame's edge on its way out, where a face leans furthest.
                            float[] at = doors.get(f);
                            String tag = world.id() + "-door" + (f + 1);
                            look.refocus(at[0] + at[2] * PLAY * .38f, at[1] + at[3] * PLAY * .2f, PLAY);
                            out.add(new SnapshotArtifact(tag + "-play.png", look.render(sim, "")));
                            look.refocus(at[0] + at[2] * DOOR_CLOSE * .38f, at[1] + at[3] * DOOR_CLOSE * .2f,
                                    DOOR_CLOSE);
                            out.add(new SnapshotArtifact(tag + "-close.png", look.render(sim, "")));
                        }
                        System.out.printf(Locale.ROOT, "building-walls-look %s: door frames %s%n", world.id(),
                                describe(doors));
                        continue;
                    }
                    List<float[]> frames = "curtain".equals(framing) ? curtainFrames(sim, 2) : frames(sim, 2);
                    BattleReviewFrameRenderer look = BattleReviewFrameRenderer.look(context.modRoot(), FRAME_W,
                            FRAME_H, frames.get(0)[0], frames.get(0)[1], PLAY);
                    for (int f = 0; f < frames.size(); f++) {
                        float[] at = frames.get(f);
                        String tag = world.id() + "-" + (f + 1) + "-";
                        shoot(out, look, sim, at, tag + "intact");
                        Building cut = nearest(sim, at[0], at[1]);
                        if (cut != null) {
                            cut.currentAlpha = cut.targetAlpha = 0f;
                            shoot(out, look, sim, at, tag + "cutaway");
                            cut.currentAlpha = cut.targetAlpha = 1f;
                        }
                    }
                    for (int f = 0; f < frames.size(); f++) damage(sim, frames.get(f));
                    for (int f = 0; f < frames.size(); f++) {
                        shoot(out, look, sim, frames.get(f), world.id() + "-" + (f + 1) + "-damaged");
                    }
                    System.out.printf(Locale.ROOT, "building-walls-look %s: %d buildings, frames %s%n", world.id(),
                            sim.getBuildings().all().size(), describe(frames));
                }
            }
        } finally {
            WallDepth.lookAs(null);
        }
        return out;
    }

    private static void shoot(List<SnapshotArtifact> out, BattleReviewFrameRenderer look, BattleSimulation sim,
                              float[] at, String name) throws Exception {
        look.refocus(at[0], at[1], PLAY);
        out.add(new SnapshotArtifact(name + ".png", look.render(sim, "")));
        look.refocus(at[0], at[1], CLOSE);
        out.add(new SnapshotArtifact(name + "-close.png", look.render(sim, "")));
    }

    /**
     * Frames on open ground with the most building wall standing toward them: each candidate scored by the building
     * cells in frame weighted by their distance from its centre, where a face stands tallest. Each frame also names
     * the quarter its buildings lean to, for the close shot.
     */
    static List<float[]> frames(BattleSimulation sim, int count) {
        CellTopology t = sim.getTopology();
        int w = t.getWidth(), h = t.getHeight();
        List<float[]> picked = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            float best = -1f;
            float[] bestAt = null;
            for (int y = 18; y < h - 18; y += 3) {
                for (int x = 31; x < w - 31; x += 3) {
                    if (t.getBuildingId(x, y) != 0) continue;
                    boolean far = true;
                    for (float[] p : picked) far &= Math.abs(p[0] - x) > 36 || Math.abs(p[1] - y) > 22;
                    if (!far) continue;
                    float score = 0f, sx = 0f, sy = 0f;
                    for (int dy = -16; dy <= 16; dy++) {
                        for (int dx = -29; dx <= 29; dx++) {
                            if (t.getBuildingId(x + dx, y + dy) == 0) continue;
                            // Only a building's edge turned to the frame's centre stands a face toward it.
                            int ix = dx == 0 ? 0 : -Integer.signum(dx), iy = dy == 0 ? 0 : -Integer.signum(dy);
                            boolean edge = Math.abs(dx) > 2 && t.getBuildingId(x + dx + ix, y + dy) == 0
                                    || Math.abs(dy) > 2 && t.getBuildingId(x + dx, y + dy + iy) == 0;
                            if (!edge) continue;
                            float d = Math.max(Math.abs(dx) / 30f, Math.abs(dy) / 17f);
                            score += d;
                            sx += dx * d;
                            sy += dy * d;
                        }
                    }
                    if (score > best) { best = score; bestAt = new float[]{x + .5f, y + .5f, sx, sy}; }
                }
            }
            if (bestAt == null) break;
            picked.add(bestAt);
        }
        if (picked.isEmpty()) picked.add(new float[]{w / 2f, h / 2f, 1f, 1f});
        return picked;
    }

    /**
     * Frames on the joints where a diagonal freestanding wall runs into a straight one, each set a few cells off its
     * joint so the faces there stand toward the view's centre; falls back on {@link #frames} where there are none.
     */
    /** How many cells across a door's close frame is. */
    private static final float DOOR_CLOSE = 28f;

    /**
     * A building's street doors, each as {@code x, y, outX, outY}: the door's middle and its way out, at least a
     * frame apart.
     */
    static List<float[]> doorFrames(BattleSimulation sim, int count) {
        CellTopology ground = sim.getTopology();
        NavigationGrid grid = sim.getGrid();
        int w = ground.getWidth(), h = ground.getHeight();
        List<float[]> picked = new ArrayList<>();
        for (int y = 2; y < h - 2 && picked.size() < count; y++) {
            for (int x = 2; x < w - 2 && picked.size() < count; x++) {
                if (!grid.isDoorway(x, y)) continue;
                for (int d = 0; d < 4; d++) {
                    int dx = d == 0 ? 1 : d == 1 ? -1 : 0, dy = d == 2 ? 1 : d == 3 ? -1 : 0;
                    CellTopology.GroundKind out = ground.getGroundKind(x + dx, y + dy);
                    boolean street = out == CellTopology.GroundKind.STREET || out == CellTopology.GroundKind.SIDEWALK;
                    if (!street || ground.getBuildingId(x - dx, y - dy) == 0) continue;
                    float cx = x + .5f, cy = y + .5f;
                    boolean far = true;
                    for (float[] p : picked) far &= Math.abs(p[0] - cx) > 30 || Math.abs(p[1] - cy) > 18;
                    if (far) picked.add(new float[]{cx, cy, dx, dy});
                    break;
                }
            }
        }
        return picked.isEmpty() ? frames(sim, count) : picked;
    }

    static List<float[]> curtainFrames(BattleSimulation sim, int count) {
        List<float[]> joints = new ArrayList<>();
        for (FreestandingWalls.Run run : FreestandingWallFaces.runs(sim.getTopology())) {
            if (run.kind() == FreestandingWalls.Kind.ENVELOPE || run.kind() == FreestandingWalls.Kind.ENVELOPE_PLINTH)
                continue;
            float[] st = run.strokes();
            for (int o = 0; o < st.length; o += FreestandingWalls.STROKE) {
                if (st[o] == st[o + 2] || st[o + 1] == st[o + 3]) continue;
                for (int end = 0; end < 2; end++) {
                    float x = st[o + end * 2], y = st[o + end * 2 + 1];
                    for (int c = 0; c < st.length; c += FreestandingWalls.STROKE) {
                        float x0 = st[c], y0 = st[c + 1], x1 = st[c + 2], y1 = st[c + 3];
                        boolean straight = (x0 == x1) != (y0 == y1);
                        if (straight && x >= Math.min(x0, x1) && x <= Math.max(x0, x1) && y >= Math.min(y0, y1)
                                && y <= Math.max(y0, y1)) {
                            joints.add(new float[]{x, y});
                            break;
                        }
                    }
                }
            }
        }
        List<float[]> picked = new ArrayList<>();
        for (float[] j : joints) {
            if (picked.size() == count) break;
            boolean far = true;
            for (float[] p : picked) far &= Math.abs(p[0] - j[0]) > 20 || Math.abs(p[1] - j[1]) > 12;
            if (far) picked.add(new float[]{j[0] - 7f, j[1] - 4f, 1f, 1f});
        }
        System.out.printf(Locale.ROOT, "building-walls-look: %d curtain-wall joints%n", joints.size());
        return picked.isEmpty() ? frames(sim, count) : picked;
    }

    /** The building nearest a frame's centre, by its bounds. */
    private static Building nearest(BattleSimulation sim, float x, float y) {
        Building best = null;
        float bestD = Float.MAX_VALUE;
        for (Building b : sim.getBuildings().all()) {
            float cx = Math.max(b.minX, Math.min(b.maxX + 1, x)), cy = Math.max(b.minY, Math.min(b.maxY + 1, y));
            float d = (cx - x) * (cx - x) + (cy - y) * (cy - y);
            if (b.cellsX.length >= 16 && d < bestD) { bestD = d; best = b; }
        }
        return best;
    }

    /**
     * The buildings in a frame damaged by their own number: about a quarter razed (most of the roof burnt or fallen
     * in), half hurt (walls holed and scorched, a few breached, a corner's storeys fallen in) and a quarter left whole.
     */
    private static void damage(BattleSimulation sim, float[] at) {
        CellTopology t = sim.getTopology();
        NavigationGrid grid = sim.getGrid();
        for (Building b : sim.getBuildings().all()) {
            if (b.maxX < at[0] - 32 || b.minX > at[0] + 32 || b.maxY < at[1] - 18 || b.minY > at[1] + 18) continue;
            float d = BuildingFaces.hash(b.id, 77);
            if (d >= .75f) continue;
            boolean razed = d < .25f;
            int cornerX = BuildingFaces.hash(b.id, 78) < .5f ? b.minX : b.maxX;
            int cornerY = BuildingFaces.hash(b.id, 79) < .5f ? b.minY : b.maxY;
            float reach = Math.max(3f, .45f * Math.max(b.maxX - b.minX, b.maxY - b.minY));
            for (int i = 0; i < b.cellsX.length; i++) {
                int x = b.cellsX[i], y = b.cellsY[i];
                float near = (float) Math.hypot(x - cornerX, y - cornerY);
                if (razed ? BuildingFaces.hash(x + y * 4096, 80) < .85f : near < reach) {
                    sim.mapEditor().destroyRoof(x, y, BuildingFaces.hash(x + y * 4096, 81) < .5f
                            ? RoofDamage.BURNT : RoofDamage.COLLAPSED);
                } else if (BuildingFaces.hash(x + y * 4096, 82) < .06f) {
                    sim.mapEditor().destroyRoof(x, y, RoofDamage.HOLED);
                }
            }
            for (int y = b.minY - 1; y <= b.maxY + 1; y++) {
                for (int x = b.minX - 1; x <= b.maxX + 1; x++) {
                    if (!t.inBounds(x, y) || !t.isWall(x, y)) continue;
                    float r = BuildingFaces.hash(x + y * 4096, 83);
                    int hp = grid.getWallHp(x, y);
                    if (r < (razed ? .25f : .08f)) {
                        fire(sim, x, y, 1, WallScars.Mark.BLAST, 1.6f);
                        sim.damageCell(x, y, 100_000);
                    } else if (r < .65f) {
                        // Rounds fired at it from the street, kept where they land as the game keeps them, and the
                        // wall worn by as much.
                        int rounds = 2 + (int) (7 * BuildingFaces.hash(x + y * 4096, 84));
                        fire(sim, x, y, rounds, WallScars.Mark.CHIP, .6f);
                        if (BuildingFaces.hash(x + y * 4096, 85) < .35f) fire(sim, x, y, 1, WallScars.Mark.GOUGE, .6f);
                        if (BuildingFaces.hash(x + y * 4096, 86) < .12f) fire(sim, x, y, 1, WallScars.Mark.BLAST, 1.2f);
                        grid.setWallHp(x, y, Math.max(1, (int) (hp * (.15f + .6f * (r - .1f)))));
                    }
                }
            }
        }
    }

    /**
     * Fires {@code rounds} rounds at wall cell {@code (x, y)} from the open street beyond one of its open sides, each
     * kept where it reaches the wall ({@link WallScars#strike}), as {@code BattleScreen} keeps a round that stops on a
     * wall.
     */
    private static void fire(BattleSimulation sim, int x, int y, int rounds, WallScars.Mark mark, float size) {
        CellTopology t = sim.getTopology();
        int[][] sides = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int k = 0; k < rounds; k++) {
            int start = (int) (4 * BuildingFaces.hash(x * 31 + y * 4096 + k, 87));
            for (int s = 0; s < 4; s++) {
                int[] side = sides[(start + s) % 4];
                int ox = x + side[0], oy = y + side[1];
                if (!t.inBounds(ox, oy) || t.isWall(ox, oy) || t.getBuildingId(ox, oy) != 0) continue;
                float along = .15f + .7f * BuildingFaces.hash(x * 31 + y * 4096 + k, 88);
                float spread = (BuildingFaces.hash(x * 31 + y * 4096 + k, 89) - .5f) * 6f;
                // Where the round reaches the wall: on the side it came from, along it.
                float hx = side[0] != 0 ? x + (side[0] > 0 ? 1f : 0f) : x + along;
                float hy = side[1] != 0 ? y + (side[1] > 0 ? 1f : 0f) : y + along;
                float fx = hx + side[0] * 8f + (side[0] == 0 ? spread : 0f);
                float fy = hy + side[1] * 8f + (side[1] == 0 ? spread : 0f);
                sim.effects().wallScars().strike(t.getWidth(), fx, fy, hx, hy, 0f, mark, size);
                break;
            }
        }
    }

    private static String describe(List<float[]> frames) {
        StringBuilder s = new StringBuilder();
        for (float[] f : frames) s.append(String.format(Locale.ROOT, "(%.0f,%.0f) ", f[0], f[1]));
        return s.toString().trim();
    }
}
