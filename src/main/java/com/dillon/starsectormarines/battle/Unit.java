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
    /** Archetype — drives sprite + base stat block. Set once at construction. */
    public final UnitType type;
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

    // Stats — initialized from UnitType, then mutable per-unit so captain traits
    // and mission modifiers can adjust an individual without changing the archetype.
    public float moveSpeed;
    public float maxHp;
    public float hp;
    public float attackDamage;
    public float attackRange;
    public float attackCooldown;
    public float cooldownTimer = 0f;
    public float accuracy;

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

    /** {@link UnitRole#FLEE} idle pause between wander legs. While >0 the civilian stands at their current cell instead of picking a new destination. Rolled fresh on arrival; ignored when a threat is in range. */
    public float wanderDwellTimer = 0f;

    /**
     * Cell this unit returns to when nothing else is happening — the
     * "post" they were assigned at spawn. Used by {@link UnitRole#GARRISON}
     * for idle behavior: members path to their home and idle there while
     * their squad is UNAWARE. -1 sentinel = no home assigned (units that
     * roam, e.g. patrols and marines, don't set this).
     */
    public int homeCellX = -1;
    public int homeCellY = -1;

    /** Primary handheld weapon. Null for legacy / non-marine units — fire stats fall back to the {@link UnitType} defaults baked into {@link #attackRange}/{@link #attackDamage}/etc. Assigned at deboard time for marines. */
    public MarineWeapon primaryWeapon;
    /** Optional secondary slot (rocket launcher, future grenades). Null = no secondary. */
    public MarineSecondary secondaryWeapon;
    /** Rounds remaining on the {@link #secondaryWeapon}. Decremented on each secondary shot; once zero the marine reverts to primary fire. */
    public int secondaryAmmo;
    /** Independent cooldown for the secondary weapon so it doesn't share state with the primary's {@link #cooldownTimer}. */
    public float secondaryCooldownTimer = 0f;
    /** Sim-seconds remaining in the secondary's aim-then-fire animation. While &gt;0 the marine is locked in place and the renderer draws the {@link MarineSecondary#aimSpritePath} pose; the actual shot launches when this drops below {@link MarineSecondary#aimDuration}/2. */
    public float secondaryActionTimer = 0f;
    /** Latched on launch within the current aim cycle so we only emit one shot per cycle, even though the trigger condition holds for several ticks past launch. */
    public boolean secondaryFiredThisAction = false;
    /** Target locked at the start of the aim cycle. The rocket fires here even if the original target dies mid-aim — the launcher's already committed. */
    public Unit secondaryAimTarget;

    /** Burst rounds queued after the AI's initial primary shot — the sim emits one per {@link MarineWeapon#burstSpacing} interval until exhausted. 0 = single-shot mode. */
    public int burstRemaining = 0;
    /** Sim-seconds until the next queued burst round fires. Decremented in {@code InfantryWeapons.tick}. */
    public float burstTimer = 0f;
    /** Target captured when the burst was queued. Burst rounds keep firing here even if {@link #target} drifts to someone else, so a burst doesn't smear across multiple enemies. Cleared along with {@link #burstRemaining} when the burst ends or the target dies. */
    public Unit burstTarget;

    /** Random prone-pose index rolled on death. Drives which corpse frame the renderer picks from {@link UnitType#deadSpritePath} so a battlefield has pose variety rather than every body in the same slump. -1 sentinel = unit still alive. */
    public int deathPoseIdx = -1;

    /**
     * Mech chassis loadout. Non-null only on mech-class units ({@link UnitType#HEAVY_MECH}
     * today). When set, the unit fires three concurrent weapon tracks via the
     * mech-fire pass in {@code BattleSimulation} instead of the marine
     * primary/secondary path; the unit's base {@link #attackDamage} /
     * {@link #attackCooldown} are unused and {@link #attackRange} only matters
     * for target acquisition (set wide on {@link UnitType#HEAVY_MECH} to match
     * the LRM's reach).
     */
    public MechLoadoutState mech;

    public Unit(String id, Faction faction, UnitType type, int cellX, int cellY) {
        this.id = id;
        this.faction = faction;
        this.type = type;
        this.cellX = cellX;
        this.cellY = cellY;
        this.renderX = cellX;
        this.renderY = cellY;
        this.moveSpeed = type.moveSpeed;
        this.maxHp = type.maxHp;
        this.hp = type.maxHp;
        this.attackDamage = type.attackDamage;
        this.attackRange = type.attackRange;
        this.attackCooldown = type.attackCooldown;
        this.accuracy = type.accuracy;
    }

    public boolean isAlive() {
        return hp > 0f;
    }
}
