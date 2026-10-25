package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;

import java.util.function.LongPredicate;

/**
 * Whether a side has eyes on a body right now, from somebody other than the shooter: the question an indirect-fire
 * hull asks before it fires at what it cannot see itself ({@code convoy-nouns.md}, the missile carriers).
 *
 * <p>Eyes are a hull (any vehicle, the scouts farthest) or a sensor drone with the body in its own sight range and line
 * of sight; a body the side's scouts recorded on the last tick ({@link HullSightings}); any friendly soldier or mech within
 * {@link #OBSERVER_REACH_CELLS} with line of sight, as a mortar's spotter is; and a comms operator with it in sight.
 * A soldier who holds still (dragged off alive by the swarm, or not yet up from being dropped) calls
 * nothing: a marine hauled to a bore's mouth must not bring the carriers' rounds down on it.
 * Nothing here is a belief or a memory: a body that ducked behind a wall a second ago is not spotted, so a carrier
 * holds fire on it rather than shelling where it used to be.
 */
public final class SideObservers implements SideSpotting {

    /** How far a soldier or mech calls a target for a launcher, cells; the field mortar's spotter reach. */
    public static final float OBSERVER_REACH_CELLS = 34f;

    /** Whether a side's comms operators have a point in sight. */
    @FunctionalInterface
    public interface CommsSight {
        boolean sees(Faction side, float x, float y);
        CommsSight NONE = (side, x, y) -> false;
    }

    private final ConvoyService convoy;
    private final UnitRosterService roster;
    private final UnitSpatialIndex index;
    private final NavigationGrid grid;
    private final HullSightings scouts;
    private final CommsSight comms;
    private final LongPredicate holdsStill;
    private final LongBucket scratch = new LongBucket();

    public SideObservers(ConvoyService convoy, UnitRosterService roster, UnitSpatialIndex index,
                         NavigationGrid grid, HullSightings scouts, CommsSight comms, LongPredicate holdsStill) {
        this.convoy = convoy;
        this.roster = roster;
        this.index = index;
        this.grid = grid;
        this.scouts = scouts;
        this.comms = comms;
        this.holdsStill = holdsStill != null ? holdsStill : id -> false;
    }

    @Override
    public boolean spotted(Faction side, long target) {
        if (side == null || target == 0L) return false;
        if (scouts != null && scouts.sighted(side, target)) return true;
        float tx = roster.world().x(target);
        float ty = roster.world().y(target);
        if (!Float.isFinite(tx) || !Float.isFinite(ty)) return false;
        // A hull sees vehicles too, which its scout record (dense-roster bodies only) does not carry.
        for (int i = 0, n = convoy.vehicleCount(); i < n; i++) {
            long hull = convoy.vehicleAt(i);
            VehicleType type = convoy.vehicleType(hull);
            if (type == null || !convoy.isPresent(hull) || convoy.faction(hull) != side) continue;
            GroundBody body = convoy.body(hull);
            if (HullSightings.sees(grid, body.x, body.y, tx, ty, type.observeCells())) return true;
        }
        if (scouts != null && scouts.extraSees(grid, side, tx, ty)) return true;
        Faction targetSide = convoy.isVehicle(target) ? convoy.faction(target) : roster.identity().faction(target);
        if (targetSide != null && index != null) {
            synchronized (scratch) {
                index.gatherHostileCombatants(tx, ty, OBSERVER_REACH_CELLS, targetSide, scratch);
                int cx = (int) Math.floor(tx);
                int cy = (int) Math.floor(ty);
                for (int k = 0; k < scratch.size; k++) {
                    long eye = scratch.ids[k];
                    if (!roster.isLive(eye) || roster.identity().faction(eye) != side || holdsStill.test(eye)) continue;
                    if (grid.hasLineOfSight(roster.world().cellX(eye), roster.world().cellY(eye), cx, cy)) return true;
                }
            }
        }
        return comms.sees(side, tx, ty);
    }
}
