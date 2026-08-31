package com.dillon.starsectormarines.battle.air;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.world.gen.Affordance;
import com.dillon.starsectormarines.battle.world.gen.FixtureTask;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What there is to do on an airfield's apron, as work points beside its berths.
 *
 * <p><b>Authored by the host rather than by generation</b>, which is the
 * opposite of how a fitted room publishes its work and is deliberate. Everywhere
 * else a room's work is cut with the room: a fitting places a bench and says
 * what the bench affords, and the berth a job is bound to is a berth that
 * fitting laid. An airfield is not a fitted room. Generation reserves a lot,
 * paves it and marks out hardstands; which of them are berths, in what order,
 * and what stands on each is {@link AirfieldService}'s decision at battle setup.
 *
 * <p>That matters for one specific reason. A servicing job names <em>which
 * berth</em> it works, as an index, and an index is only meaningful against the
 * list it indexes. Published from generation, those indices would have to agree
 * with a berth list registered later in another file, in an order neither of
 * them states — an agreement nothing would notice going wrong, because the
 * symptom is technicians servicing the wrong aircraft. Published here, the job
 * and the berth it names are made in the same loop, and there is no order for
 * them to disagree about.
 *
 * <p>A pass over the field yields two kinds of stop. Servicing is at the
 * aircraft, on both flanks, and is bound to the berth so it is work only while
 * something is standing there. A readout is at the stand's own board, ahead of
 * the nose — the airframe's state read off rather than felt for — and is bound
 * to the cell, because a board is worth reading whether or not the aircraft it
 * belongs to is home.
 */
public final class AirfieldWork {

    /** Cells out from the middle of a stand to where somebody works on the hull. */
    private static final int FLANK = 2;
    /** Cells ahead of the stand to the board its state is read off. */
    private static final int BOARD = 3;

    private AirfieldWork() { }

    /**
     * The work an airfield's berths offer, indexed against
     * {@link AirfieldService#berths()} in that list's own order.
     *
     * <p>A stop is taken only where somebody could actually stand: a stand
     * against the fence or backed onto a shed has fewer places to work than one
     * in the open, and offering a point inside a wall would publish a job whose
     * claimant walks into it and never arrives.
     */
    public static List<FixtureTask> onTheApron(AirfieldService field, NavigationGrid grid) {
        List<FixtureTask> work = new ArrayList<>();
        if (field == null) return work;
        Set<Long> taken = standings(field);

        List<AirfieldService.Berth> berths = field.berths();
        for (int index = 0; index < berths.size(); index++) {
            AirfieldService.Berth berth = berths.get(index);
            for (int side : new int[]{ -FLANK, FLANK }) {
                int x = berth.centerX + side;
                if (!standable(grid, taken, x, berth.centerY)) continue;
                work.add(FixtureTask.servingBerth(x, berth.centerY, index,
                        berth.centerX, berth.centerY));
            }
            int boardY = berth.centerY - BOARD;
            if (standable(grid, taken, berth.centerX, boardY)) {
                work.add(FixtureTask.at(berth.centerX, boardY, Affordance.READOUT,
                        berth.centerX, berth.centerY));
            }
        }
        return List.copyOf(work);
    }

    /**
     * Which berths have an aircraft on them, as the job board reads occupancy.
     *
     * <p>Read off the berth's state rather than off whether a unit is standing
     * there yet. The board is published during setup and the field puts its
     * aircraft out on its first tick, so asking for the unit would find every
     * stand empty, offer no servicing anywhere, and leave the field unmanned for
     * the rest of the battle — a whole crew lost to one tick of ordering.
     */
    public static boolean[] occupied(AirfieldService field) {
        List<AirfieldService.Berth> berths = field.berths();
        boolean[] held = new boolean[berths.size()];
        for (int index = 0; index < held.length; index++) {
            AirfieldService.BerthState state = berths.get(index).state;
            held[index] = state != AirfieldService.BerthState.DESTROYED
                    && state != AirfieldService.BerthState.AWAY;
        }
        return held;
    }

    /** The middle of every stand, which is aircraft rather than somewhere to stand. */
    private static Set<Long> standings(AirfieldService field) {
        Set<Long> centres = new HashSet<>();
        for (AirfieldService.Berth berth : field.berths()) {
            centres.add(key(berth.centerX, berth.centerY));
        }
        return centres;
    }

    private static boolean standable(NavigationGrid grid, Set<Long> taken, int x, int y) {
        return grid.inBounds(x, y) && grid.isWalkable(x, y) && !taken.contains(key(x, y));
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
