package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * What the side's hulls — every vehicle, and the hover scout's sensor drones — see, as the contacts a squad would hold.
 *
 * <p>A hull is an observer that belongs to no squad, out to {@link VehicleType#observeCells()}: its sensor mast where
 * it has one, its crew's eyes otherwise. Each tick it records every hostile combatant it has ordinary line of sight
 * to within that range. The commander reads those records through
 * {@code CommanderInfluenceService.SightingWatch} as direct contacts, as it reads a fort gun's target. No
 * squad's beliefs are written: the hull informs command, and a squad learns of what it saw by being ordered toward
 * it. It is not a combat buff — nothing it sees changes a hit roll.
 *
 * <p>What the player sees through a hull or a sensor drone is not here. Both are fog contributors like a soldier or
 * a mech ({@code FogOfWarService}), cached and refreshed by cohort.
 *
 * <p>Runs after the spatial index is rebuilt, so its sightings are read by the next commander capture, well inside
 * its cadence.
 */
public final class HullSightings {

    private final LongBucket scratch = new LongBucket();
    private final Map<Faction, long[]> sightings = new EnumMap<>(Faction.class);
    private final Map<Faction, Integer> counts = new EnumMap<>(Faction.class);
    /** The extra observers (sensor drones) of the last tick, kept so a vehicle target can be asked of them too. */
    private Faction[] extraSide = new Faction[8];
    private float[] extraX = new float[8];
    private float[] extraY = new float[8];
    private float[] extraRange = new float[8];
    private int extraCount;

    /** Whether an observer at ({@code sx},{@code sy}) with {@code range} cells of sight sees ({@code tx},{@code ty}). */
    public static boolean sees(NavigationGrid grid, float sx, float sy, float tx, float ty, float range) {
        float dx = tx - sx;
        float dy = ty - sy;
        if (dx * dx + dy * dy > range * range) return false;
        return grid.hasLineOfSight((int) Math.floor(sx), (int) Math.floor(sy),
                (int) Math.floor(tx), (int) Math.floor(ty));
    }

    /** An observer that is not a hull: a side's sensor drone at a point, with its range in cells. */
    @FunctionalInterface
    public interface Observer {
        void observe(Faction side, float x, float y, float rangeCells);
    }

    /** Hands every extra observer (a hull's sensor drones) to the sink, once per tick. */
    @FunctionalInterface
    public interface Observers {
        void forEach(Observer sink);
    }

    public void tick(ConvoyService convoy, UnitRosterService roster, UnitSpatialIndex index, NavigationGrid grid) {
        tick(convoy, roster, index, grid, sink -> { });
    }

    /** As {@link #tick(ConvoyService, UnitRosterService, UnitSpatialIndex, NavigationGrid)}, adding {@code extra} observers on the same terms as a hull. */
    public void tick(ConvoyService convoy, UnitRosterService roster, UnitSpatialIndex index,
                     NavigationGrid grid, Observers extra) {
        counts.clear();
        extraCount = 0;
        for (int i = 0, n = convoy.vehicleCount(); i < n; i++) {
            long id = convoy.vehicleAt(i);
            VehicleType type = convoy.vehicleType(id);
            if (type == null || !convoy.isPresent(id)) continue;
            GroundBody body = convoy.body(id);
            observe(convoy.faction(id), body.x, body.y, type.observeCells(), roster, index, grid);
        }
        extra.forEach((side, x, y, range) -> {
            if (extraCount == extraSide.length) {
                int cap = extraCount * 2;
                extraSide = Arrays.copyOf(extraSide, cap);
                extraX = Arrays.copyOf(extraX, cap);
                extraY = Arrays.copyOf(extraY, cap);
                extraRange = Arrays.copyOf(extraRange, cap);
            }
            extraSide[extraCount] = side;
            extraX[extraCount] = x;
            extraY[extraCount] = y;
            extraRange[extraCount] = range;
            extraCount++;
            observe(side, x, y, range, roster, index, grid);
        });
        // Sorted and de-duplicated, so two scouts seeing one body report it once and the order never depends on
        // the spatial index's buckets.
        for (Map.Entry<Faction, Integer> entry : counts.entrySet()) {
            long[] ids = sightings.get(entry.getKey());
            int n = entry.getValue();
            Arrays.sort(ids, 0, n);
            int unique = 0;
            for (int k = 0; k < n; k++) {
                if (unique == 0 || ids[unique - 1] != ids[k]) ids[unique++] = ids[k];
            }
            entry.setValue(unique);
        }
    }

    private void observe(Faction side, float x, float y, float range, UnitRosterService roster,
                         UnitSpatialIndex index, NavigationGrid grid) {
        if (side == null) return;
        scratch.clear();
        index.gatherHostileCombatants(x, y, range, side, scratch);
        for (int k = 0; k < scratch.size; k++) {
            long target = scratch.ids[k];
            if (!roster.isLive(target)) continue;
            if (sees(grid, x, y, roster.world().x(target), roster.world().y(target), range)) {
                record(side, target);
            }
        }
    }

    /**
     * Whether one of {@code side}'s extra observers, a sensor drone, sees ({@code tx},{@code ty}) now. The sightings
     * above carry roster bodies only; a vehicle is asked of the observers by place.
     */
    public boolean extraSees(NavigationGrid grid, Faction side, float tx, float ty) {
        for (int i = 0; i < extraCount; i++) {
            if (extraSide[i] == side && sees(grid, extraX[i], extraY[i], tx, ty, extraRange[i])) return true;
        }
        return false;
    }

    /** Every hostile body {@code side}'s hulls saw on the last tick, once each, in id order. */
    public void forEachSighting(Faction side, LongConsumer sink) {
        Integer n = counts.get(side);
        if (n == null) return;
        long[] ids = sightings.get(side);
        for (int k = 0; k < n; k++) sink.accept(ids[k]);
    }

    /** Whether {@code side}'s hulls saw {@code target} on the last tick. */
    public boolean sighted(Faction side, long target) {
        Integer n = counts.get(side);
        return n != null && Arrays.binarySearch(sightings.get(side), 0, n, target) >= 0;
    }

    private void record(Faction side, long target) {
        int n = counts.getOrDefault(side, 0);
        long[] ids = sightings.get(side);
        if (ids == null || n == ids.length) {
            ids = ids == null ? new long[16] : Arrays.copyOf(ids, ids.length * 2);
            sightings.put(side, ids);
        }
        ids[n] = target;
        counts.put(side, n + 1);
    }
}
