package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.VehicleBayFitting;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;

/**
 * What a fortress owes, as buildings with authored footprints.
 *
 * <p>The program sizes the fortress; the fortress does not size the program.
 * That is the whole of the inversion — a district with a wall round it comes
 * out of asking what fits, and a fortress comes out of asking what a garrison
 * needs and then finding room for it.
 *
 * <p>Footprints are stated in cells because that is what makes them arguable. A
 * vehicle shed is {@value #SHED_BAYS} bays to a rank and two ranks deep, derived
 * from the bay module rather than chosen, so it changes when the module does.
 * A magazine is squat because it is mostly wall. A barrack block is a slab
 * because bunks rank along a passage. None of these are the rectangle a
 * partition happened to leave.
 */
public final class FortressProgram {

    private FortressProgram() {}

    /** Machine berths per rank. Three out of one door is a motor pool, not a garage. */
    private static final int SHED_BAYS = 3;

    /**
     * A vehicle shed sized from the bay module rather than guessed at.
     *
     * <p>The arrangement is the deck's: two ranks of bays facing across a
     * service lane, a vestibule at the door end and a shop at the other. Its
     * dimensions therefore have to be the ones that arrangement needs, and
     * stating them as arithmetic over the module is what keeps that true — a
     * shed picked to look about right came out fifteen by nine, which the
     * fitting could only answer with a single three-deep bay before the fill was
     * rolled back for sealing itself. The room has to be able to hold what it is
     * for.
     */
    private static final RoomShape VEHICLE_SHED = RoomShape.rectangle(
            VehicleBayFitting.VESTIBULE
                    + SHED_BAYS * VehicleBayFitting.BAY_WIDTH
                    + (SHED_BAYS - 1) * VehicleBayFitting.BAY_GAP
                    + VehicleBayFitting.SHOP_WIDTH,
            2 * VehicleBayFitting.BAY_DEPTH + VehicleBayFitting.SERVICE_LANE);

    /**
     * The keep. An L rather than a slab, because the inner corner is what gives
     * the last stand somewhere to be fought over instead of one room to rush.
     */
    private static final RoomShape KEEP = RoomShape.of(
            "############",
            "############",
            "############",
            "#######     ",
            "#######     ",
            "#######     ");

    private static final RoomShape MAGAZINE = RoomShape.rectangle(9, 7);
    private static final RoomShape BARRACK_BLOCK = RoomShape.rectangle(16, 6);
    private static final RoomShape WORKSHOP = RoomShape.rectangle(10, 8);
    private static final RoomShape GENERATOR_HALL = RoomShape.rectangle(8, 8);
    private static final RoomShape STORES = RoomShape.rectangle(12, 5);
    private static final RoomShape MESS = RoomShape.rectangle(11, 7);
    private static final RoomShape GATEHOUSE = RoomShape.of(
            "#########",
            "###   ###",
            "###   ###",
            "#########");
    private static final RoomShape GUARD_POST = RoomShape.rectangle(4, 4);

    /**
     * A garrison fortress: what the place is for, in the order it matters.
     *
     * <p>The gatehouse and the vehicle sheds carry a perimeter requirement
     * because for them a placement that fits is still wrong — a gatehouse in the
     * middle of the yard is a building, and a shed with no way out is a shed.
     * Everything else only needs to fit somewhere it belongs.
     */
    public static List<FortressBuilding> garrison() {
        return List.of(
                new FortressBuilding(RoomPurpose.KEEP_THRONE, KEEP, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.ARMORY, MAGAZINE, Ward.REAR, 2),
                new FortressBuilding(RoomPurpose.ENGINE_ROOM, GENERATOR_HALL, Ward.REAR, 1),
                // One shed, not two. Two of them at the size the bay arrangement
                // needs widened the ward until it swallowed the road a defender
                // convoy commits along, and six berths under one roof is a motor
                // pool already.
                new FortressBuilding(RoomPurpose.VEHICLE_BAY, VEHICLE_SHED, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 1),
                new FortressBuilding(RoomPurpose.KEEP_ENTRY, GATEHOUSE, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 4),
                new FortressBuilding(RoomPurpose.BARRACKS, BARRACK_BLOCK, Ward.YARD, 3),
                new FortressBuilding(RoomPurpose.MESS_HALL, MESS, Ward.YARD, 1),
                new FortressBuilding(RoomPurpose.PARTS_CAGE, WORKSHOP, Ward.YARD, 2),
                new FortressBuilding(RoomPurpose.STOCKROOM, STORES, Ward.YARD, 2));
    }

    /**
     * The same fortress built around a citadel that already exists.
     *
     * <p>A conquest map seeds one canonical military compound in the fortress
     * band, and that compound carries the mission's command post. Packing a keep
     * as well would give the place two, so the ward program is the garrison
     * program with its own keep taken out: the compound is the keep, and this is
     * what stands around it.
     */
    public static List<FortressBuilding> ward() {
        List<FortressBuilding> out = new ArrayList<>();
        for (FortressBuilding building : garrison()) {
            if (building.purpose() != RoomPurpose.KEEP_THRONE) out.add(building);
        }
        return List.copyOf(out);
    }

    /** Cells of building floor the program needs, walls and roadways excluded. */
    public static int floorArea(List<FortressBuilding> program) {
        int area = 0;
        for (FortressBuilding building : program) area += building.area() * building.count();
        return area;
    }

    /**
     * How much ground a program needs, including everything packing will add
     * around it.
     *
     * <p>The program sizes the fortress. Handed an envelope chosen independently
     * of it, the packer wedges every building against the boundary — that is
     * what its score rewards, and the boundary is the only solid thing there is
     * to start with — and leaves a void in the middle that no amount of tuning
     * fills. A fortress is as big as what it holds.
     *
     * <p>The multiplier is measured, not guessed. Swept over eight seeds with
     * the garrison program (1256 cells of building floor), {@value #SLACK} is
     * the tightest ground that packs every building on every seed: at 2.0 a
     * building is left over, and at 1.8 and below eight to ten are. It is also
     * the least wasteful of the ratios that do pack — the largest untouched
     * square grows from 14 cells at this ratio to 25 at 3.0, which is the void
     * reappearing. It has to cover the wall ring each building carries, the
     * two-wide roadways cut between them, and the slivers packing always
     * strands.
     */
    public static int envelopeArea(List<FortressBuilding> program) {
        return Math.round(floorArea(program) * SLACK);
    }

    /** Ground per cell of building floor. Measured, not guessed — see {@link #envelopeArea}. */
    private static final float SLACK = 2.2f;

    /**
     * The program flattened to one entry per building, largest first.
     *
     * <p>Largest first because the buildings that cannot go just anywhere need
     * their pick before the small ones fill in around them. Ties break on
     * purpose name so the order is a property of the program rather than of the
     * iteration that built it.
     */
    public static List<FortressBuilding> expanded(List<FortressBuilding> program) {
        List<FortressBuilding> out = new ArrayList<>();
        for (FortressBuilding building : program) {
            for (int i = 0; i < building.count(); i++) out.add(building);
        }
        out.sort((a, b) -> {
            int byArea = Integer.compare(b.area(), a.area());
            return byArea != 0 ? byArea : a.purpose().name().compareTo(b.purpose().name());
        });
        return out;
    }
}
