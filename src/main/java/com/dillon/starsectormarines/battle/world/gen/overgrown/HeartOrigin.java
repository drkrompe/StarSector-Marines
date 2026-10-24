package com.dillon.starsectormarines.battle.world.gen.overgrown;

import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.gen.precinct.Precinct;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Where the heart came up (`heart-origins.md`): the facility the far place is,
 * which tells without words how the bloom got onto this world. Each is a walled
 * place built from ordinary buildings, with one <b>core building</b> the first
 * mother came up against; the authored damage layer breaks that building open
 * from her side and batters the rest of the place.
 *
 * <p>No program here owes a keep, barracks, armoury or guard post: each of those
 * publishes a defender's node, and the heart's defender is the swarm.
 */
public enum HeartOrigin {

    /** Dug into it: the reservoir's pump station went too deep. The pump hall is the core. */
    WORKINGS(RoomPurpose.ENGINE_ROOM, new FortressProgram(List.of(
            new FortressBuilding(RoomPurpose.ENGINE_ROOM, RoomShape.rectangle(14, 10), Ward.REAR, 1),
            new FortressBuilding(RoomPurpose.PARTS_CAGE, RoomShape.rectangle(10, 7), Ward.YARD, 1),
            new FortressBuilding(RoomPurpose.STOCKROOM, RoomShape.rectangle(8, 6), Ward.YARD, 1),
            new FortressBuilding(RoomPurpose.CIVIC_OFFICE, RoomShape.rectangle(8, 6), Ward.FRONTAGE,
                    RoomPacker.EdgeContact.ANY, 1)), 0, 260)),

    /** It rode in on cargo: a freight terminal's warehouse is the core, its yard where the containers stood. */
    FREIGHT(RoomPurpose.LOADING_BAY, new FortressProgram(List.of(
            new FortressBuilding(RoomPurpose.LOADING_BAY, RoomShape.rectangle(18, 12), Ward.REAR, 1),
            new FortressBuilding(RoomPurpose.STOCKROOM, RoomShape.rectangle(14, 9), Ward.YARD, 2),
            new FortressBuilding(RoomPurpose.CIVIC_OFFICE, RoomShape.rectangle(8, 6), Ward.FRONTAGE,
                    RoomPacker.EdgeContact.ANY, 1)), 0, 700)),

    /** Somebody was studying it: a research compound's sealed annex is the core. */
    RESEARCH(RoomPurpose.TREATMENT_ROOM, new FortressProgram(List.of(
            new FortressBuilding(RoomPurpose.TREATMENT_ROOM, RoomShape.rectangle(11, 9), Ward.REAR, 1),
            new FortressBuilding(RoomPurpose.PHARMACY, RoomShape.rectangle(12, 9), Ward.YARD, 2),
            new FortressBuilding(RoomPurpose.SERVER_ROOM, RoomShape.rectangle(8, 6), Ward.YARD, 1),
            new FortressBuilding(RoomPurpose.CIVIC_RECEPTION, RoomShape.rectangle(10, 7), Ward.FRONTAGE,
                    RoomPacker.EdgeContact.ANY, 1)), 0, 220)),

    /**
     * Nobody's fault, the colony built over it: an open green with its café pavilion, groundskeeper's shed and toilet
     * block on a wide lawn, no wall round it. The first mother opens at the pond, not in a building.
     */
    PARK(RoomPurpose.SHOP_FLOOR, new FortressProgram(List.of(
            new FortressBuilding(RoomPurpose.SHOP_FLOOR, RoomShape.rectangle(9, 6), Ward.FRONTAGE,
                    RoomPacker.EdgeContact.ANY, 1),
            new FortressBuilding(RoomPurpose.STOCKROOM, RoomShape.rectangle(6, 5), Ward.YARD, 1),
            new FortressBuilding(RoomPurpose.WASHROOM, RoomShape.rectangle(5, 4), Ward.YARD, 1)), 0, 900),
            OriginProp.POND, false);

    /** The building the first mother came up against, or the park's pavilion. */
    public final RoomPurpose core;
    /** What the place is built of. */
    public final FortressProgram program;
    /** The prop in the yard the first mother opens at instead of the core building, or {@code null}. */
    public final OriginProp opensAt;
    /** Whether the place stands inside {@link #FENCE}; the park does not. */
    public final boolean fenced;

    HeartOrigin(RoomPurpose core, FortressProgram program) {
        this(core, program, null, true);
    }

    HeartOrigin(RoomPurpose core, FortressProgram program, OriginProp opensAt, boolean fenced) {
        this.core = core;
        this.program = program;
        this.opensAt = opensAt;
        this.fenced = fenced;
    }

    /** A light fence round the facility, two gates: a works fence, not a fort's wall. */
    public static final Fortification FENCE = new Fortification(2, 60);

    private static final long SALT = 0x4F52_4947_494E_5321L;

    /**
     * The origin a country has: its seed stepped on by its form and density, so the pools' three seeds per biome show
     * three origins each and every origin stands somewhere in each form's pools.
     */
    public static HeartOrigin of(long seed, HeartCountry.Form form, HeartCountry.Density density) {
        HeartOrigin[] all = values();
        return all[(int) Math.floorMod(seed + density.ordinal() + 2L * form.ordinal(), (long) all.length)];
    }

    /** The origin a plan's far place was built as, from its program; {@code null} for a place no origin built. */
    public static HeartOrigin of(Precinct place) {
        if (place == null) return null;
        for (HeartOrigin origin : values()) if (origin.program.equals(place.program())) return origin;
        return null;
    }

    /** Cells round the far place's seed its core building is looked for in. */
    static final int CORE_SEARCH = 60;

    /**
     * The middle of the core building nearest the far place's seed, or {@code null} when the place packed none:
     * the mean of the core purpose's cells within {@link #CORE_SEARCH} of the seed that join the nearest one.
     */
    public static int[] core(CellTopology topology, HeartOrigin origin, int seedX, int seedY) {
        int width = topology.getWidth(), height = topology.getHeight();
        int bestX = -1, bestY = -1;
        long best = Long.MAX_VALUE;
        for (int y = Math.max(0, seedY - CORE_SEARCH); y <= Math.min(height - 1, seedY + CORE_SEARCH); y++) {
            for (int x = Math.max(0, seedX - CORE_SEARCH); x <= Math.min(width - 1, seedX + CORE_SEARCH); x++) {
                if (topology.getRoomPurpose(x, y) != origin.core) continue;
                long d = (long) (x - seedX) * (x - seedX) + (long) (y - seedY) * (y - seedY);
                if (d < best) { best = d; bestX = x; bestY = y; }
            }
        }
        if (bestX < 0) return null;
        int building = topology.getBuildingId(bestX, bestY);
        long sx = 0, sy = 0, n = 0;
        for (int y = Math.max(0, seedY - CORE_SEARCH); y <= Math.min(height - 1, seedY + CORE_SEARCH); y++) {
            for (int x = Math.max(0, seedX - CORE_SEARCH); x <= Math.min(width - 1, seedX + CORE_SEARCH); x++) {
                if (topology.getRoomPurpose(x, y) != origin.core || topology.getBuildingId(x, y) != building) continue;
                sx += x; sy += y; n++;
            }
        }
        return new int[]{(int) (sx / n), (int) (sy / n)};
    }

    /** The damage layer an origin is applied as. */
    public static final String DAMAGE_LAYER = "origin";
    /**
     * Radius of the collapse at the core and on the mother's side of it, of the battering kept to the core building,
     * and of the scar at her mouth.
     */
    static final double RUIN_RADIUS = 5, BATTERED_RADIUS = 8, SCAR_RADIUS = 9;
    /** Torn-down sections of the facility's fence and of its other buildings' walls, and each one's radius. */
    static final int FENCE_BREACHES = 5, WALL_BREACHES = 3;
    static final double FENCE_BREACH_RADIUS = 2.5, WALL_BREACH_RADIUS = 2;

    /**
     * The origin's damage: the core building collapsed from the side the mother came up on, a stroke from her mouth
     * into its middle, battered round it and the ground scarred at her mouth; and across the facility, weeks of the
     * swarm: torn-down sections of the fence and holes in the other buildings' walls, spread apart, while the rest of
     * the place keeps its roofs, so the ruin reads against what still stands.
     */
    public static List<DamagePlan.Stroke> damage(CellTopology topology, int[] core, int[] mouth, int seedX, int seedY,
                                                 long seed) {
        DamagePlan.Point c = new DamagePlan.Point(core[0], core[1]), m = new DamagePlan.Point(mouth[0], mouth[1]);
        List<DamagePlan.Stroke> out = new ArrayList<>(List.of(
                new DamagePlan.Stroke(DamagePlan.Mode.BATTERED, BATTERED_RADIUS, List.of(c)),
                new DamagePlan.Stroke(DamagePlan.Mode.RUIN, RUIN_RADIUS, List.of(m, c)),
                new DamagePlan.Stroke(DamagePlan.Mode.SCAR, SCAR_RADIUS, List.of(m))));
        int coreBuilding = topology.getBuildingId(core[0], core[1]);
        List<int[]> fence = new ArrayList<>(), walls = new ArrayList<>();
        int width = topology.getWidth(), height = topology.getHeight();
        for (int y = Math.max(0, seedY - CORE_SEARCH); y <= Math.min(height - 1, seedY + CORE_SEARCH); y++) {
            for (int x = Math.max(0, seedX - CORE_SEARCH); x <= Math.min(width - 1, seedX + CORE_SEARCH); x++) {
                if (!topology.isWall(x, y)) continue;
                int building = topology.getBuildingId(x, y);
                if (building == 0) fence.add(new int[]{x, y});
                else if (building != coreBuilding) walls.add(new int[]{x, y});
            }
        }
        SplittableRandom rng = new SplittableRandom(seed ^ SALT);
        for (int[] at : spread(fence, FENCE_BREACHES, rng)) {
            out.add(new DamagePlan.Stroke(DamagePlan.Mode.RUIN, FENCE_BREACH_RADIUS,
                    List.of(new DamagePlan.Point(at[0], at[1]))));
        }
        for (int[] at : spread(walls, WALL_BREACHES, rng)) {
            out.add(new DamagePlan.Stroke(DamagePlan.Mode.RUIN, WALL_BREACH_RADIUS,
                    List.of(new DamagePlan.Point(at[0], at[1]))));
        }
        return out;
    }

    /**
     * Up to {@code n} of {@code cells} spread as far apart as they go: the first at random, each next the cell
     * farthest from every one taken so far.
     */
    static List<int[]> spread(List<int[]> cells, int n, SplittableRandom rng) {
        List<int[]> out = new ArrayList<>();
        if (cells.isEmpty()) return out;
        out.add(cells.get(rng.nextInt(cells.size())));
        while (out.size() < Math.min(n, cells.size())) {
            int[] best = null;
            long bestD = -1;
            for (int[] c : cells) {
                long near = Long.MAX_VALUE;
                for (int[] t : out) near = Math.min(near, (long) (c[0] - t[0]) * (c[0] - t[0]) + (long) (c[1] - t[1]) * (c[1] - t[1]));
                if (near > bestD) { bestD = near; best = c; }
            }
            if (bestD == 0) break;
            out.add(best);
        }
        return out;
    }
}
