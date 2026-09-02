package com.dillon.starsectormarines.battle.world.gen.fortress;

import com.dillon.starsectormarines.battle.world.gen.fit.RoomPacker;
import com.dillon.starsectormarines.battle.world.gen.fit.RoomShape;
import com.dillon.starsectormarines.battle.world.gen.fit.VehicleBayFitting;
import com.dillon.starsectormarines.battle.world.gen.AirbaseLot;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

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
public record FortressProgram(List<FortressBuilding> buildings, int airfields, int apron) {

    public FortressProgram {
        buildings = List.copyOf(buildings);
        if (airfields < 0) {
            throw new IllegalArgumentException("a fortress cannot owe " + airfields + " airfields");
        }
        if (apron < 0) {
            throw new IllegalArgumentException("a place cannot owe " + apron + " cells of apron");
        }
    }

    /** A program that owes buildings and airfields but no open ground of its own. */
    public FortressProgram(List<FortressBuilding> buildings, int airfields) {
        this(buildings, airfields, 0);
    }

    /**
     * How many airfields a garrison keeps unless a mission says otherwise. One
     * is the shipped answer and is a default rather than a law: a depot has
     * none and a forward base may have two, and both are the same generator
     * asked a different question.
     */
    private static final int GARRISON_AIRFIELDS = 1;

    /**
     * The same program with a different count of one purpose. Zero removes it —
     * {@link #expanded()} simply stops emitting it — which is how a mission
     * orders a fortress with no motor pool rather than a fortress whose motor
     * pool failed to place.
     */
    public FortressProgram with(RoomPurpose purpose, int count) {
        if (count < 0) throw new IllegalArgumentException("count " + count + " for " + purpose);
        List<FortressBuilding> out = new ArrayList<>();
        boolean seen = false;
        for (FortressBuilding building : buildings) {
            if (building.purpose() != purpose) {
                out.add(building);
                continue;
            }
            seen = true;
            if (count > 0) {
                out.add(new FortressBuilding(building.purpose(), building.shape(),
                        building.ward(), building.perimeter(), count));
            }
        }
        if (!seen) {
            throw new IllegalArgumentException(
                    "this program has no " + purpose + " to set a count for");
        }
        return new FortressProgram(out, airfields, apron);
    }

    /** The same program owing a different number of airfields; zero is a fortress without one. */
    public FortressProgram withAirfields(int count) {
        return new FortressProgram(buildings, count, apron);
    }

    /**
     * The same program owing a different amount of open ground.
     *
     * <p>How a landing place is trimmed to a map it does not fit on. The
     * {@link #FIT_LADDER} cannot do it: apron is not a building count, and a
     * beachhead with its apron taken away is not a smaller beachhead but a
     * place with nowhere to put a shuttle down. Whoever authors the program is
     * the one that knows what the map can spare.
     */
    public FortressProgram withApron(int cells) {
        return new FortressProgram(buildings, airfields, cells);
    }

    /**
     * What this garrison gives up, in order, when the ground will not take all
     * of it.
     *
     * <p>Ordered by what a garrison can least afford to lose. The airfield goes
     * first because a lot is by some way the single largest item and an air arm
     * is the one part of an installation that can simply be elsewhere. Then the
     * counts come down: sleeping space, then guard posts, then stores, then
     * magazines, then sleeping space again. What is never on this ladder is the
     * keep, the gatehouse, the vehicle shed and the mess hall — a place with
     * none of those is not a garrison, and shrinking it into one would be worse
     * than handing the packer more than it can seat.
     */
    private static final List<UnaryOperator<FortressProgram>> FIT_LADDER = List.of(
            program -> program.airfields > 0 ? program.withAirfields(0) : program,
            program -> program.reducedTo(RoomPurpose.BARRACKS, 2),
            program -> program.reducedTo(RoomPurpose.CONTROL_ROOM, 2),
            program -> program.reducedTo(RoomPurpose.STOCKROOM, 1)
                    .reducedTo(RoomPurpose.PARTS_CAGE, 1),
            program -> program.reducedTo(RoomPurpose.ARMORY, 1),
            program -> program.reducedTo(RoomPurpose.BARRACKS, 1));

    /**
     * The largest version of this program whose envelope fits in
     * {@code groundBudget}, or the smallest the ladder can reach.
     *
     * <p>A program is authored for the installation rather than for the map, so
     * a garrison sized for a landing zone will not fit a skirmish map: the
     * garrison envelope is around five thousand cells against a 144x80 map's
     * eleven and a half thousand, and a place that takes half the map leaves
     * nothing to approach it through.
     *
     * <p>Going over budget at the end of the ladder is allowed rather than
     * refused. The packer already records what it could not place under
     * {@code BspKeys.UNPLACED_PROGRAM}, so a cramped installation comes out
     * short and says so, where a refusal would come out as no map at all.
     */
    public FortressProgram fittedTo(int groundBudget) {
        FortressProgram fitted = this;
        for (UnaryOperator<FortressProgram> step : FIT_LADDER) {
            if (fitted.envelopeArea() <= groundBudget) return fitted;
            fitted = step.apply(fitted);
        }
        return fitted;
    }

    /**
     * The same program owing at most {@code count} of {@code purpose}, and
     * unchanged where it already owes that many or fewer.
     *
     * <p>A rung must never raise a count: a mission that ordered one barrack
     * block has said so, and a ladder that is trimming the program is the last
     * thing that should hand it a second. A purpose the program does not owe at
     * all is skipped rather than added, because {@link #with} rejects one and
     * this is a trim rather than an order.
     */
    private FortressProgram reducedTo(RoomPurpose purpose, int count) {
        return countOf(purpose) > count ? with(purpose, count) : this;
    }

    /** How many of {@code purpose} this program owes; zero when it owes none. */
    public int countOf(RoomPurpose purpose) {
        for (FortressBuilding building : buildings) {
            if (building.purpose() == purpose) return building.count();
        }
        return 0;
    }

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
    public static FortressProgram garrison() {
        return new FortressProgram(List.of(
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
                new FortressBuilding(RoomPurpose.STOCKROOM, STORES, Ward.YARD, 2)),
                GARRISON_AIRFIELDS);
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
    public static FortressProgram ward() {
        return garrison().with(RoomPurpose.KEEP_THRONE, 0);
    }

    /**
     * A post on the way to somewhere else: something a squad holds and a
     * company clears.
     *
     * <p>Two guard posts, a barrack block and a store. For a battle that is one
     * compound — the barrack block — with a couple of manned positions around
     * it and a depot worth walking into, standing on about four hundred cells.
     * It is the smallest thing that is still a <em>place</em> rather than a
     * scatter of emplacements: somebody sleeps here, so somebody is here.
     *
     * <p><b>No keep and no airfield</b>, and neither is an omission. The keep is
     * the map's one canonical command post and belongs to the objective; an air
     * arm is an installation's, not a picket's, and a lot is by some way the
     * largest thing a program can order.
     */
    public static FortressProgram outpost() {
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 2),
                new FortressBuilding(RoomPurpose.BARRACKS, BARRACK_BLOCK, Ward.YARD, 1),
                new FortressBuilding(RoomPurpose.STOCKROOM, STORES, Ward.YARD, 1)),
                0);
    }

    /**
     * Something a track has to stop for.
     *
     * <p>A gatehouse, three guard posts — two watching the way in and the one
     * the position is run from — two barrack blocks and an armoury, on about
     * eight hundred cells. For a battle that is three compounds rather than
     * one, so taking it is a sequence and not a rush, and the armoury is a
     * prize on top of being a supply hub.
     *
     * <p>Same two exclusions as {@link #outpost()} and for the same reasons.
     * What separates the two is depth: an outpost is a position, a strongpoint
     * is a position that has to be reduced.
     */
    public static FortressProgram strongpoint() {
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.ARMORY, MAGAZINE, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.KEEP_ENTRY, GATEHOUSE, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, GUARD_POST, Ward.FRONTAGE,
                        RoomPacker.EdgeContact.ANY, 3),
                new FortressBuilding(RoomPurpose.BARRACKS, BARRACK_BLOCK, Ward.YARD, 2)),
                0);
    }

    /**
     * How much open ground a landing place owes before anything is built on it.
     *
     * <p>The berths are the reason. An arrival area is thirteen cells across
     * and five deep, and a Conquest asks for three of them sixteen cells apart,
     * so the beachhead needs something like seventy cells of frontage with room
     * to scan inward behind it. A compact claim of this many cells comes out
     * roughly seventy across, which seats them with the fringe to spare that an
     * irregular outline needs. It is a first guess to be measured, like
     * {@code PrecinctPlan.FIT}, and the number to move when a beachhead comes
     * out too tight to seat its areas.
     */
    public static final int LANDING_APRON = 3800;

    /**
     * A civil spaceport: a terminal, a hangar, a control office and a fuel yard
     * standing back from an open apron.
     *
     * <p>The room vocabulary is the stock recipe's spaceport, said as a
     * program rather than as a parcel fill — a berth, the office that runs it,
     * somewhere to work an aircraft, and the cargo and fuel that arrive with
     * it. Every building sits in the {@link Ward#REAR} band so the apron stays
     * open at the approach end, which is the end shuttles come in over.
     *
     * <p>No keep, so the one-keep law is untouched, and no airfield: the ground
     * the shuttles use is the apron, and a lot would put a runway and a fence
     * across it.
     */
    public static FortressProgram spaceport() {
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.CIVIC_RECEPTION, TERMINAL, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.HANGAR, LANDING_HANGAR, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, CONTROL_OFFICE, Ward.REAR, 1),
                new FortressBuilding(RoomPurpose.STOCKROOM, FUEL_YARD, Ward.REAR, 1)),
                0, LANDING_APRON);
    }

    /** An apron with one hut on it: what an off-grid settlement is supplied through. */
    public static FortressProgram landingStrip() {
        return new FortressProgram(List.of(
                new FortressBuilding(RoomPurpose.CONTROL_ROOM, FIELD_HUT, Ward.REAR, 1)),
                0, LANDING_APRON);
    }

    /** Bare ground. It still claims the apron, because the berths stand on it. */
    public static FortressProgram landingField() {
        return new FortressProgram(List.of(), 0, LANDING_APRON);
    }

    private static final RoomShape TERMINAL = RoomShape.rectangle(14, 8);
    private static final RoomShape LANDING_HANGAR = RoomShape.rectangle(12, 10);
    private static final RoomShape CONTROL_OFFICE = RoomShape.rectangle(6, 5);
    private static final RoomShape FUEL_YARD = RoomShape.rectangle(8, 6);
    private static final RoomShape FIELD_HUT = RoomShape.rectangle(5, 4);

    /** Cells of building floor the program needs, walls and roadways excluded. */
    public int floorArea() {
        int area = 0;
        for (FortressBuilding building : buildings) area += building.area() * building.count();
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
     * the garrison program, {@value #SLACK} is the tightest ground that packs
     * every building on every seed: at 2.2 seven buildings are left over across
     * the sweep and at 2.0 eight are. It is also the least wasteful of the
     * ratios that do pack — the largest untouched square stays at 17 cells here
     * and at 2.6, and grows to 23 at 2.8 and 25 at 3.0, which is the void
     * reappearing. It has to cover the wall ring each building carries, the
     * two-wide roadways cut between them, the yard the ward's massing keeps
     * between neighbours, and the slivers packing always strands.
     *
     * <p>It was re-measured when that massing arrived. Buildings held a cell
     * apart need more ground than buildings chained into a slab, and the ratio
     * that was tightest for the slab left a building homeless once they were
     * spaced: a sizing rule measured under one packing policy does not survive
     * a change to it.
     */
    public int envelopeArea() {
        // The airbase lot is ground the ward holds but no building stands on,
        // so it is added rather than scaled: the slack covers what packing
        // wastes around buildings, and a facility is not waste. The lot is
        // reserved out of the ward before packing, so this is what makes sure
        // the ward is sized to afford it.
        // The apron is added rather than scaled for the same reason the lot is:
        // it is ground the place holds and nothing stands on, so the packing
        // slack has nothing to say about it.
        return buildingGround() + airfields * AirbaseLot.area(AirbaseLot.Size.STATION) + apron;
    }

    /**
     * The ground this program's buildings need to pack, before anything else in
     * the ward is allowed to claim any. A host reserving a lot inside the ward
     * checks what it wants to take against this: below it, buildings start
     * going unplaced.
     */
    public int buildingGround() {
        return Math.round(floorArea() * SLACK);
    }

    /** Ground per cell of building floor. Measured, not guessed — see {@link #envelopeArea}. */
    private static final float SLACK = 2.4f;

    /**
     * The program flattened to one entry per building, largest first.
     *
     * <p>Largest first because the buildings that cannot go just anywhere need
     * their pick before the small ones fill in around them. Ties break on
     * purpose name so the order is a property of the program rather than of the
     * iteration that built it.
     */
    public List<FortressBuilding> expanded() {
        List<FortressBuilding> out = new ArrayList<>();
        for (FortressBuilding building : buildings) {
            for (int i = 0; i < building.count(); i++) out.add(building);
        }
        out.sort((a, b) -> {
            int byArea = Integer.compare(b.area(), a.area());
            return byArea != 0 ? byArea : a.purpose().name().compareTo(b.purpose().name());
        });
        return out;
    }
}
