package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.CellTopology.GroundKind;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Gear round the bulkheads and a clear middle to use it in.
 *
 * <p>A ship's gym is the one compartment whose subject is the deck itself. The
 * racks, the machines and the kit lockers all stand against the sides, and what
 * the room is <em>for</em> is the space they are standing out of the way of. So
 * the middle is reserved before anything is placed, marked out so that its
 * emptiness reads as deliberate rather than as a fill that gave up, and matted
 * — matting is laid as floor covering rather than as furniture, so the deck
 * under it stays clear and walkable, which is the whole distinction between a
 * training floor and a store.
 *
 * <p>This is also the other room aboard where doing nothing is the point. Along
 * with the lounge it is where a watch that is neither working nor asleep
 * actually goes, and {@link Affordance#EXERCISE} is published generously for
 * that reason: a complement with nowhere to be off watch has only the passages,
 * and a deck full of people standing in passages is the failure the ambient
 * model was built to prevent.
 *
 * <p>Work is published in both of the honest places a gym has. Gear that
 * somebody uses standing at it publishes at the gear. The mats publish on open
 * deck, through {@link RoomFloor#fixtureTask}, each one facing the middle of the
 * room — a floor station has no prop to be found beside, and a search for one
 * would either fail or attach the point to whatever furniture happened to be
 * nearest, which is not what the person on the mat is doing.
 *
 * <p>Everything scales off the footprint: gear at a fixed pitch round a longer
 * bulkhead is more gear, and a wider clear deck takes more mats. Nothing here
 * records a capacity.
 */
public final class GymFitting implements RoomFitting {

    /** Cells of gear band inboard of each bulkhead. Deep enough for a machine. */
    private static final int BAND = 2;
    /** Cells between one piece of gear and the next along a bulkhead. */
    private static final int GEAR_PITCH = 3;
    /** Cells on a side of one mat. */
    private static final int MAT = 2;
    /** Cells between one mat's corner and the next. Room to swing an arm. */
    private static final int MAT_PITCH = 3;

    /** The matting laid over the training floor. Covering, not furniture. */
    private static final String MATTING = "doodad.gym-mat";

    /**
     * One piece of gear: its art, the footprint that art is drawn at, and what
     * a person does at it.
     *
     * <p>The span is stated in <b>world</b> cells because that is what the art
     * is: a rowing machine is drawn lying north-south and no amount of turning
     * the room redraws it. What the pose decides is which canonical cells that
     * world footprint covers, which is worked out at placement.
     *
     * @param affordance null for gear that is only part of the picture. A kit
     *     locker is not somewhere anybody trains, and publishing it as such
     *     would staff the gym out of its storage.
     */
    private record Gear(String id, int spanX, int spanY, Affordance affordance) {

        static Gear used(String id, int spanX, int spanY) {
            return new Gear(id, spanX, spanY, Affordance.EXERCISE);
        }

        static Gear stowage(String id) {
            return new Gear(id, 1, 1, null);
        }
    }

    /**
     * The kit, dealt round the bulkheads in order.
     *
     * <p>Mixed rather than sorted, so a wall is a rack, a locker, a machine and
     * a bag rather than a rank of one thing — the same reason the lounge deals
     * out different groups. The stowage between the machines is ordinary ship's
     * furniture, and honestly so: a chest of kit is a chest of kit whether it
     * stands in a berth or beside a weights rack.
     */
    private static final Gear[] KIT = {
            Gear.used("doodad.gym-weight-rack", 2, 1),
            Gear.stowage("doodad.gym-kit-locker"),
            Gear.used("doodad.gym-ergometer", 1, 2),
            Gear.stowage("doodad.chest-1"),
            Gear.used("doodad.gym-bench-press", 1, 2),
            Gear.stowage("doodad.shelf-3"),
            Gear.used("doodad.gym-treadmill", 1, 2),
            Gear.stowage("doodad.chest-2"),
            Gear.used("doodad.gym-weight-rack", 2, 1) };

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.GYMNASIUM;
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();
        int clearAlong = along - 2 * BAND;
        int clearAcross = across - 2 * BAND;
        // Below this there is a band of gear and no floor to use it on, which is
        // a store with a rowing machine in it rather than a gym.
        if (clearAlong < MAT || clearAcross < MAT) return;

        // The middle first, so nothing placed afterwards can encroach on the one
        // thing this compartment exists to provide.
        reserve(floor, BAND, BAND, clearAlong, clearAcross);
        for (Doorway door : floor.localDoors()) {
            floor.reserveLane(door.x() - 1, door.y() - 1, 3, 3);
        }
        mark(floor, BAND, BAND, clearAlong, clearAcross);

        layMats(floor, along, across);
        layGear(floor, installedKit(), along, across);
    }

    /**
     * The kit, less anything the registry has no art for.
     *
     * <p>Filtered up front rather than skipped at each slot, because the two are
     * not the same: a piece that cannot be placed <em>here</em> should hold its
     * turn so the run resumes with it, and a piece that does not exist at all
     * would then hold its turn forever and the gym would come out bare. The
     * distinction cost this room every one of its fittings the first time it was
     * run against a registry the gym art had not reached yet.
     *
     * <p>Leaving the room to fall back on ship's furniture is deliberate rather
     * than a graceful failure. A chest of kit against a bulkhead is a chest of
     * kit, and a gym is honestly a clear deck with lockers round it; what would
     * not be honest is a crate standing in for a weights rack under a hopeful
     * name.
     */
    private static List<Gear> installedKit() {
        List<Gear> kit = new ArrayList<>(KIT.length);
        for (Gear gear : KIT) {
            if (TileRegistry.installed().doodad(gear.id()) != null) kit.add(gear);
        }
        return kit;
    }

    /**
     * The matting, and a training station standing on each mat looking inboard.
     *
     * <p>Laid before the gear, because covering recorded after a fixture is
     * covering drawn over it.
     *
     * <p>Every station faces the same middle rather than its own mat. A gym in
     * use is a dozen people working into the same clear deck, and pointing each
     * of them at the two-cell square under their own feet would arrange them as
     * a dozen unrelated tasks that happen to share a room.
     */
    private void layMats(RoomFloor floor, int along, int across) {
        int middleAlong = BAND + (along - 2 * BAND) / 2;
        int middleAcross = BAND + (across - 2 * BAND) / 2;
        int[] middle = floor.toLocal(middleAlong, middleAcross);
        for (int a = BAND; a + MAT <= along - BAND; a += MAT_PITCH) {
            for (int c = BAND; c + MAT <= across - BAND; c += MAT_PITCH) {
                int[] rect = floor.toLocalRect(a, c, MAT, MAT);
                floor.pave(rect[0], rect[1], MATTING);
                floor.fixtureTask(rect[0], rect[1], Affordance.EXERCISE,
                        middle[0], middle[1]);
            }
        }
    }

    /**
     * The gear, run round all four bulkheads.
     *
     * <p>The ends are worked wall to wall and the sides only between them, so
     * the corners belong to one run rather than being contested by two — a
     * second run reaching into a corner places nothing there and merely shifts
     * everything after it out of pitch.
     */
    private void layGear(RoomFloor floor, List<Gear> kit, int along, int across) {
        if (kit.isEmpty()) return;
        int dealt = 0;
        for (int a = 0; a < along; a += GEAR_PITCH) {
            dealt = place(floor, kit, a, 0, 0, 1, dealt);
            dealt = place(floor, kit, a, across - 1, 0, -1, dealt);
        }
        for (int c = BAND; c < across - BAND; c += GEAR_PITCH) {
            dealt = place(floor, kit, 0, c, 1, 0, dealt);
            dealt = place(floor, kit, along - 1, c, -1, 0, dealt);
        }
    }

    /**
     * One piece of gear standing against a bulkhead and reaching inboard.
     *
     * <p>The authored cell is the one touching the bulkhead, and the piece grows
     * from it towards the middle. Which corner of its footprint that makes the
     * origin depends on which bulkhead this is, so the inboard direction is
     * carried in rather than assumed: gear on the far side of the room is
     * anchored at its inboard cell and reaches back to the wall.
     *
     * @return the next position in the kit rotation, advanced only when
     *     something actually went down, so a run that meets a hatch resumes
     *     where it left off instead of skipping a piece
     */
    private int place(RoomFloor floor, List<Gear> kit, int along, int across,
                      int inboardAlong, int inboardAcross, int dealt) {
        Gear gear = kit.get(dealt % kit.size());
        boolean upright = floor.pose().upright();
        int spanAlong = upright ? gear.spanX() : gear.spanY();
        int spanAcross = upright ? gear.spanY() : gear.spanX();

        int cornerAlong = inboardAlong < 0 ? along - spanAlong + 1 : along;
        int cornerAcross = inboardAcross < 0 ? across - spanAcross + 1 : across;
        if (cornerAlong < 0 || cornerAcross < 0) return dealt;
        int[] rect = floor.toLocalRect(cornerAlong, cornerAcross, spanAlong, spanAcross);
        boolean laid = gear.affordance() == null
                ? floor.place(gear.id(), rect[0], rect[1])
                : floor.place(gear.id(), rect[0], rect[1], gear.affordance());
        return laid ? dealt + 1 : dealt;
    }

    /**
     * Mark the training floor.
     *
     * <p>Law 10 asks an open region to be circulation or to carry a stated
     * reason for being open, and a gym's deck is the reason. Painting it says
     * so: an unmarked clear rectangle in the middle of a furnished compartment
     * reads as a fill that ran out of ideas, and the same rectangle with a floor
     * on it reads as the room.
     */
    private void mark(RoomFloor floor, int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.markGround(rect[0], rect[1], rect[2], rect[3], GroundKind.TILE);
    }

    private void reserve(RoomFloor floor,
                         int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
