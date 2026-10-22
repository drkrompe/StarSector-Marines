package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.fit.layout.LayoutOp;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.List;

/**
 * A sick bay as a ward flanked by the things that make it clinical, either side
 * of a stretcher-width spine.
 *
 * <p>A ward is not a dormitory. Ranked like a berth it is furnished honestly and
 * reads as one anyway, because a bed with nothing beside it but another bed is a
 * bunk — a ward bed is somewhere <em>checked</em>, and what says so is the
 * monitor at its head, the gap a stretcher needs at its foot, and a clinical end
 * to the room that is not more beds. So this fitting keeps three things apart
 * that a plain rank collapses into one: the ward itself, a treatment station
 * distinct from any bed, and a dispensary secured behind its own counter — with
 * the medic's own station, where the ward is actually written up, at the
 * dispensary rather than floating in the beds.
 *
 * <p>The room divides along its length. The near end — {@link #CLINICAL_DEPTH}
 * columns — is the clinic: a treatment table against one bulkhead and a
 * dispensary against the other, both worked from a lane that joins them to the
 * spine. Everything aft of that is the ward: bed bays ranked either side of the
 * same spine, one column of bed at a pitch that always leaves the next column a
 * genuinely clear gap — reserved before a single bed goes down, the way every
 * lane in this package is, so a stretcher has somewhere to stand beside a bed
 * that later furniture can never take back.
 *
 * <p>A bed bay is itself two fixtures, not one: the monitor stack against the
 * bulkhead, and the bed reaching in from it toward the spine. Publishing
 * {@link Affordance#TREAT} on the bed rather than the monitor is the same
 * choice {@code AisleFitting}'s groups make elsewhere — the work is at the
 * fixture that is actually worked, and a monitor with nobody's job on it is
 * honestly just part of the picture.
 *
 * <p>Every id here is asked of the {@link TileRegistry} before it is used, and a
 * preferred medical id that is not installed yet falls back to an existing
 * fixture that already plays the same structural role — a residential bed head
 * for the ward bed, the washroom's own fixture for the sink. A fitting that
 * named an id the registry does not have would furnish the room with nothing and
 * say why to nobody; this one is a sick bay in either art era.
 */
public final class SickBayFitting implements RoomFitting {

    private final boolean shipboard;
    private static final String SHIP_KIT = "sickbay-clinic.";

    public SickBayFitting() { this(false); }
    private SickBayFitting(boolean shipboard) { this.shipboard = shipboard; }
    public static SickBayFitting shipboard() { return new SickBayFitting(true); }

    /** Columns at the near end given over to the clinic rather than the ward. */
    private static final int CLINICAL_DEPTH = 4;
    /** Rows either bulkhead band is deep. Bed plus its monitor, or the clinic's working row. */
    private static final int BAND = 3;
    /** Rows of the central spine — wide enough that a stretcher and a walker pass each other. */
    private static final int AISLE_WIDTH = 4;

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.PATIENT_WARD;
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();
        if (along < CLINICAL_DEPTH + 2 || across < 2 * BAND + AISLE_WIDTH) return;

        Art art = shipboard ? null : Art.resolve();
        int aisleFrom = BAND;

        if (shipboard) {
            paint(floor, 0, 0, along, across, "clinical");
            paint(floor, 0, aisleFrom, along, AISLE_WIDTH, "clinical-aisle");
        }

        // Circulation before anything else stands on it: the spine the whole
        // room hangs off, then a stub from every hatch the deck actually cut,
        // wherever on the bulkhead ring that turned out to be.
        reserve(floor, 0, aisleFrom, along, AISLE_WIDTH);
        for (Doorway door : floor.localDoors()) {
            stubFromDoor(floor, door, aisleFrom, AISLE_WIDTH, along, across);
        }

        layClinic(floor, art, across);
        layWard(floor, art, along, across);
    }

    /**
     * The near end: a treatment station against the north bulkhead, a
     * dispensary and the medic's own desk against the south one, each worked
     * from a row abreast of the spine.
     */
    private void layClinic(RoomFloor floor, Art art, int across) {
        int northApproach = BAND - 1;
        int southApproach = across - BAND;
        int southFar = across - BAND + 1;

        // The lanes that join the clinic to the spine, reserved before either
        // flank is furnished — and on both sides they are the row that *touches*
        // the spine, with the working row outboard of them. Put the approach
        // beyond its own furniture instead and it is a lane enclosed on all four
        // sides: the working row inboard of it, the bulkhead bank outboard, and
        // the ward's first bed closing the end. The room still furnishes and
        // still publishes, and then fails its own connectivity check and ships
        // as bare deck.
        reserve(floor, 0, northApproach, CLINICAL_DEPTH, 1);
        reserve(floor, 0, southApproach, CLINICAL_DEPTH, 1);

        if (shipboard) {
            // Put the staffed bank on the flank whose desk footprint survives the hatches.
            int[] desk = floor.toLocalRect(1, southFar, 2, 1);
            boolean dispensaryLow = !floor.isFree(desk[0], desk[1], desk[2], desk[3]);
            int storeRow = dispensaryLow ? 0 : across - 1;
            int workRow = dispensaryLow ? 1 : southFar;
            int approach = dispensaryLow ? northApproach : southApproach;
            clinicPiece(floor, "cabinet", 0, storeRow, null, 0, 0);
            clinicPiece(floor, "cabinet", 2, storeRow, null, 0, 0);
            clinicPiece(floor, "trolley", 0, workRow, Affordance.STOW, 0, approach);
            clinicPiece(floor, "station", 1, workRow, Affordance.TREAT, 1, approach);
            clinicPiece(floor, "sink", 3, workRow, Affordance.WASH, 3, approach);

            int examRow = dispensaryLow ? across - 1 : 0;
            int examApproach = dispensaryLow ? across - 2 : 1;
            boolean examAtStart = clinicPiece(floor, "exam", 0, examRow,
                    Affordance.TREAT, 1, examApproach);
            if (!examAtStart) clinicPiece(floor, "exam", 2, examRow,
                    Affordance.TREAT, 2, examApproach);
            clinicPiece(floor, "trolley", examAtStart ? 2 : 0, examRow, null, 0, 0);
            clinicPiece(floor, "screen", 3, dispensaryLow ? across - 2 : 0, null, 0, 0);
            return;
        }

        // Treatment: the table against the bulkhead, its trolley beside it, and
        // the screen that keeps a patient on it out of the ward's sightline.
        // A second piece against the bulkhead — the ready shelf — is what turns
        // the flank into a worked corner rather than one prop and a wall.
        place(floor, art.table(), 0, 0, 2, 1, Affordance.TREAT);
        place(floor, art.trolley(), 2, 0, 1, 1, null);
        place(floor, art.screen(), 3, 0, 1, 1, null);
        place(floor, art.shelf(), 3, 1, 1, 1, null);

        // Dispensary: the locker bank secured against the bulkhead is stock, not
        // a job — the same distinction the armoury draws between its racks and
        // its counter. The counter, the desk the ward is actually written up
        // at, and the scrub sink all work from the row that meets the spine.
        place(floor, art.locker(), 0, across - 1, 3, 1, null);
        place(floor, art.crate(), 3, across - 1, 1, 1, null);
        place(floor, art.counter(), 0, southFar, 1, 1, Affordance.STOW);
        place(floor, art.desk(), 1, southFar, 1, 1, Affordance.TREAT);
        place(floor, art.chair(), 2, southFar, 1, 1, null);
        place(floor, art.sink(), 3, southFar, 1, 1, Affordance.WASH);
    }

    /**
     * The ward: a bed and its monitor per column, ranked either side of the
     * spine at a pitch that always leaves the next column clear.
     *
     * <p>The gap is reserved <em>as lane</em> rather than merely left unfilled,
     * which is what makes it a stretcher's berth and not a strip of deck that
     * happened to be left over — the same distinction {@code RoomFloor} draws
     * everywhere else between authored circulation and furniture's leavings.
     * {@link RoomFit#gap()} still decides how tight the pitch is, but a gap of
     * zero would put the next bed against the last one, so it is never let
     * fall below one column: a poorly fitted sick bay is sloppier, not one
     * where nobody can reach the beds.
     */
    private void layWard(RoomFloor floor, Art art, int along, int across) {
        int gap = Math.max(1, floor.fit().gap());
        int pitch = 1 + gap;
        int southBedRow = across - BAND;

        for (int column = CLINICAL_DEPTH; column < along; column++) {
            if ((column - CLINICAL_DEPTH) % pitch != 0) {
                reserve(floor, column, 0, 1, BAND);
                reserve(floor, column, southBedRow, 1, BAND);
            }
        }

        if (shipboard) {
            for (int column = CLINICAL_DEPTH; column < along; column += pitch) {
                bedBay(floor, column, 0, along, across, true);
                bedBay(floor, column, southBedRow, along, across, false);
            }
            return;
        }

        int[] headNorth = floor.pose().mapDirection(0, -1);
        int[] headSouth = floor.pose().mapDirection(0, 1);
        String bedNorth = bedFacing(headNorth, art);
        String bedSouth = bedFacing(headSouth, art);

        for (int column = CLINICAL_DEPTH; column < along; column += pitch) {
            place(floor, art.monitor(), column, 0, 1, 1, null);
            place(floor, bedNorth, column, 1, 1, 2, Affordance.TREAT);
            place(floor, art.monitor(), column, across - 1, 1, 1, null);
            place(floor, bedSouth, column, southBedRow, 1, 2, Affordance.TREAT);
        }
    }

    /** Check the entire bay first, so a hatch cannot leave a monitor without its bed. */
    private void bedBay(RoomFloor floor, int column, int band, int along, int across, boolean low) {
        int[] bay = floor.toLocalRect(column, band, 1, BAND);
        if (!floor.isFree(bay[0], bay[1], bay[2], bay[3])) return;
        int bedRow = low ? 1 : band;
        if (!floor.placeAssembly(column, bedRow, SHIP_KIT + "bed", List.of(
                new LayoutOp.Layer(0, 0, SHIP_KIT + (low ? "linen-low" : "linen-high"))))) return;
        floor.placeAssembly(column, low ? 0 : across - 1, SHIP_KIT + "monitor", List.of());
        int standX = column + 1 < along ? column + 1 : column;
        int standY = column + 1 < along ? bedRow : low ? BAND : band - 1;
        int targetRow = standX != column ? bedRow : low ? bedRow + 1 : bedRow;
        task(floor, standX, standY, Affordance.TREAT, column, targetRow);
    }

    private boolean clinicPiece(RoomFloor floor, String piece, int x, int y,
                             Affordance work, int standX, int standY) {
        if (!floor.placeAssembly(x, y, SHIP_KIT + piece, List.of())) return false;
        if (work != null) task(floor, standX, standY, work, x, y);
        return true;
    }

    private void paint(RoomFloor floor, int x, int y, int w, int h, String material) {
        int[] r = floor.toLocalRect(x, y, w, h);
        floor.markFloorSurface(r[0], r[1], r[2], r[3], "ship-floor-trim." + material);
    }

    private void task(RoomFloor floor, int x, int y, Affordance work, int fixtureX, int fixtureY) {
        int[] stand = floor.toLocal(x, y);
        int[] fixture = floor.toLocal(fixtureX, fixtureY);
        floor.fixtureTask(stand[0], stand[1], work, fixture[0], fixture[1]);
    }

    /** The compass-facing bed variant whose head points the way the world says this bulkhead does. */
    private static String bedFacing(int[] head, Art art) {
        if (head[0] < 0) return art.bedHeadW();
        if (head[0] > 0) return art.bedHeadE();
        if (head[1] < 0) return art.bedHeadN();
        return art.bedHeadS();
    }

    /**
     * Join one hatch to the spine, a single column wide.
     *
     * <p>A hatch that already opens onto the spine costs nothing. One that
     * lands anywhere else on the bulkhead ring — either long side, or either
     * end — costs the column it lands in, reserved from the hatch to the
     * nearest spine row. The same mechanism {@code AisleFitting} uses, because
     * a sick bay's doors are exactly as unpredictable as any other room's.
     */
    private void stubFromDoor(RoomFloor floor, Doorway door,
                              int aisleFrom, int aisleSpan, int along, int across) {
        int[] canonical = floor.toCanonical(door.x(), door.y());
        int doorAcross = canonical[1];
        if (doorAcross >= aisleFrom && doorAcross < aisleFrom + aisleSpan) return;
        int column = clamp(canonical[0], 0, along);
        int from = Math.min(Math.max(0, doorAcross), aisleFrom);
        int to = Math.max(Math.min(across - 1, doorAcross), aisleFrom + aisleSpan - 1);
        reserve(floor, column, from, 1, to - from + 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max - 1, value));
    }

    private static void reserve(RoomFloor floor,
                                int along, int across, int alongSpan, int acrossSpan) {
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }

    private static boolean place(RoomFloor floor, String id, int along, int across,
                                 int spanAlong, int spanAcross, Affordance affordance) {
        int[] rect = floor.toLocalRect(along, across, spanAlong, spanAcross);
        return affordance == null
                ? floor.place(id, rect[0], rect[1])
                : floor.place(id, rect[0], rect[1], affordance);
    }

    /**
     * Every id this fitting places, resolved once against the installed
     * registry so a room built before the medical set ships still comes out
     * furnished — with ship's ordinary furniture standing in for clinical art
     * that is not there yet, never with a name nothing answers to.
     */
    private record Art(String bedHeadN, String bedHeadS, String bedHeadE, String bedHeadW,
                       String monitor, String table, String trolley, String screen,
                       String shelf, String locker, String crate, String counter,
                       String desk, String chair, String sink) {

        static Art resolve() {
            return new Art(
                    art("doodad.medical-ward-bed-head-n", "doodad.residential-bed-head-n"),
                    art("doodad.medical-ward-bed-head-s", "doodad.residential-bed-v"),
                    art("doodad.medical-ward-bed-head-e", "doodad.residential-bed-head-e"),
                    art("doodad.medical-ward-bed-head-w", "doodad.residential-bed-h"),
                    art("doodad.medical-monitor-stack", "doodad.chest-2"),
                    art("doodad.medical-treatment-table", "doodad.office-conference-table"),
                    art("doodad.medical-supply-trolley", "doodad.industrial-control-console"),
                    art("doodad.medical-privacy-screen", "doodad.industrial-pipe-bundle"),
                    art("doodad.medical-supply-shelf", "doodad.shelf-1"),
                    art("doodad.medical-drug-locker", "doodad.industrial-crate-stack"),
                    "doodad.box",
                    art("doodad.medical-dispensary-counter", "doodad.crate"),
                    art("doodad.medical-charting-desk", "doodad.desk-2"),
                    "doodad.chair-south-yellow",
                    art("doodad.medical-scrub-sink", "doodad.box"));
        }

        private static String art(String preferred, String fallback) {
            return TileRegistry.installed().doodad(preferred) != null ? preferred : fallback;
        }
    }
}
