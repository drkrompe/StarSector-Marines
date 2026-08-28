package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.GenContext;
import com.dillon.starsectormarines.battle.world.gen.TraversalAxis;
import com.dillon.starsectormarines.battle.world.gen.fit.Doorway;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs a fortress's program into its envelope and cuts the roadways that reach
 * it.
 *
 * <p>This runs <b>before</b> any wall is drawn. A fortress whose wall is stamped
 * first can only ever enclose whatever the ordinary fill left inside it; packed
 * first, the wall has something to be drawn around, its gates can land where the
 * roadways already run out to meet them, and its kill zone is measured from real
 * structure. See {@code compound-programs.md}.
 *
 * <p>Roadways are not laid out in advance. The packer cuts a two-wide way to
 * every building that cannot otherwise be reached, through whatever the packing
 * left over, so the fortress ends up with a road network shaped by where its
 * buildings actually went. A grid ruled first would put the buildings in the
 * bays between the roads, which is the parcel-first mistake wearing a different
 * hat.
 */
public final class FortressInterior {

    /**
     * Fortress ground: buildings stand on paved interiors, roadways are stone,
     * and thresholds are marked so a doorway reads as one from outside.
     */
    private static final RoomPacker.Palette PALETTE = new RoomPacker.Palette(
            GroundKind.INDOOR, GroundKind.STONE, GroundKind.STRIPED);

    /** The open ground between the buildings: parade square, vehicle park, verge. */
    private static final GroundKind YARD = GroundKind.DIRT;

    /** Where a fortress's buildings ended up, and what it could not find room for. */
    public record Result(List<RoomPacker.Placed> placed, List<FortressBuilding> unplaced) {

        public Result {
            placed = List.copyOf(placed);
            unplaced = List.copyOf(unplaced);
        }
    }

    private FortressInterior() {}

    /**
     * Lay the program into {@code buildable}, approached from {@code axis}.
     *
     * @param muster the parade ground and approach that already exist, which the
     *               packing must leave alone and may open onto
     */
    public static Result pack(GenContext ctx, boolean[][] buildable, boolean[][] muster,
                              TraversalAxis axis, List<FortressBuilding> program) {
        Bounds bounds = Bounds.of(buildable, ctx.width, ctx.height);
        if (bounds == null) return new Result(List.of(), program);

        RoomPacker packer = new RoomPacker(ctx, buildable, muster, PALETTE);
        List<RoomPacker.Placed> placed = new ArrayList<>();
        List<FortressBuilding> unplaced = new ArrayList<>();
        for (FortressBuilding building : FortressProgram.expanded(program)) {
            RoomPacker.Request request = new RoomPacker.Request(
                    building.purpose(), building.shape(),
                    wardAffinity(bounds, axis, building.ward()), building.perimeter());
            RoomPacker.Placed room = packer.place(request, true);
            if (room == null) {
                unplaced.add(building);
            } else {
                placed.add(room);
            }
        }
        metalYard(ctx, buildable, placed);
        return new Result(placed, unplaced);
    }

    /**
     * Open every scrap of ground the packing did not build on.
     *
     * <p>Where a hull's leftovers stay solid — a void inside a ship is
     * structure, and walking through it would be walking through the vessel —
     * a fortress's leftovers are its yard. Parade ground, vehicle park, the
     * space between a magazine and the wall: all of it is ground people cross,
     * and leaving it closed would produce a fortress that is mostly corridor
     * and cannot be fought through.
     *
     * <p>This is also what makes the roadways read as roadways. The packer cuts
     * them because a building had to be reached; against solid ground they are
     * the only way through, which is a warren. Against open yard they are the
     * made-up routes across it, and a squad can leave one and cross open ground
     * under fire — which is the choice a fortress assault should be about.
     */
    private static void metalYard(GenContext ctx, boolean[][] buildable,
                                  List<RoomPacker.Placed> placed) {
        boolean[][] structure = new boolean[ctx.width][ctx.height];
        for (RoomPacker.Placed room : placed) {
            for (int[] cell : room.shape().wall()) {
                int x = room.x() + cell[0];
                int y = room.y() + cell[1];
                if (x >= 0 && y >= 0 && x < ctx.width && y < ctx.height) structure[x][y] = true;
            }
        }
        // A doorway is cut through the ring, so it is not structure however the
        // ring was stamped; opening the yard must not close a building's door.
        for (RoomPacker.Placed room : placed) {
            for (Doorway door : room.doors()) {
                if (door.x() >= 0 && door.y() >= 0
                        && door.x() < ctx.width && door.y() < ctx.height) {
                    structure[door.x()][door.y()] = false;
                }
            }
        }
        for (int x = 0; x < ctx.width; x++) {
            for (int y = 0; y < ctx.height; y++) {
                if (!buildable[x][y] || structure[x][y] || ctx.grid.isWalkable(x, y)) continue;
                ctx.grid.setWalkableFloor(x, y);
                ctx.topology.setGroundKind(x, y, YARD);
                ctx.topology.setRoomPurpose(x, y, RoomPurpose.GENERIC);
            }
        }
    }

    /**
     * Whether a position sits at the depth this ward means, measured along the
     * attacker's approach.
     *
     * <p>Thirds rather than a distance falloff. A building is near the wall, in
     * the middle, or at the back, and the packer's affinity is a yes-or-no
     * anyway — grading it would be precision the packing cannot honour once
     * shapes start wedging against each other.
     */
    private static RoomPacker.Affinity wardAffinity(Bounds bounds, TraversalAxis axis, Ward ward) {
        return (centreX, centreY) -> depthBand(bounds, axis, centreX, centreY) == ward;
    }

    /** Which third of the approach this cell falls in, with FRONTAGE nearest the attacker. */
    private static Ward depthBand(Bounds bounds, TraversalAxis axis, int x, int y) {
        // The attacker enters at the low end of the traversal axis, so distance
        // from that end is distance from the wall they will breach.
        int span = axis == TraversalAxis.SOUTH_TO_NORTH ? bounds.height() : bounds.width();
        int from = axis == TraversalAxis.SOUTH_TO_NORTH ? y - bounds.top() : x - bounds.left();
        if (span <= 0) return Ward.YARD;
        int third = from * 3 / span;
        return third <= 0 ? Ward.FRONTAGE : third == 1 ? Ward.YARD : Ward.REAR;
    }

    /** The extent of the buildable envelope, so wards are thirds of the fortress. */
    private record Bounds(int left, int top, int right, int bottom) {

        static Bounds of(boolean[][] mask, int width, int height) {
            int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE;
            int right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++) {
                    if (!mask[x][y]) continue;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
            return right < left ? null : new Bounds(left, top, right, bottom);
        }

        int width() {
            return right - left + 1;
        }

        int height() {
            return bottom - top + 1;
        }
    }
}
