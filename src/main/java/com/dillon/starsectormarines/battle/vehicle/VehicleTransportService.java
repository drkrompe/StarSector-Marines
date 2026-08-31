package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.component.BattleComponents;
import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.nav.NavigationService;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.squad.Squad;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.engine.ecs.ComponentType;
import com.dillon.starsectormarines.engine.ecs.EntityWorld;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Who is riding in what, and the two operations that change it.
 *
 * <p>Carrying people is not new here, but carrying a <em>particular</em> person
 * is. Delivery counts passengers — a convoy holds a number and mints a fresh
 * marine per unload, an evacuating civilian is deleted and a counter goes up —
 * which is right for arrivals and departures, where the individual is either
 * not yet real or gone for good. It is exactly wrong for a ride across the
 * battlefield, where the entire point is that the squad that gets out is the
 * squad that got in, with its casualties, its loadout and its squad still
 * attached.
 *
 * <p>So a passenger is the unit itself, narrowed: mounting takes away
 * {@code POSITION} and {@code MOVEMENT} — being somewhere, and being on the way
 * somewhere — and keeps everything else. That is the trick a convoy chassis
 * already runs one level up, where carrying no {@code POSITION} is what makes
 * occupancy and separation skip it.
 *
 * <p><b>It stops there deliberately.</b> Removing a component drops its row:
 * {@code EntityWorld.transition} moves the entity to a new archetype and the
 * old values do not come back, so stripping {@code COMBAT} and re-adding it on
 * dismount would hand back a marine with no weapon range and no damage — and
 * would look completely fine to a test that checked the squad got out intact.
 * {@code POSITION} is safe because the dismount writes it, and
 * {@code MOVEMENT} because a path is meant to be rebuilt. Anything carrying
 * authored numbers stays where it is, and {@code RIDING} is what the passes
 * that must skip a passenger actually read.
 *
 * <p>{@code RIDING} is the only record of it. A carrier's manifest is derived
 * by asking who is riding in it, so there is no second list to drift.
 */
public final class VehicleTransportService {

    /** How far from the hull a unit may be standing and still climb aboard. */
    public static final int MOUNT_RADIUS_CELLS = 3;
    /** How far from the hull the service will look for somewhere to put a passenger down. */
    private static final int DISMOUNT_SCAN_RADIUS = 8;

    private final UnitRosterService roster;
    private final ConvoyService convoy;
    private final EntityWorld entityWorld;
    private final BattleComponents components;
    private final NavigationService navigation;

    public VehicleTransportService(UnitRosterService roster, ConvoyService convoy,
                                   EntityWorld entityWorld, BattleComponents components,
                                   NavigationService navigation) {
        this.roster = roster;
        this.convoy = convoy;
        this.entityWorld = entityWorld;
        this.components = components;
        this.navigation = navigation;
    }

    /** Whether {@code unit} is currently riding in something. */
    public boolean isRiding(long unit) {
        return roster.isRiding(unit);
    }

    /** The vehicle carrying {@code unit}, or {@code 0L} when it is on its own feet. */
    public long carrierOf(long unit) {
        if (!isRiding(unit)) return 0L;
        return entityWorld.getLong(unit, components.RIDING, BattleComponents.RIDING_CARRIER_ID);
    }

    /** Everyone currently aboard {@code vehicle}, in dense-roster order. */
    public List<Long> manifest(long vehicle) {
        List<Long> aboard = new ArrayList<>();
        long[] dense = roster.denseArray();
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long unit = dense[i];
            if (carrierOf(unit) == vehicle) aboard.add(unit);
        }
        return aboard;
    }

    /** Seats left on {@code vehicle}. */
    public int seatsFree(long vehicle) {
        return convoy.vehicleType(vehicle).capacity - manifest(vehicle).size();
    }

    /**
     * Puts every member of {@code squad} standing near {@code vehicle} aboard it.
     *
     * <p>All or nothing on the squad: a half-loaded squad is two groups to
     * command with one identity, which is worse than a squad that stayed put.
     *
     * @return how many mounted, or zero when the squad does not fit or nobody
     *         is close enough
     */
    public int mountSquad(long vehicle, int squadId) {
        if (!convoy.isVehicle(vehicle)) return 0;
        List<Long> boarding = squadMembersNear(vehicle, squadId);
        if (boarding.isEmpty() || boarding.size() > seatsFree(vehicle)) return 0;

        for (long unit : boarding) mount(vehicle, unit);
        // The ride invalidates where the squad was told to be. Keeping the
        // objective would have it dismount and walk straight back the way it
        // was just carried; the claim is untouched because nothing about who
        // owns this squad changed by putting it in a truck.
        // The ride invalidates where the squad was told to be. Keeping the
        // objective would have it dismount and walk straight back the way it
        // was just carried. The command claim is untouched: nothing about who
        // owns this squad changed by putting it in a truck, so there is no
        // hand-off to make — a claim is handed off when it changes hands, and
        // this one does not.
        Squad squad = roster.getSquad(squadId);
        if (squad != null) squad.assignedObjective = null;
        return boarding.size();
    }

    /**
     * Sets everyone aboard {@code vehicle} down on open ground around it.
     *
     * @return how many got out; a passenger with nowhere to stand stays aboard
     */
    public int dismountAll(long vehicle) {
        return dismountUpTo(vehicle, Integer.MAX_VALUE);
    }

    /**
     * Sets at most {@code limit} of the passengers down, in manifest order.
     * Bounded because a hull that brews up lets only some of them out.
     *
     * @return how many got out
     */
    public int dismountUpTo(long vehicle, int limit) {
        if (!convoy.isVehicle(vehicle)) return 0;
        GroundBody body = convoy.body(vehicle);
        Set<Long> taken = new HashSet<>();
        int out = 0;
        for (long unit : manifest(vehicle)) {
            if (out >= limit) break;
            int[] cell = openCellNear((int) Math.floor(body.x), (int) Math.floor(body.y), taken);
            if (cell == null) break;
            taken.add(key(cell[0], cell[1]));
            dismount(unit, cell[0] + 0.5f, cell[1] + 0.5f);
            out++;
        }
        return out;
    }

    /**
     * Takes everyone aboard {@code vehicle} off it and hands them back, for a
     * hull that is about to be a wreck. Armour is a gamble on the vehicle
     * rather than immunity, so the caller kills them — this service owns who is
     * aboard, not what killing means.
     */
    public List<Long> disembarkForWreck(long vehicle) {
        List<Long> aboard = manifest(vehicle);
        GroundBody body = convoy.body(vehicle);
        for (long unit : aboard) {
            entityWorld.transmute(unit,
                    new ComponentType[]{components.POSITION, components.MOVEMENT},
                    new ComponentType[]{components.RIDING});
            roster.world().setPos(unit, body.x, body.y);
            settleFreshMovement(unit);
        }
        return aboard;
    }

    private void mount(long vehicle, long unit) {
        entityWorld.addComponent(unit, components.RIDING);
        entityWorld.setLong(unit, components.RIDING, BattleComponents.RIDING_CARRIER_ID, vehicle);
        entityWorld.transmute(unit, null,
                new ComponentType[]{components.POSITION, components.MOVEMENT});
    }

    private void dismount(long unit, float x, float y) {
        entityWorld.transmute(unit,
                new ComponentType[]{components.POSITION, components.MOVEMENT},
                new ComponentType[]{components.RIDING});
        roster.world().setPos(unit, x, y);
        settleFreshMovement(unit);
    }

    /**
     * A re-added component arrives zeroed, and a null path is not the same
     * thing as no path — the movers read its length. Give a dismounted unit
     * the empty path a unit that never moved would have.
     */
    private void settleFreshMovement(long unit) {
        roster.movement().setPathRef(unit, GridPathfinder.EMPTY_PATH);
        roster.movement().setPathIdx(unit, 0);
    }

    private List<Long> squadMembersNear(long vehicle, int squadId) {
        GroundBody body = convoy.body(vehicle);
        List<Long> near = new ArrayList<>();
        long[] dense = roster.denseArray();
        float r2 = MOUNT_RADIUS_CELLS * MOUNT_RADIUS_CELLS;
        for (int i = 0, n = roster.liveCount(); i < n; i++) {
            long unit = dense[i];
            if (isRiding(unit)) continue;
            if (!roster.squad().hasSquad(unit) || roster.squad().squadId(unit) != squadId) continue;
            float dx = roster.world().x(unit) - body.x;
            float dy = roster.world().y(unit) - body.y;
            if (dx * dx + dy * dy <= r2) near.add(unit);
        }
        return near;
    }

    /** Nearest walkable, unoccupied, not-already-claimed cell to set somebody down on. */
    private int[] openCellNear(int cx, int cy, Set<Long> taken) {
        NavigationGrid grid = navigation.getGrid();
        Set<Long> seen = new HashSet<>();
        Queue<int[]> q = new ArrayDeque<>();
        q.add(new int[]{cx, cy, 0});
        seen.add(key(cx, cy));
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[2] > DISMOUNT_SCAN_RADIUS) continue;
            if (grid.inBounds(p[0], p[1]) && grid.isWalkable(p[0], p[1])
                    && !taken.contains(key(p[0], p[1]))
                    && !navigation.isCellOccupied(p[0], p[1])) {
                return new int[]{p[0], p[1]};
            }
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = p[0] + d[0];
                int ny = p[1] + d[1];
                if (!grid.inBounds(nx, ny) || !seen.add(key(nx, ny))) continue;
                q.add(new int[]{nx, ny, p[2] + 1});
            }
        }
        return null;
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }
}
