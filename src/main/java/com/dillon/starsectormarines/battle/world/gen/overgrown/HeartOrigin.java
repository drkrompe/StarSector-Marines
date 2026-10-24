package com.dillon.starsectormarines.battle.world.gen.overgrown;

import com.dillon.starsectormarines.battle.world.gen.damage.DamagePlan;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressBuilding;
import com.dillon.starsectormarines.battle.world.gen.fortress.FortressProgram;
import com.dillon.starsectormarines.battle.world.gen.fortress.Ward;
import com.dillon.starsectormarines.battle.world.gen.precinct.Fortification;
import com.dillon.starsectormarines.battle.world.model.CellTopology;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

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
                    RoomPacker.EdgeContact.ANY, 1)), 0, 220));

    /** The building the first mother came up against. */
    public final RoomPurpose core;
    /** What the place is built of. */
    public final FortressProgram program;

    HeartOrigin(RoomPurpose core, FortressProgram program) {
        this.core = core;
        this.program = program;
    }

    /** A light fence round the facility, two gates: a works fence, not a fort's wall. */
    public static final Fortification FENCE = new Fortification(2, 60);

    private static final long SALT = 0x4F52_4947_494E_5321L;

    /** The origin a seed's country has. */
    public static HeartOrigin of(long seed) {
        HeartOrigin[] all = values();
        return all[new SplittableRandom(seed ^ SALT).nextInt(all.length)];
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
    /** Radius of the collapse at the core and on the mother's side of it, and of the battering round the place. */
    static final double RUIN_RADIUS = 5, BATTERED_RADIUS = 16, SCAR_RADIUS = 9;

    /**
     * The origin's damage: the core building collapsed from the side the mother came up on, a stroke from her mouth
     * into its middle, the facility round it battered by weeks of the swarm, and the ground scarred at her mouth.
     */
    public static List<DamagePlan.Stroke> damage(int[] core, int[] mouth) {
        DamagePlan.Point c = new DamagePlan.Point(core[0], core[1]), m = new DamagePlan.Point(mouth[0], mouth[1]);
        return List.of(
                new DamagePlan.Stroke(DamagePlan.Mode.BATTERED, BATTERED_RADIUS, List.of(c)),
                new DamagePlan.Stroke(DamagePlan.Mode.RUIN, RUIN_RADIUS, List.of(m, c)),
                new DamagePlan.Stroke(DamagePlan.Mode.SCAR, SCAR_RADIUS, List.of(m)));
    }
}
