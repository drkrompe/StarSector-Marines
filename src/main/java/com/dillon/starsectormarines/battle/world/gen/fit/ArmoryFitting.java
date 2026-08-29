package com.dillon.starsectormarines.battle.world.gen.fit;

import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.model.RoomPurpose;
import com.dillon.starsectormarines.battle.world.tiles.DoodadDef;
import com.dillon.starsectormarines.battle.world.tiles.TileRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * An armoury as a counter with a workshop behind it, not a stockroom with guns
 * in it.
 *
 * <p>The counter is the <b>whole arrangement</b>. It runs the width of the
 * compartment and divides it into a public apron anybody may stand in and a
 * secure floor only the armourer works behind — ready-use lockers close to the
 * counter, a rifle-rack run against the deepest bulkhead, a bench where a
 * weapon is actually stripped down and mended, and the ammunition kept on its
 * own run rather than stacked against the arms. A person coming in reaches the
 * counter and stops there; the only way behind it is the one length the run
 * deliberately leaves open, which is where the armourer actually crosses.
 *
 * <p>{@linkplain RoomFitting#handed() Handed}, because the arrangement has a
 * front and a back that a mirror image would swap: the door side is the
 * public side, full stop, and an armoury whose counter backed onto the hatch
 * instead would be a different room rather than the same one seen from
 * behind. {@link #hookups} authors doors on the apron's own bulkhead alone —
 * a hatch cut into the workshop instead would let a passer-by walk straight in
 * among the racks — and {@link #stubDoors} joins a fallback door that lands
 * somewhere else regardless straight back through the floor, the same
 * insurance every other authored hookup carries.
 *
 * <p>Only a couple of counter positions carry {@link Affordance#ISSUE}. The
 * counter is a job for whoever is behind it, not for every length of it at
 * once — six issue points in a room this size would be six armourers, and the
 * room has one. What fills the rest of the run is {@link Affordance#STOW}:
 * racks and lockers restocked from the bench, which is exactly the
 * between-two-points work the affordance is for. The bench itself is
 * {@link Affordance#REPAIR} — a weapon waiting to be put right is the one job
 * here that is not the counter's.
 *
 * <p>New art is authored for the pieces the registry has no way to say: the
 * counter itself, the rifle-rack run, the ready-use locker bank, and the
 * ammunition run. Every one of them is asked for by a stable id and none of
 * them is guaranteed to exist yet — the atlas that bakes them in is a separate
 * pass — so each is {@linkplain #resolve resolved} against
 * {@link TileRegistry} first and falls back to ship's furniture already in
 * service, the way {@link GymFitting} filters its own kit. A locker bank that
 * has not been baked yet still stands as a run of shelving; the room is
 * furnished either way, and never bare deck under a hopeful id.
 */
public final class ArmoryFitting implements RoomFitting {

    /** Cells of apron in front of the counter — where a queue actually stands. */
    private static final int PUBLIC_DEPTH = 2;
    /** Cells behind the counter reserved as the armourer's own working aisle. */
    private static final int STAFF_AISLE_DEPTH = 1;
    /** Cells of working alley in front of the back-wall rack run. */
    private static final int RACK_ALLEY_DEPTH = 1;
    /** Depth of the rack run itself, against the deepest bulkhead. */
    private static final int RACK_DEPTH = 1;
    /** Every fixed band this arrangement needs regardless of room size: apron, counter, aisle, alley, racks. */
    private static final int FIXED_DEPTH =
            PUBLIC_DEPTH + 1 + STAFF_AISLE_DEPTH + RACK_ALLEY_DEPTH + RACK_DEPTH;

    /** Cells one length of counter takes. Two lets a five-length run leave a clean gap. */
    private static final int COUNTER_PIECE = 2;
    /** Cells one length of rifle rack takes. */
    private static final int RACK_PIECE = 3;
    private static final int LOCKER_WIDTH = 3;
    private static final int AMMO_WIDTH = 2;
    private static final int BENCH_WIDTH = 3;
    /** Depth a ready-use locker bank or an ammunition run needs to stand in. */
    private static final int WORK_FIXTURE_DEPTH = 2;

    /** Shallowest room this arrangement is worth attempting, on either axis. */
    private static final int MIN_ALONG = COUNTER_PIECE * 3;
    private static final int MIN_ACROSS = FIXED_DEPTH;

    private static final String COUNTER_ID = "doodad.armoury-issue-counter";
    private static final String COUNTER_FALLBACK = "doodad.shelf-1";
    private static final String RACK_ID = "doodad.armoury-rifle-rack";
    private static final String RACK_FALLBACK = "doodad.shelf-2";
    private static final String LOCKER_ID = "doodad.armoury-ready-locker";
    private static final String LOCKER_FALLBACK = "doodad.shelf-3";
    private static final String AMMO_ID = "doodad.armoury-ammo-locker";
    private static final String AMMO_FALLBACK = "doodad.industrial-crate-stack";
    private static final String BENCH_ID = "doodad.armoury-cleaning-bench";
    private static final String BENCH_FALLBACK = "doodad.box";

    @Override
    public RoomPurpose purpose() {
        return RoomPurpose.ARMORY;
    }

    /**
     * The public apron and the workshop are not the same room seen from
     * behind — the counter faces one way, and a mirror image swaps which side
     * of it a hand is issued from.
     */
    @Override
    public boolean handed() {
        return true;
    }

    /**
     * Doors on the apron's own bulkhead only, so anybody who comes in lands in
     * the queue and never behind the counter.
     *
     * <p>The same forward/aft alternative {@link BerthingFitting} offers on its
     * one worked bulkhead: two doorways where the deck can serve both, one
     * where it can only reach the room once.
     */
    @Override
    public List<Hookup> hookups(RoomShape canonical) {
        int along = canonical.width();
        int forward = Math.max(0, along / 4);
        int aft = Math.min(along - 2, along - 1 - along / 4);
        if (aft - forward < 2) {
            return List.of(Hookup.of(doorway(along / 2)));
        }
        return List.of(
                Hookup.of(doorway(forward), doorway(aft)),
                Hookup.of(doorway(along / 2)));
    }

    /** One doorway on the apron's bulkhead, as a pair of cells either of which serves. */
    private static Hookup.DoorSlot doorway(int along) {
        return Hookup.DoorSlot.run(along, -1, 2, 1);
    }

    @Override
    public void fit(RoomFloor floor) {
        int along = floor.canonicalWidth();
        int across = floor.canonicalHeight();
        if (along < MIN_ALONG || across < MIN_ACROSS) return;

        int counterRow = PUBLIC_DEPTH;
        int staffAisleRow = counterRow + 1;
        int workshopFrom = staffAisleRow + 1;
        int workshopDepth = across - FIXED_DEPTH;
        int rackAlleyRow = across - 2;
        int rackRow = across - 1;

        // Circulation first: the apron everybody may stand on, the aisle only
        // the armourer works from, and the alley the racks are serviced from.
        reserve(floor, 0, 0, along, PUBLIC_DEPTH);
        reserve(floor, 0, staffAisleRow, along, 1);
        reserve(floor, 0, rackAlleyRow, along, 1);
        stubDoors(floor, along, across);

        List<Integer> counterCols = layCounter(floor, along, counterRow);
        layIssuePoints(floor, counterCols, counterRow, staffAisleRow);
        layWorkshop(floor, along, workshopFrom, workshopDepth);
        layRacks(floor, along, rackRow);
    }

    /**
     * The counter itself, in lengths across the whole width, with one length
     * left open as the gap the armourer actually crosses.
     *
     * <p>The gap sits short of the far bulkhead rather than at it, so the
     * counter still reaches into the corner and nobody can walk round its end
     * instead of through the one gap it left.
     *
     * @return the canonical column of every length that was actually placed
     */
    private List<Integer> layCounter(RoomFloor floor, int along, int counterRow) {
        String id = resolve(floor, COUNTER_ID, COUNTER_FALLBACK, 1);
        int width = piece(id, COUNTER_ID, COUNTER_PIECE);
        int pieces = along / width;
        int passSlot = Math.max(0, pieces - 2);
        int leftover = along - pieces * width;
        reserve(floor, pieces * width, counterRow, leftover, 1);

        List<Integer> placedAt = new ArrayList<>();
        for (int slot = 0; slot < pieces; slot++) {
            int col = slot * width;
            if (slot == passSlot) {
                reserve(floor, col, counterRow, width, 1);
                continue;
            }
            if (place(floor, col, counterRow, id)) placedAt.add(col);
        }
        return placedAt;
    }

    /**
     * The counter's own work: a couple of positions, not one per length.
     * Stood on the secure side, since drawing and handing back weapons is the
     * armourer's job rather than the queue's.
     */
    private void layIssuePoints(RoomFloor floor, List<Integer> counterCols,
                                int counterRow, int staffAisleRow) {
        if (counterCols.isEmpty()) return;
        int first = counterCols.get(0);
        task(floor, first, staffAisleRow, Affordance.ISSUE, first, counterRow);
        int last = counterCols.get(counterCols.size() - 1);
        if (last != first) {
            task(floor, last, staffAisleRow, Affordance.ISSUE, last, counterRow);
        }
    }

    /**
     * Ready-use lockers close to the counter, ammunition on its own run at the
     * other end so it is never stacked against the arms, and the bench between
     * them where a weapon actually gets put right.
     *
     * <p>Falls back to the bench alone in a room too shallow or narrow to seat
     * both banks — still somewhere a weapon is mended, which is the one job
     * this arrangement cannot lose and still call itself a workshop.
     */
    private void layWorkshop(RoomFloor floor, int along, int workshopFrom, int workshopDepth) {
        if (workshopDepth < 1) return;
        String bench = resolve(floor, BENCH_ID, BENCH_FALLBACK, workshopDepth);
        if (workshopDepth >= WORK_FIXTURE_DEPTH
                && along >= LOCKER_WIDTH + AMMO_WIDTH + BENCH_WIDTH) {
            place(floor, 0, workshopFrom,
                    resolve(floor, LOCKER_ID, LOCKER_FALLBACK, workshopDepth), Affordance.STOW);
            int ammoCol = along - AMMO_WIDTH;
            place(floor, ammoCol, workshopFrom,
                    resolve(floor, AMMO_ID, AMMO_FALLBACK, workshopDepth), Affordance.STOW);
            int middleWidth = ammoCol - LOCKER_WIDTH;
            int benchCol = LOCKER_WIDTH + Math.max(0, (middleWidth - BENCH_WIDTH) / 2);
            place(floor, benchCol, workshopFrom, bench, Affordance.REPAIR);
        } else {
            int benchCol = Math.max(0, (along - BENCH_WIDTH) / 2);
            place(floor, benchCol, workshopFrom, bench, Affordance.REPAIR);
        }
        // Whatever the banks and the bench left over is the aisle between them.
        reserve(floor, 0, workshopFrom, along, workshopDepth);
    }

    /**
     * The rifle racks against the deepest bulkhead, worked from the alley in
     * front of them. Only every other length carries a job — a rack is
     * furniture whether or not it is being restocked this watch.
     */
    private void layRacks(RoomFloor floor, int along, int rackRow) {
        String id = resolve(floor, RACK_ID, RACK_FALLBACK, 1);
        int width = piece(id, RACK_ID, RACK_PIECE);
        int pieces = along / width;
        int leftover = along - pieces * width;
        reserve(floor, pieces * width, rackRow, leftover, 1);
        for (int slot = 0; slot < pieces; slot++) {
            int col = slot * width;
            if (slot % 2 == 1) {
                place(floor, col, rackRow, id, Affordance.STOW);
            } else {
                place(floor, col, rackRow, id);
            }
        }
    }

    /**
     * A door landing somewhere the authored hookups could not reach the deck
     * is still joined straight back through the compartment — the same
     * insurance every fitting with an authored hookup carries. A door that
     * landed on the apron as asked needs nothing further: the apron is already
     * reserved whole.
     */
    private void stubDoors(RoomFloor floor, int along, int across) {
        for (Doorway door : floor.localDoors()) {
            int[] canonical = floor.toCanonical(door.x(), door.y());
            if (canonical[1] < PUBLIC_DEPTH) continue;
            int column = Math.max(0, Math.min(along - 1, canonical[0]));
            reserve(floor, column, 0, 1, across);
        }
    }

    /**
     * The registered id if the atlas has caught up with this room <em>and</em>
     * the piece can stand in a band this deep, or ship's furniture if not.
     *
     * <p>Having the id is only half the question. Every piece here is drawn
     * lying along the deck's x axis, so in a quarter-turned compartment it
     * stands that length <em>deep</em> instead — a three-cell rack reaching
     * three cells out of a one-cell rack row, refused without a word. The
     * armoury then generated its bands, its counter gap and its doors, placed
     * two of its nine fixtures, and looked from the outside like a room that
     * had simply been authored sparsely.
     */
    private static String resolve(RoomFloor floor, String preferred, String fallback,
                                  int depth) {
        if (TileRegistry.installed().doodad(preferred) == null) return fallback;
        return span(floor, preferred)[1] <= depth ? preferred : fallback;
    }

    /** Cells of bulkhead one length covers: the authored run, or one cell for a stand-in. */
    private static int piece(String id, String preferred, int authored) {
        return id.equals(preferred) ? authored : 1;
    }

    /** How much of the canonical frame one piece of art covers, world footprint swapped by turn. */
    private static int[] span(RoomFloor floor, String id) {
        DoodadDef def = TileRegistry.installed().doodad(id);
        int x = def == null ? 1 : def.footprintCellsX;
        int y = def == null ? 1 : def.footprintCellsY;
        return floor.pose().upright() ? new int[]{ x, y } : new int[]{ y, x };
    }

    private boolean place(RoomFloor floor, int along, int across, String id) {
        int[] sp = span(floor, id);
        int[] rect = floor.toLocalRect(along, across, sp[0], sp[1]);
        return floor.place(id, rect[0], rect[1]);
    }

    private boolean place(RoomFloor floor, int along, int across, String id, Affordance affordance) {
        int[] sp = span(floor, id);
        int[] rect = floor.toLocalRect(along, across, sp[0], sp[1]);
        return floor.place(id, rect[0], rect[1], affordance);
    }

    private void task(RoomFloor floor, int cellAlong, int cellAcross, Affordance affordance,
                      int fixtureAlong, int fixtureAcross) {
        int[] stand = floor.toLocal(cellAlong, cellAcross);
        int[] fixture = floor.toLocal(fixtureAlong, fixtureAcross);
        floor.fixtureTask(stand[0], stand[1], affordance, fixture[0], fixture[1]);
    }

    private void reserve(RoomFloor floor, int along, int across, int alongSpan, int acrossSpan) {
        if (alongSpan <= 0 || acrossSpan <= 0) return;
        int[] rect = floor.toLocalRect(along, across, alongSpan, acrossSpan);
        floor.reserveLane(rect[0], rect[1], rect[2], rect[3]);
    }
}
