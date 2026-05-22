package com.dillon.starsectormarines.battle;

import com.dillon.starsectormarines.battle.nav.GridPathfinder;
import com.dillon.starsectormarines.battle.objective.Objective;

import java.util.Random;

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
    /**
     * Per-unit RNG owned by the thread processing this unit during UPDATE_UNITS.
     * Replaces sim-shared {@code BattleSimulation.rng} for parallel-decide-phase
     * call sites (weapon hit rolls, shot endpoint scatter, flee wander,
     * patrol jitter, drone swarm) so the fork-join dispatch has no Random
     * contention. Sim-global RNG keeps serving serial-phase callers
     * (death-pose pick in {@code applyDamageNow}, map gen, setup). Seeded
     * with system time by default — we don't require bit-reproducible
     * battles.
     */
    public final Random rng = new Random();

    // Logical cell (pathfinder sees these).
    public int cellX;
    public int cellY;

    // Smooth render position in cell units. Equals (cellX, cellY) at rest.
    public float renderX;
    public float renderY;

    /**
     * Current path as a flat {@code int[]} of interleaved {@code x,y} pairs —
     * cell {@code i} sits at {@code (path[i*2], path[i*2+1])}. Empty
     * ({@link GridPathfinder#EMPTY_PATH}) when the unit has nothing scheduled.
     * Flattened from the old {@code List<int[]>} to drop the per-cell
     * {@code int[2]} allocations on each pathfind.
     */
    public int[] path = GridPathfinder.EMPTY_PATH;
    /** Index of the next cell along {@link #path} to step into — addresses cells, not raw int positions (i.e. path slots {@code [pathIdx*2, pathIdx*2+1]}). */
    public int pathIdx = 0;
    public float moveProgress = 0f; // 0..1 toward path[pathIdx]

    /** Convenience accessor — number of cells in {@link #path}. */
    public int pathCellCount() { return path.length >> 1; }
    /** Convenience accessor — x coordinate of the i-th cell along {@link #path}. */
    public int pathCellX(int i) { return path[i << 1]; }
    /** Convenience accessor — y coordinate of the i-th cell along {@link #path}. */
    public int pathCellY(int i) { return path[(i << 1) | 1]; }
    /** True when the unit has no path scheduled. Match for the old {@code path.isEmpty()} check. */
    public boolean pathEmpty() { return path.length == 0; }

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

    /**
     * Close-wall radius for "air" line-of-sight, in cells. When &gt; 0, walls
     * within this many cells of this unit's position are treated as transparent
     * for LoS checks involving this unit — both as shooter and as target.
     * Models flying mounts that hover above building footprints: a drone can
     * fire OUT of the building it's directly above, and ground combatants can
     * fire UP at the drone through the same close walls. Both directions use
     * the same radius so the rule is symmetric. 0 (default) means standard
     * grid LoS; only {@link Drone} sets this today, but {@link
     * com.dillon.starsectormarines.battle.air.Shuttle}-mounted turrets pass
     * their own equivalent radius through {@code TurretAim.State}.
     */
    public float airLosRadius = 0f;

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

    /**
     * Sim-seconds until this unit is next eligible to micro-reposition between
     * shots. Story G — replaces the prior per-shot 30% RNG roll with a real
     * cooldown so a setup machine gunner in heavy cover doesn't twitch every
     * burst, and the squad's individual marines visibly shift at different
     * times (cooldowns decorrelate as they reset on different shots).
     * Ticked down each tick by {@link com.dillon.starsectormarines.battle.ai.InfantryUnitPrep#tickCooldowns}.
     */
    public float repositionCooldown = 0f;

    /** {@link UnitRole#FLEE} idle pause between wander legs. While >0 the civilian stands at their current cell instead of picking a new destination. Rolled fresh on arrival; ignored when a threat is in range. */
    public float wanderDwellTimer = 0f;

    /**
     * Sim-tick index of the last {@code rollReprioritizeOnHit} attempt
     * against this unit. Compared to {@link com.dillon.starsectormarines.battle.BattleSimulation#simTickIndex}
     * to gate the reprio to one roll per tick — without the gate, a 4-marine
     * squad opening up in the same tick gives the mech a ~82% per-tick
     * reprio chance from the base 0.35 rate (1 − 0.65⁴), which produces
     * near-constant target twitching. Only mechs + turrets pay attention
     * to this field; infantry leaves it at the -1 sentinel. {@code -1}
     * before the first reprio attempt and stays at -1 for units that
     * never qualify (infantry, civilians, dead units).
     */
    public volatile int lastReprioTickIndex = -1;

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

    /**
     * Queue the burst follow-up rounds after the AI has already fired round 1.
     * No-op for single-shot weapons or units without a {@link #primaryWeapon}
     * profile (militia / aliens / turrets — those use their own burst paths or
     * are intrinsically single-shot). Centralizes the trigger pattern so every
     * fireShot callsite — stanced, moving, opportunity, garrison — gets bursts
     * consistently. Without this, the moving-stance callsites tap once while
     * stanced callsites rip a full burst, which reads as a bug after
     * PULSE_RIFLE became a 3-round BR.
     */
    public void beginBurst(Unit target) {
        if (primaryWeapon == null || primaryWeapon.burstCount <= 1) return;
        burstRemaining = primaryWeapon.burstCount - 1;
        burstTimer = primaryWeapon.burstSpacing;
        burstTarget = target;
    }

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
