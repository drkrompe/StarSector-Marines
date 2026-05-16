package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.objective.Objective;

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

    /** Sentinel value for {@link #squadId} when the unit isn't part of a squad — defenders, solo combatants, anyone not deboarded from a marine shuttle. */
    public static final int NO_SQUAD = -1;

    public final String id;
    public final Faction faction;
    /** Squad identity. Set to a positive int when this unit deboarded as part of a fireteam; {@link #NO_SQUAD} for solo units. */
    public int squadId = NO_SQUAD;

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
    public float maxHp         = 25f;
    public float hp            = 25f;
    public float attackDamage  = 2f;
    public float attackRange   = 24.0f; // cells; long rifle range — quarter of the map width, makes cross-map sight lanes matter
    public float attackCooldown = 1.0f; // seconds between shots
    public float cooldownTimer  = 0f;
    public float accuracy       = 0.35f; // probability a fired shot deals damage; misses still emit a visual tracer. Tuned with HP25/dmg2 for ~2-3 min engagements.

    public Unit target;

    /** Role drives behavior dispatch in the sim. Default {@link UnitRole#COMBATANT} matches pre-role behavior. */
    public UnitRole role = UnitRole.COMBATANT;
    /** Objective this unit is acting on, when the role requires one (charge site for a planter, exfil zone for a VIP, position to camp for an objective camper). Null for plain combatants. */
    public Objective assignedObjective;
    /** {@link UnitRole#KIT_RETRIEVER} target — the dropped kit this unit is heading to recover. Cleared when picked up or when the drop is consumed by someone else. */
    public EquipmentDrop equipmentDropTarget;

    /** Sim-seconds remaining in fall-back state. >0 means the unit is breaking contact toward {@link #fallbackCellX}/{@link #fallbackCellY}. */
    public float fallbackTimer = 0f;
    public int fallbackCellX = -1;
    public int fallbackCellY = -1;

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
