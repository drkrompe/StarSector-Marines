package com.dillon.starsectormarines.battle.vehicle;

import com.dillon.starsectormarines.battle.nav.NavigationGrid;
import com.dillon.starsectormarines.battle.sim.ConvoyService;
import com.dillon.starsectormarines.battle.unit.Faction;
import com.dillon.starsectormarines.battle.unit.LongBucket;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import com.dillon.starsectormarines.battle.unit.UnitSpatialIndex;
import com.dillon.starsectormarines.battle.vision.FogOfWarService;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.LongConsumer;

/**
 * What the side's sight-carrying hulls — the scout car — see, published to the two pictures a side has.
 *
 * <p>A hull with {@link VehicleType#sightCells()} is an observer that belongs to no squad. Each tick it publishes
 * itself to its side's fog as a temporary sight source (when that side's sight is shown to the player), and records
 * every hostile combatant it has ordinary line of sight to within that range. The commander reads those records
 * through {@code CommanderInfluenceService.SightingWatch} as direct contacts, as it reads a fort gun's target. No
 * squad's beliefs are written: the scout informs command and the player, and a squad learns of what it saw by being
 * ordered toward it. It is not a combat buff — nothing it sees changes a hit roll.
 *
 * <p>Runs after the spatial index is rebuilt, so its fog sources are read by the next vision pass and its
 * sightings by the next commander capture; both are well inside their own cadences.
 */
public final class ScoutSight {

    private final LongBucket scratch = new LongBucket();
    private final Map<Faction, long[]> sightings = new EnumMap<>(Faction.class);
    private final Map<Faction, Integer> counts = new EnumMap<>(Faction.class);

    /** Whether an observer at ({@code sx},{@code sy}) with {@code range} cells of sight sees ({@code tx},{@code ty}). */
    public static boolean sees(NavigationGrid grid, float sx, float sy, float tx, float ty, float range) {
        float dx = tx - sx;
        float dy = ty - sy;
        if (dx * dx + dy * dy > range * range) return false;
        return grid.hasLineOfSight((int) Math.floor(sx), (int) Math.floor(sy),
                (int) Math.floor(tx), (int) Math.floor(ty));
    }

    public void tick(ConvoyService convoy, UnitRosterService roster, UnitSpatialIndex index,
                     NavigationGrid grid, FogOfWarService fog) {
        fog.clearVehicleSightSources();
        counts.clear();
        for (int i = 0, n = convoy.vehicleCount(); i < n; i++) {
            long id = convoy.vehicleAt(i);
            VehicleType type = convoy.vehicleType(id);
            if (type == null || type.sightCells() <= 0f || !convoy.isPresent(id)) continue;
            GroundBody body = convoy.body(id);
            Faction side = convoy.faction(id);
            float range = type.sightCells();
            if (fog.isInitialized() && fog.getVisionState().isContributor(side)) {
                fog.addVehicleSightSource((int) Math.floor(body.x), (int) Math.floor(body.y), Math.round(range));
            }
            scratch.clear();
            index.gatherHostileCombatants(body.x, body.y, range, side, scratch);
            for (int k = 0; k < scratch.size; k++) {
                long target = scratch.ids[k];
                if (!roster.isLive(target)) continue;
                if (sees(grid, body.x, body.y, roster.world().x(target), roster.world().y(target), range)) {
                    record(side, target);
                }
            }
        }
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

    /** Every hostile body {@code side}'s scouts saw on the last tick, once each, in id order. */
    public void forEachSighting(Faction side, LongConsumer sink) {
        Integer n = counts.get(side);
        if (n == null) return;
        long[] ids = sightings.get(side);
        for (int k = 0; k < n; k++) sink.accept(ids[k]);
    }

    /** Whether {@code side}'s scouts saw {@code target} on the last tick. */
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
