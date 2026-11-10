package com.dillon.starsectormarines.battle.world.gen;

import com.dillon.starsectormarines.battle.world.gen.bsp.stage.HabitatStage;
import com.dillon.starsectormarines.battle.world.gen.nature.Flora;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralGround;
import com.dillon.starsectormarines.battle.world.gen.rural.RuralLayout;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainPlan;
import com.dillon.starsectormarines.battle.world.gen.terrain.TerrainSurface;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.Doodad;
import com.dillon.starsectormarines.battle.world.model.RockDamage;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileCover;
import com.dillon.starsectormarines.battle.world.tiles.TileDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Re-dresses a finished story map for the world it is fought on ({@code story-map-pools.md}, Planet skin). A story
 * mission owns one map, generated on its home palette; every other world plays that same map, skinned. The owner:
 * "All of these should use a single seed, that we own and make good. They should be skinned for the planet and make
 * sense for the planet."
 *
 * <p><b>The layout never moves.</b> No cell changes whether it can be walked, seen through or built on: the skin swaps
 * what ground, trees, stones, cliffs and walls are drawn as, and substitutes worked fields for what a colony grows on
 * a world where open fields make no sense: the plot cleared to the planet's own ground and a walk-in covered farm
 * stood on it, as walkable and as open to sight as it was. Every rule here is a
 * pure function of the home palette and the world, so a test states it without a map.
 *
 * <p>What it does not do, because each would move the layout: raise a sealed perimeter round a town, add a drop, grow
 * new water or woods. A drop belongs on a map only where its terrain explains one ({@code owned-maps.md}).
 */
public final class PlanetSkin {

    private PlanetSkin() { }

    /** What a colony's worked fields are laid as on a world. */
    public enum FieldUse {
        /** Open crops, grain and pasture, as laid: a world where things grow in the open. */
        CROPS,
        /**
         * Grown under cover: the plot cleared to the planet's own ground and a covered farm stood on it in rows
         * ({@link #covered}), lit hydroponic buildings a marine walks into and takes no cover behind. Paved
         * hydroponic bays on the footprint were tried and read as concrete slabs (owner, 2026-11-10).
         */
        BARE
    }

    /** {@code map}, generated on {@code home}, skinned for {@code world} in place. Returns {@code map}. */
    public static MapResult apply(MapResult map, SurfacePalette home, TargetProfile world) {
        if (map == null || world == null) return map;
        SurfacePalette to = world.surface();
        LivingWorld living = to == SurfacePalette.VERDANT ? world.living() : LivingWorld.TEMPERATE;
        CellTopology topology = map.topology;
        TileRegistry tiles = TileRegistry.installed();
        GenMappingRegistry mapping = GenMappingRegistry.installed();
        boolean[] field = fieldMask(map);
        if (to != home) {
            topology.setGroundPalette(to);
            topology.setSurfaceField(new SurfaceField(to, Basin.NONE, topology.getWidth(), topology.getHeight(),
                    map.topology.surfaceField() == null ? 0 : map.topology.surfaceField().seed(), living));
            ground(topology, home, to, field, mapping);
            if (tiles != null) {
                overlays(topology, tiles, home, to, field);
                doodads(map, tiles, home, to, living);
                cliffs(topology, home, to);
            }
        }
        if (world.signature() != null) topology.setSignature(world.signature());
        FieldUse use = fieldsOn(to, climateOf(world));
        if (use != FieldUse.CROPS) {
            fields(map, field, use, to);
            if (tiles != null) farm(map, field, tiles);
        }
        Habitat habitat = new BiomeSkin(to, living, world.signature(), climateOf(world)).habitat();
        if (habitat != Habitat.OPEN) HabitatStage.rebuild(topology, habitat);
        return map;
    }

    // --- the rules ------------------------------------------------------------------------------------------------

    /**
     * The air a world has when its profile says nothing: none on bare rock, which in the Sector is an airless world.
     * Every other world's silence stands, and a climate the profile names always wins.
     */
    public static Climate climateOf(TargetProfile world) {
        Climate stated = world.climate();
        if (stated != null && !stated.hazards().isEmpty()) return stated;
        return world.surface() == SurfacePalette.ROCK ? new Climate(EnumSet.of(Climate.Hazard.AIRLESS)) : Climate.NONE;
    }

    /**
     * How a world's colony grows its food. In the open only where things grow and the air lets them: a living or
     * desert world with breathable air. Nowhere else: no open fields on a frozen, bare-rock, volcanic,
     * irradiated or toxic world, nor under air that kills (owner: "farm fields don't make sense on a frozen world").
     */
    public static FieldUse fieldsOn(SurfacePalette world, Climate climate) {
        boolean open = world == SurfacePalette.VERDANT || world == SurfacePalette.ARID;
        boolean air = climate == null || !(climate.has(Climate.Hazard.TOXIC) || climate.has(Climate.Hazard.AIRLESS)
                || climate.has(Climate.Hazard.RADIATION));
        return open && air ? FieldUse.CROPS : FieldUse.BARE;
    }

    /**
     * What a natural cell's ground is on the world: the home's wild ground and its living lawn become the world's own
     * wild ground ({@link SurfacePalette#inlandGround}); bare stone, earth, snow and hardpan stay what they are, drawn
     * in the world's material. Made ground (streets, floors, paving) is never the skin's.
     */
    public static GroundKind ground(GroundKind kind, SurfacePalette home, SurfacePalette world) {
        if (kind == null || home == world) return kind;
        if (kind == home.inlandGround() || kind == GroundKind.GRASS || kind == GroundKind.SAND) return world.inlandGround();
        return kind;
    }

    /**
     * Which woods a world grows where the home map grew {@code home}: the home's own where the world is the home's
     * palette; a living world's own species; boreal conifers on a frozen world; and grey, dead scrub on a world where
     * nothing lives, the same silhouettes standing where the trees stood.
     */
    public static Flora flora(Flora home, SurfacePalette homePalette, SurfacePalette world, LivingWorld living) {
        if (world == homePalette) return home;
        return switch (world) {
            case VERDANT -> living == null ? Flora.TEMPERATE : living.flora();
            case FROZEN -> Flora.TAIGA;
            case ARID -> home == Flora.DRY ? Flora.DRY : Flora.TEMPERATE;
            default -> Flora.DRY;
        };
    }

    /** Whether loose plants (tufts, rosettes, hedges) grow on this world's open ground: only where things grow. */
    public static boolean growsPlants(SurfacePalette world) {
        return world == SurfacePalette.VERDANT || world == SurfacePalette.ARID;
    }

    /** What a cell of a worked field is laid as: crops as laid ({@link RuralGround#plotGround}), else the world's own wild ground. */
    public static GroundKind fieldGround(RuralLayout.Plot plot, int x, int y, FieldUse use, SurfacePalette world) {
        GroundKind crop = RuralGround.plotGround(plot, x, y);
        return use == FieldUse.CROPS ? crop : world.inlandGround();
    }

    // --- the passes -----------------------------------------------------------------------------------------------

    private static final Set<GroundKind> NATURAL = EnumSet.of(GroundKind.GRASS, GroundKind.DIRT, GroundKind.STONE,
            GroundKind.SAND, GroundKind.SNOW, GroundKind.HARDPAN);

    private static boolean[] fieldMask(MapResult map) {
        int w = map.topology.getWidth(), h = map.topology.getHeight();
        boolean[] mask = new boolean[w * h];
        for (RuralLayout.Plot plot : map.fields) {
            for (int y = Math.max(0, plot.y0()); y <= Math.min(h - 1, plot.y1()); y++)
                for (int x = Math.max(0, plot.x0()); x <= Math.min(w - 1, plot.x1()); x++)
                    if (worked(map.topology, x, y)) mask[y * w + x] = true;
        }
        return mask;
    }

    /** Whether a field's cell is still the field: open ground, not a building or wall laid over it later. */
    private static boolean worked(CellTopology topology, int x, int y) {
        return !topology.isWall(x, y) && topology.getBuildingId(x, y) == 0
                && NATURAL.contains(topology.getGroundKind(x, y));
    }

    /**
     * The world's wild ground on every natural cell: wild ground, and a lot's lawn or sand laid as the plain floor of
     * its kind, which then draws in the world's material. Paving, worn earth and fields keep their own.
     */
    private static void ground(CellTopology topology, SurfacePalette home, SurfacePalette world, boolean[] field,
                               GenMappingRegistry mapping) {
        int w = topology.getWidth();
        String lawn = mapping == null ? null : mapping.groundBlockId(GroundKind.GRASS);
        String sand = mapping == null ? null : mapping.groundBlockId(GroundKind.SAND);
        for (int y = 0; y < topology.getHeight(); y++) {
            for (int x = 0; x < w; x++) {
                if (field[y * w + x]) continue;
                GroundKind kind = topology.getGroundKind(x, y);
                if (!NATURAL.contains(kind)) continue;
                boolean plain = topology.getSurface(x, y) == 0;
                if (!plain) {
                    String id = topology.getSurfaceId(x, y);
                    if (id == null || !(id.equals(lawn) || id.equals(sand))) continue;
                }
                GroundKind skinned = ground(kind, home, world);
                if (skinned == kind) continue;
                topology.setGroundKind(x, y, skinned);
                if (!plain) topology.setSurface(x, y, 0);
            }
        }
    }

    /** Field stones in the world's set; loose plants off a world where nothing grows. Fields are left to their pass. */
    private static void overlays(CellTopology topology, TileRegistry tiles, SurfacePalette home, SurfacePalette world,
                                 boolean[] field) {
        int w = topology.getWidth();
        boolean plants = growsPlants(world);
        for (int y = 0; y < topology.getHeight(); y++) {
            for (int x = 0; x < w; x++) {
                int index = topology.getNatureOverlayIndex(x, y);
                if (index < 0) continue;
                TileDef def = tiles.byIndex(index);
                String stone = stone(def.id, world.stones());
                if (stone != null) {
                    TileDef to = tiles.tile(stone);
                    if (to != null && to.index != index) topology.setNatureOverlayIndex(x, y, to.index);
                } else if (!plants && !field[y * w + x] && looseAndBare(def)) {
                    topology.setNatureOverlayIndex(x, y, -1);
                }
            }
        }
    }

    /** {@code id} as the same stone in {@code set}, or null when it is no field stone. */
    static String stone(String id, RockDamage.Biome set) {
        if (id == null || !RockDamage.IDS.contains(id)) return null;
        for (RockDamage.Biome from : RockDamage.Biome.values())
            for (String group : RockDamage.GROUPS)
                if (from.stone(group).equals(id)) return set.stone(group);
        return null;
    }

    /** A plant a marine walks through and takes no cover behind: taking it off moves nothing. */
    private static boolean looseAndBare(TileDef def) {
        return def.passable && def.id.startsWith("nature.") && def.cover == TileCover.NONE;
    }

    /** Trees and undergrowth in the world's flora, the same silhouettes on the same cells. */
    private static void doodads(MapResult map, TileRegistry tiles, SurfacePalette home, SurfacePalette world,
                                LivingWorld living) {
        List<Doodad> props = map.doodads;
        for (int i = 0; i < props.size(); i++) {
            Doodad d = props.get(i);
            String id = d.definitionId();
            if (id == null) continue;
            String base = Flora.baseId(id);
            if (!base.startsWith("forest.")) continue;
            Flora from = flora(id);
            Flora to = flora(from, home, world, living);
            if (to == from) continue;
            DoodadDef def = tiles.doodad(base + to.suffix);
            if (def == null) continue;
            props.set(i, new Doodad(d.cellX, d.cellY, def, d.quarterTurns, d.mirrored));
        }
    }

    private static Flora flora(String id) {
        for (Flora f : Flora.values()) if (!f.suffix.isEmpty() && id.endsWith(f.suffix)) return f;
        return Flora.TEMPERATE;
    }

    /** Raised ground in the world's finish: the same cliffs, faced in its stone or ice, grass-topped where it grows. */
    private static void cliffs(CellTopology topology, SurfacePalette home, SurfacePalette world) {
        TerrainSurface surface = topology.terrainSurface();
        if (surface == null) return;
        TerrainPlan.Finish from = TerrainPlan.Finish.of(home), to = TerrainPlan.Finish.of(world);
        if (from == to) return;
        topology.setTerrainSurface(surface.remapped(s -> {
            String part = cliffPart(topology.surfaceId(s), from);
            if (part == null) return s;
            if (to == TerrainPlan.Finish.GRASS_TOP && part.equals("rock")) return 0;
            return topology.surfaceIndex(to.materialPrefix() + part);
        }));
    }

    /** What part of a cliff {@code id} faces in {@code finish} ("talus", "rock", "face.3"), or null. */
    static String cliffPart(String id, TerrainPlan.Finish finish) {
        if (id == null) return null;
        String prefix = finish.materialPrefix();
        if (!id.startsWith(prefix)) return null;
        String part = id.substring(prefix.length());
        return part.equals("talus") || part.equals("rock") || part.matches("face\\.[0-6]") ? part : null;
    }

    /** Every worked field laid as {@code use}: cleared to the world's own ground, its hedges off it. */
    private static void fields(MapResult map, boolean[] mask, FieldUse use, SurfacePalette world) {
        CellTopology topology = map.topology;
        int w = topology.getWidth();
        for (RuralLayout.Plot plot : map.fields) {
            for (int y = Math.max(0, plot.y0()); y <= Math.min(topology.getHeight() - 1, plot.y1()); y++) {
                for (int x = Math.max(0, plot.x0()); x <= Math.min(w - 1, plot.x1()); x++) {
                    if (!mask[y * w + x]) continue;
                    GroundKind kind = fieldGround(plot, x, y, use, world);
                    topology.setGroundKind(x, y, kind);
                    topology.setSurface(x, y, 0);
                    topology.setNatureOverlayIndex(x, y, -1);
                }
            }
        }
    }

    // --- the covered farm --------------------------------------------------------------------------------------------

    /** A piece of the covered farm: its doodad and its footprint unturned, along a row then across it. */
    public record Piece(String id, int along, int across) { }

    /**
     * What a covered farm is built as: a long piece the rows are made of, a short one that ends a row or fills a plot
     * too short for a long one, and a plant piece (pumps, tanks, a boiler) that ends a row where only it fits. The
     * owner rejected polytunnels ("They don't read like hydroponics", 2026-11-10); these three are the question put
     * back, every piece lit by grow-light and piped.
     */
    public enum FarmLook {
        /** Pressurised sheds with lit grow-light skylights in rows, a pump house to finish a row. */
        SHEDS(new Piece("doodad.hydro-shed", 6, 3), new Piece("doodad.hydro-shed-short", 3, 3),
                new Piece("doodad.hydro-pumphouse", 2, 3)),
        /** Geodesic domes lit inside, in linked pairs, a tank station to finish a row. */
        DOMES(new Piece("doodad.hydro-dome-pair", 9, 4), new Piece("doodad.hydro-dome", 4, 4),
                new Piece("doodad.hydro-dome-tanks", 2, 4)),
        /** Rigid glass halls over crop rows under grow-light bars, a boiler house to finish a row. */
        GLASSHOUSES(new Piece("doodad.glasshouse", 8, 4), new Piece("doodad.glasshouse-short", 4, 4),
                new Piece("doodad.glasshouse-boiler", 2, 4)),
        /** No farm: the plot left as the planet's ground. The control the looks are judged against. */
        BARE(null, null, null);

        public final Piece row, end, plant;

        FarmLook(Piece row, Piece end, Piece plant) {
            this.row = row;
            this.end = end;
            this.plant = plant;
        }
    }

    /**
     * Which covered farm stands on a cleared field: the look {@code -Dbattle.planetSkin.farm=sheds|domes|glasshouses|bare}
     * names, sheds by default.
     * Temporary, for the owner's pick of hydroponics look (picks page, planet-skin-farm): remove it, with the looks
     * not picked and their sheets, once the owner picks one.
     */
    static FarmLook farmLook() {
        String v = System.getProperty("battle.planetSkin.farm", "sheds").trim().toUpperCase(Locale.ROOT);
        for (FarmLook look : FarmLook.values()) if (look.name().equals(v)) return look;
        return FarmLook.SHEDS;
    }

    /** One piece stood on a plot: its footprint's corner and quarter turns (1 when the rows run along y). */
    public record Placed(Piece piece, int x, int y, int turns) {
        public int width() { return turns % 2 == 0 ? piece.along() : piece.across(); }
        public int height() { return turns % 2 == 0 ? piece.across() : piece.along(); }
    }

    /** The lane left between two rows of the covered farm, and between pieces along a row, in cells. */
    static final int LANE = 1;

    /**
     * The covered farm of {@code look} on a plot: inside a one-cell headland, rows as deep as the long piece along the
     * plot's crop rows with a {@link #LANE} between them, each row long pieces a lane apart finished with a short
     * piece, or the plant piece where only that fits. A plot too short for a long piece along its rows takes short
     * pieces on a grid instead, where one fits. Pure: what stands where depends on the plot and the look alone.
     */
    public static List<Placed> covered(RuralLayout.Plot plot, FarmLook look) {
        List<Placed> out = new ArrayList<>();
        if (look.row == null) return out;
        int x0 = plot.x0() + 1, y0 = plot.y0() + 1, x1 = plot.x1() - 1, y1 = plot.y1() - 1;
        boolean alongX = plot.rowsAlongX();
        int length = alongX ? x1 - x0 + 1 : y1 - y0 + 1, depth = alongX ? y1 - y0 + 1 : x1 - x0 + 1;
        int turns = alongX ? 0 : 1;
        Piece row = look.row, end = look.end, plant = look.plant;
        if (length < row.along() && length >= end.along() && depth >= end.across()) {
            for (int a = 0; a + end.across() <= depth; a += end.across() + LANE)
                for (int l = 0; l + end.along() <= length; l += end.along() + LANE)
                    out.add(at(end, alongX, x0, y0, l, a, turns));
            return out;
        }
        for (int a = 0; a + row.across() <= depth; a += row.across() + LANE) {
            int l = 0;
            while (l + row.along() <= length) {
                out.add(at(row, alongX, x0, y0, l, a, turns));
                l += row.along() + LANE;
            }
            if (l + end.along() <= length) out.add(at(end, alongX, x0, y0, l, a, turns));
            else if (l + plant.along() <= length) out.add(at(plant, alongX, x0, y0, l, a, turns));
        }
        return out;
    }

    private static Placed at(Piece piece, boolean alongX, int x0, int y0, int along, int across, int turns) {
        return alongX ? new Placed(piece, x0 + along, y0 + across, turns) : new Placed(piece, x0 + across, y0 + along, turns);
    }

    /** The covered farm on every cleared field, each piece only where every cell under it is still that field. */
    private static void farm(MapResult map, boolean[] mask, TileRegistry tiles) {
        int w = map.topology.getWidth(), h = map.topology.getHeight();
        boolean[] taken = new boolean[w * h];
        FarmLook look = farmLook();
        for (RuralLayout.Plot plot : map.fields) {
            for (Placed p : covered(plot, look)) {
                DoodadDef def = tiles.doodad(p.piece().id);
                if (def == null || !clear(p, mask, taken, w, h)) continue;
                for (int y = p.y(); y < p.y() + p.height(); y++)
                    for (int x = p.x(); x < p.x() + p.width(); x++) taken[y * w + x] = true;
                map.doodads.add(new Doodad(p.x(), p.y(), def, p.turns()));
            }
        }
    }

    private static boolean clear(Placed p, boolean[] mask, boolean[] taken, int w, int h) {
        if (p.x() < 0 || p.y() < 0 || p.x() + p.width() > w || p.y() + p.height() > h) return false;
        for (int y = p.y(); y < p.y() + p.height(); y++)
            for (int x = p.x(); x < p.x() + p.width(); x++)
                if (!mask[y * w + x] || taken[y * w + x]) return false;
        return true;
    }
}
