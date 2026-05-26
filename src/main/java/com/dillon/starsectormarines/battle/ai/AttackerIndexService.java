package com.dillon.starsectormarines.battle.ai;

import com.dillon.starsectormarines.battle.unit.Unit;
import com.dillon.starsectormarines.battle.unit.UnitRosterService;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-target attacker index — for each unit currently targeted by at least
 * one alive attacker, holds the list of attackers aiming at it. Drives the
 * O(1)-lookup crowding term in {@link TacticalScoring#crowdScore} so the
 * scorer can walk the (typically &lt; 6 entry) attacker list per candidate
 * enemy instead of scanning every unit on the map.
 *
 * <p>{@link #rebuild()} is called once at tick top in the serial phase,
 * before UPDATE_UNITS. Reads via {@link #getAttackersOf(Unit)} happen in
 * parallel during UPDATE_UNITS against the frozen snapshot — same
 * single-pass-per-tick contract as the spatial unit index. Mid-tick target
 * shifts aren't reflected until the next tick's rebuild, which matches the
 * pre-extraction inline behavior.
 *
 * <p>{@link Unit} doesn't override {@code equals/hashCode}, so
 * {@link Object2ObjectOpenHashMap} gives identity-key semantics for free.
 * Buckets are recycled through {@link #pool} so steady-state allocation is
 * zero — they grow once and live forever.
 *
 * <p>Sibling slice to {@link com.dillon.starsectormarines.battle.unit.UnitRosterService},
 * {@link com.dillon.starsectormarines.battle.damage.DamageService}, et al.
 */
public final class AttackerIndexService {

    private final UnitRosterService rosterService;

    private final Object2ObjectMap<Unit, ArrayList<Unit>> attackersByTarget = new Object2ObjectOpenHashMap<>();
    private final ArrayList<ArrayList<Unit>> pool = new ArrayList<>();

    public AttackerIndexService(UnitRosterService rosterService) {
        this.rosterService = rosterService;
    }

    /**
     * Returns the alive attackers currently aiming at {@code target}, or
     * {@code null} if no one is targeting it. The list is mutated in-place
     * each tick by {@link #rebuild()} — callers must not retain it across
     * tick boundaries.
     */
    public ArrayList<Unit> getAttackersOf(Unit target) {
        return attackersByTarget.get(target);
    }

    /**
     * Rebuilds the index from the current {@link Unit#targetId} ids. Recycles
     * bucket lists via {@link #pool} so the steady-state allocation is zero.
     * Skips dead attackers and dead / released targets so a unit holding a
     * stale id at its dying enemy doesn't pollute the next tick's lookup.
     * The registry's {@code getOrNull} folds the "target was released" case
     * into a single null check — no separate {@code targetId == 0L} branch.
     */
    public void rebuild() {
        for (ArrayList<Unit> bucket : attackersByTarget.values()) {
            bucket.clear();
            pool.add(bucket);
        }
        attackersByTarget.clear();
        List<Unit> units = rosterService.getUnits();
        com.dillon.starsectormarines.battle.unit.UnitRegistry registry = rosterService.getRegistry();
        for (Unit u : units) {
            if (!u.isAlive()) continue;
            Unit target = registry.getOrNull(u.targetId);
            if (target == null) continue;
            ArrayList<Unit> bucket = attackersByTarget.get(target);
            if (bucket == null) {
                bucket = pool.isEmpty()
                        ? new ArrayList<>(4)
                        : pool.remove(pool.size() - 1);
                attackersByTarget.put(target, bucket);
            }
            bucket.add(u);
        }
    }
}
