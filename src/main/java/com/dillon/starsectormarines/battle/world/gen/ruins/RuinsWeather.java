package com.dillon.starsectormarines.battle.world.gen.ruins;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.StormDrift;
import com.dillon.starsectormarines.battle.world.gen.SurfaceField;
import com.dillon.starsectormarines.battle.world.gen.SurfacePalette;
import com.dillon.starsectormarines.battle.world.gen.ruins.RuinsDelve.Area;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The world and the years on a dead site ({@code mapgen-nouns.md}, Ruins ground, The world takes the ruin back): a
 * ruin long dead is not a clean concrete block. Its own world's ground lies over the dead town in drifts and banks
 * against every standing wall; it blows in through the megastructure's mouths in a tongue that thins down the
 * corridor; dust and rime bank along the corridor walls, thicker near the mouths; water stands where the roof leaks;
 * and ceiling has fallen here and there.
 *
 * <p>All of it is look, never terrain: surface skins over cells that keep their ground kind, and flat decals with no
 * cover (the {@code ruins-delve-weather} sheet). Every choice is a coordinate noise or hash on the site's seed, so the
 * same site weathers the same way and the layout never changes with the world. It is the last skin laid
 * ({@link RuinsWeatherStage}), after the streets are finished; it leaves the landing ground, the courts, the aprons,
 * the vault, the themed halls and every authored surface but the bare floors alone.
 */
public final class RuinsWeather {

    /** How far a world's ground blows in from a mouth, cells along the corridor. */
    static final int REACH = 12;
    /** How far dust banks thin along the corridor walls, cells from the nearest mouth; deeper it is a thin line. */
    static final int BANK_REACH = 40;
    /** The floors the weather lies on inside: the megastructure's cast concrete. */
    static final String INDOOR_FLOOR = "installation.concrete-floor";
    /** The dead town's floors it lies on outside: bare ground, its trodden earth and rubble bed, the lots' floors. */
    static final List<String> OUTDOOR_FLOORS = List.of("pavement.dirt.worn", "pavement.gravel.cracked",
            "colony.concrete", "colony.timber", "colony.terrazzo", "colony.terracotta");

    /** The share of the dead town's open ground a world takes back, before the banks against its walls. */
    static final float RECLAIM = 0.62f;
    /** The noise scale of the dead town's drifts, cells. */
    static final float RECLAIM_SCALE = 6f;
    /** The share of the indoor floor where water stands. */
    static final float DAMP = 0.09f;
    /** One cell in this many indoor floor cells has fallen ceiling on it; a few more, chips. */
    static final int FALL_EVERY = 70, CHIPS_EVERY = 45;

    private RuinsWeather() { }

    /** What the weathering laid, for a test or an evidence line. */
    public record Report(int reclaimed, int blownIn, int banked, int damp, int decals) { }

    /** The ground a world puts back over a dead town: its own drift, or for a living world its earth and green. */
    public static String reclaim(SurfacePalette world) {
        return switch (world) {
            case VERDANT -> "floors.grass";
            case VOLCANIC -> "volcanic.basalt";
            default -> StormDrift.driftSurface(world);
        };
    }

    /** The ground a world blows in through a mouth. */
    public static String blowIn(SurfacePalette world) {
        return world == SurfacePalette.VERDANT ? "ruins-ground.biofilm" : reclaim(world);
    }

    /** What banks along a corridor's walls deep inside: rime on a frozen world, the world's dust or growth elsewhere. */
    public static String bank(SurfacePalette world) {
        return switch (world) {
            case FROZEN -> "ruins-ground.rime";
            case VERDANT -> "ruins-ground.biofilm";
            case VOLCANIC -> "volcanic.ash";
            case TOXIC -> "toxic-ground.crust";
            case ARID, ROCK, IRRADIATED -> "floors.sand";
        };
    }

    /** Where water stands: a frozen world's pools are ice, a toxic world's sludge. */
    public static String damp(SurfacePalette world) {
        return switch (world) {
            case FROZEN -> "ruins-ground.ice-sheet";
            default -> "ruins-ground.wet-floor";
        };
    }

    /** The tongue decal a mouth wears, by what the world blows in. */
    public static String tongue(SurfacePalette world) {
        return switch (world) {
            case FROZEN -> "doodad.ruins-snow-tongue";
            case ARID, IRRADIATED -> "doodad.ruins-sand-tongue";
            case VOLCANIC -> "doodad.ruins-ash-tongue";
            case TOXIC -> "doodad.ruins-sulphur-tongue";
            case VERDANT -> "doodad.ruins-root-mat";
            case ROCK -> "doodad.ruins-dust-tongue";
        };
    }

    /**
     * Weathers the dead site. {@code registry} may be null (no decals, only skins); {@code doodads} receives the
     * decals at the bottom of the draw order, under every prop.
     */
    public static Report apply(CellTopology topology, NavigationGrid grid, RuinsDelve delve, SurfaceField field,
                               TileRegistry registry, List<Doodad> doodads) {
        int w = topology.getWidth(), h = topology.getHeight();
        long seed = field.seed();
        boolean[] kept = keptClear(delve, w, h);
        Area inside = delve.interior() == null ? null : delve.interior().footprint();
        int indoor = topology.surfaceIndex(INDOOR_FLOOR);
        int[] outdoor = OUTDOOR_FLOORS.stream().mapToInt(topology::surfaceIndex).toArray();

        // Inside: how far each bare floor cell is from the nearest mouth, walking the floor.
        int[] dist = inside == null ? null : mouthDistance(topology, grid, delve, inside, indoor, kept);
        int reclaimed = 0, blownIn = 0, banked = 0, damp = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (kept[y * w + x] || topology.isWall(x, y) || topology.isFixture(x, y)) continue;
                SurfacePalette world = field.at(x, y);
                int surface = topology.getSurface(x, y);
                if (inside != null && inside.contains(x, y)) {
                    if (surface != indoor) continue;
                    int d = dist[y * w + x];
                    float n = SurfaceField.noise(x / 3f, y / 3f, seed ^ 0x57454154L);
                    if (d <= REACH && n * 1.25f > (float) d / REACH) {
                        topology.setSurface(x, y, topology.surfaceIndex(blowIn(world)));
                        blownIn++;
                    } else if (nextToWall(topology, x, y) && SurfaceField.noise(x / 2f, y / 2f, seed ^ 0x42414E4BL)
                            > 0.35f + 0.45f * Math.min(d, BANK_REACH) / BANK_REACH) {
                        topology.setSurface(x, y, topology.surfaceIndex(bank(world)));
                        banked++;
                    } else if (SurfaceField.noise(x / 4f, y / 4f, seed ^ 0x44414D50L) > 1f - DAMP * 2.2f) {
                        topology.setSurface(x, y, topology.surfaceIndex(damp(world)));
                        damp++;
                    }
                    continue;
                }
                if (!outdoorGround(topology, x, y, surface, outdoor)) continue;
                // The world's own open ground is already the world's: it keeps its own look and features.
                if (surface == 0 && topology.getGroundKind(x, y) == world.inlandGround()) continue;
                float n = SurfaceField.noise(x / RECLAIM_SCALE, y / RECLAIM_SCALE, seed ^ 0x5245434CL);
                // Bare ground and trodden earth go first; a lot's floor only where the drift is deep.
                float share = surface == 0 || surface == outdoor[0] ? RECLAIM : RECLAIM * 0.5f;
                if (nextToWall(topology, x, y)) share += 0.35f;
                if (n > 1f - share) {
                    topology.setSurface(x, y, topology.surfaceIndex(reclaim(world)));
                    reclaimed++;
                }
            }
        }
        int decals = registry == null ? 0 : decals(topology, delve, field, registry, doodads, kept, indoor, dist);
        return new Report(reclaimed, blownIn, banked, damp, decals);
    }

    /** Cells the weather leaves alone: the landing, the courts, the aprons, the vault and the themed halls. */
    static boolean[] keptClear(RuinsDelve delve, int w, int h) {
        boolean[] kept = new boolean[w * h];
        mark(kept, w, h, delve.landingGround(), 1);
        mark(kept, w, h, delve.vault().footprint(), 2);
        for (RuinsDelve.Layer layer : delve.layers()) {
            if (layer.court() != null) mark(kept, w, h, layer.court().ground(), 1);
        }
        if (delve.interior() != null) {
            for (RuinsDelve.Apron apron : delve.interior().aprons()) mark(kept, w, h, apron.ground(), 0);
            for (RuinsDelve.PlantHall hall : delve.interior().halls()) mark(kept, w, h, hall.footprint(), 0);
        }
        return kept;
    }

    private static void mark(boolean[] kept, int w, int h, Area a, int margin) {
        for (int y = Math.max(0, a.bottom() - margin); y <= Math.min(h - 1, a.top() + margin); y++) {
            for (int x = Math.max(0, a.left() - margin); x <= Math.min(w - 1, a.right() + margin); x++) kept[y * w + x] = true;
        }
    }

    /** Whether an outdoor cell is open ground the world may take back: never road, water, a drop or a wall's rubble. */
    static boolean outdoorGround(CellTopology topology, int x, int y, int surface, int[] outdoor) {
        if (topology.isRubble(x, y) && topology.getWallRemnant(x, y) != null) return false;
        if (surface != 0 && Arrays.stream(outdoor).noneMatch(s -> s == surface)) return false;
        if (StormDrift.paved(topology.roadSurface(), x, y)) return false;
        return switch (topology.getGroundKind(x, y)) {
            case DIRT, STONE, SAND, SNOW, HARDPAN, GRASS, COURTYARD, BRICK, RUBBLE -> true;
            default -> false;
        };
    }

    static boolean nextToWall(CellTopology topology, int x, int y) {
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = x + o[0], ny = y + o[1];
            if (topology.inBounds(nx, ny) && topology.isWall(nx, ny)) return true;
        }
        return false;
    }

    /** Steps from the nearest mouth to every walkable interior cell; {@link Integer#MAX_VALUE} where none reaches. */
    static int[] mouthDistance(CellTopology topology, NavigationGrid grid, RuinsDelve delve, Area inside, int indoor,
                               boolean[] kept) {
        int w = topology.getWidth(), h = topology.getHeight();
        int[] dist = new int[w * h];
        Arrays.fill(dist, Integer.MAX_VALUE);
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        for (Area door : delve.interior().entrances()) {
            for (int y = door.bottom(); y <= door.top(); y++) {
                for (int x = door.left(); x <= door.right(); x++) {
                    if (!topology.inBounds(x, y)) continue;
                    dist[y * w + x] = 0;
                    queue.add(new int[]{x, y});
                }
            }
        }
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            int d = dist[c[1] * w + c[0]];
            for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + o[0], ny = c[1] + o[1];
                if (!inside.contains(nx, ny) || !topology.inBounds(nx, ny) || dist[ny * w + nx] != Integer.MAX_VALUE) continue;
                if (topology.isWall(nx, ny) || !grid.isWalkable(nx, ny)) continue;
                dist[ny * w + nx] = d + 1;
                queue.add(new int[]{nx, ny});
            }
        }
        return dist;
    }

    /** Lays the tongues at every mouth and the fallen ceiling and seeps down the corridors; how many it laid. */
    static int decals(CellTopology topology, RuinsDelve delve, SurfaceField field, TileRegistry registry,
                      List<Doodad> doodads, boolean[] kept, int indoor, int[] dist) {
        int w = topology.getWidth();
        long seed = field.seed();
        List<Doodad> laid = new ArrayList<>();
        boolean[] taken = new boolean[kept.length];
        if (delve.interior() != null) {
            // A mouth is a vertical span in a west-facing wall: a tongue lies just inside it, its wide end west.
            for (Area door : delve.interior().entrances()) {
                SurfacePalette world = field.at(door.right() + 1, door.centreY());
                DoodadDef def = registry.doodad(tongue(world));
                if (def == null) continue;
                int across = def.footprintCellsX;         // the tongue's width runs along the door once turned
                int along = def.footprintCellsY;
                if (world == SurfacePalette.VERDANT) {    // growth: one mat at the mouth, not a row of them
                    if (clear(topology, kept, taken, door.right() + 1, door.bottom(), along, across, -1)) {
                        laid.add(new Doodad(door.right() + 1, door.bottom(), def, 3));
                        take(taken, w, door.right() + 1, door.bottom(), along, across);
                    }
                    continue;
                }
                for (int y = door.bottom(); y + across - 1 <= door.top(); y += across) {
                    int x = door.right() + 1;
                    if (!clear(topology, kept, taken, x, y, along, across, -1)) continue;
                    laid.add(new Doodad(x, y, def, 3));
                    take(taken, w, x, y, along, across);
                }
            }
            DoodadDef fall = registry.doodad("doodad.ruins-fallen-ceiling");
            DoodadDef chips = registry.doodad("doodad.ruins-fallen-panel");
            DoodadDef seep = registry.doodad("doodad.ruins-seep-stain");
            DoodadDef mat = registry.doodad("doodad.ruins-root-mat");
            Area inside = delve.interior().footprint();
            for (int y = inside.bottom(); y <= inside.top(); y++) {
                for (int x = inside.left(); x <= inside.right(); x++) {
                    long r = hash(x, y, seed);
                    DoodadDef def;
                    if (r % FALL_EVERY == 0) def = fall;
                    else if (r % CHIPS_EVERY == 1) def = chips;
                    else if (r % FALL_EVERY == 2) def = seep;
                    else if (r % FALL_EVERY == 3 && field.at(x, y) == SurfacePalette.VERDANT && dist[y * w + x] < REACH * 2) def = mat;
                    else continue;
                    if (def == null || !clear(topology, kept, taken, x, y, def.footprintCellsX, def.footprintCellsY, indoor)) continue;
                    laid.add(new Doodad(x, y, def, (int) (r >>> 8) & 3));
                    take(taken, w, x, y, def.footprintCellsX, def.footprintCellsY);
                }
            }
        }
        doodads.addAll(0, laid);
        return laid.size();
    }

    /** Whether a decal fits: every cell walkable floor, in bounds, off the kept ground and the decals laid already. */
    private static boolean clear(CellTopology topology, boolean[] kept, boolean[] taken, int x, int y, int fw, int fh,
                                 int indoor) {
        int w = topology.getWidth();
        for (int dy = 0; dy < fh; dy++) {
            for (int dx = 0; dx < fw; dx++) {
                int cx = x + dx, cy = y + dy;
                if (!topology.inBounds(cx, cy) || topology.isWall(cx, cy) || topology.isFixture(cx, cy)) return false;
                int i = cy * w + cx;
                if (kept[i] || taken[i]) return false;
                if (indoor >= 0 && topology.getSurface(cx, cy) == 0) return false;
            }
        }
        return true;
    }

    private static void take(boolean[] taken, int w, int x, int y, int fw, int fh) {
        for (int dy = 0; dy < fh; dy++) for (int dx = 0; dx < fw; dx++) taken[(y + dy) * w + x + dx] = true;
    }

    static long hash(int x, int y, long seed) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ seed * 0x165667B19E3779F9L;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return h >>> 1;
    }
}
