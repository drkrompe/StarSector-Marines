package com.dillon.starsectormarines.battle;

import java.util.Collections;
import java.util.List;

/**
 * One combatant in the battle simulation. Plain data — all behavior lives on
 * {@link BattleSimulation}. Fields are public for hot-path access from the
 * tick loop; the package keeps the surface narrow.
 *
 * <p>Position is split: {@link #cellX}/{@link #cellY} are the logical cell
 * (what pathfinding sees), while {@link #renderX}/{@link #renderY} are the
 * smooth-interpolated position inside the cell grid (in cell units, fractional)
 * that the renderer reads. The two coincide when the unit is at rest or has
 * just landed in a new cell.
 *
 * <p>{@link #path} + {@link #pathIdx} + {@link #moveProgress} describe the
 * current movement step. The simulation rebuilds {@code path} when the unit is
 * between cells ({@code moveProgress == 0}) and lerps {@code renderX/Y} toward
 * {@code path[pathIdx]} as {@code moveProgress} climbs from 0 to 1; on arrival
 * the logical cell advances and progress resets.
 */
public class Unit {

    public final String id;
    public final Faction faction;

    // Logical cell (pathfinder sees these).
    public int cellX;
    public int cellY;

    // Smooth render position in cell units. Equals (cellX, cellY) at rest.
    public float renderX;
    public float renderY;

    // Movement state — owned by BattleSimulation; exposed for the renderer.
    public List<int[]> path = Collections.emptyList();
    public int pathIdx = 0;
    public float moveProgress = 0f; // 0..1 toward path[pathIdx]

    // Placeholder stats — same numbers per side until we drive these from
    // captain traits + mission difficulty in a later slice.
    public float moveSpeed     = 2.0f;  // cells/second
    public float maxHp         = 10f;
    public float hp            = 10f;
    public float attackDamage  = 2f;
    public float attackRange   = 4.0f;  // cells; gives ~40 firing-position candidates around a target for the squad to ring up
    public float attackCooldown = 1.0f; // seconds between shots
    public float cooldownTimer  = 0f;
    public float accuracy       = 0.6f; // probability a fired shot deals damage; misses still emit a visual tracer

    public Unit target;

    public Unit(String id, Faction faction, int cellX, int cellY) {
        this.id = id;
        this.faction = faction;
        this.cellX = cellX;
        this.cellY = cellY;
        this.renderX = cellX;
        this.renderY = cellY;
    }

    public boolean isAlive() {
        return hp > 0f;
    }
}
