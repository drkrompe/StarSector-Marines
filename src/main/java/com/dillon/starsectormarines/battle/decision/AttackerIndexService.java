package com.dillon.starsectormarines.battle.decision;
import com.dillon.starsectormarines.battle.sim.World;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;

import java.util.ArrayList;

/**
 * Per-target attacker index — for each unit currently targeted by at least
 * one alive attacker, holds the list of attacker ids aiming at it. Drives the
 * O(1)-lookup crowding term in {@code TacticalScoring#scoreCrowding} so the
 * scorer can walk the (typically &lt; 6 entry) attacker list per candidate
 * enemy instead of scanning every unit on the map.
 *
 * <p>{@link #rebuild()} is called once at tick top in the serial phase,
 * before UPDATE_UNITS. Reads via {@link #getAttackersOf(long)} happen in
 * parallel during UPDATE_UNITS against the frozen snapshot — same
 * single-pass-per-tick contract as the spatial unit index. Mid-tick target
 * shifts aren't reflected until the next tick's rebuild, which matches the
 * pre-extraction inline behavior.
 *
 * <p>Keyed by {@code long} entity id ({@link Long2ObjectOpenHashMap}). Buckets
 * are primitive-{@code long} id lists recycled through {@link #pool} so
 * steady-state allocation is zero — they grow once and live forever.
 *
 * <p>Sibling slice to {@link com.dillon.starsectormarines.battle.unit.UnitRosterService},
 * {@link com.dillon.starsectormarines.battle.combat.DamageService}, et al.
 */
public final class AttackerIndexService {

    private final UnitRosterService rosterService;

    private final Long2ObjectMap<LongArrayList> attackersByTarget = new Long2ObjectOpenHashMap<>();
    private final ArrayList<LongArrayList> pool = new ArrayList<>();

    public AttackerIndexService(UnitRosterService rosterService) {
        this.rosterService = rosterService;
    }

    /**
     * Returns the ids of the alive attackers currently aiming at {@code target},
     * or {@code null} if no one is targeting it. The list is mutated in-place
     * each tick by {@link #rebuild()} — callers must not retain it across
     * tick boundaries.
     */
    public LongArrayList getAttackersOf(long target) {
        return attackersByTarget.get(target);
    }

    /**
     * Rebuilds the index from the current {@code world.targetId(id)} ids. Recycles
     * bucket lists via {@link #pool} so the steady-state allocation is zero.
     * Skips dead attackers and dead / released targets so a unit holding a
     * stale id at its dying enemy doesn't pollute the next tick's lookup —
     * {@link UnitRosterService#isLive(long)} folds the "no target (0L)" and
     * "target was released" cases into one gate.
     */
    public void rebuild() {
        for (LongArrayList bucket : attackersByTarget.values()) {
            bucket.clear();
            pool.add(bucket);
        }
        attackersByTarget.clear();
        World world = rosterService.world();
        for (int i = 0, n = rosterService.liveCount(); i < n; i++) {
            long u = rosterService.get(i);
            if (!rosterService.identity().type(u).combatant) continue; // non-combatants carry no COMBAT.targetId
            long targetId = world.targetId(u);
            if (!rosterService.isLive(targetId)) continue;
            LongArrayList bucket = attackersByTarget.get(targetId);
            if (bucket == null) {
                bucket = pool.isEmpty()
                        ? new LongArrayList(4)
                        : pool.remove(pool.size() - 1);
                attackersByTarget.put(targetId, bucket);
            }
            bucket.add(u);
        }
    }
}
